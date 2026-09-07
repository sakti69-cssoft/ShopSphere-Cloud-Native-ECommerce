#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repo_root=$(cd -- "$script_dir/.." && pwd -P)

env_file=${ENV_FILE:-.env.production}
compose_file=${COMPOSE_FILE:-compose.prod.yaml}
backup_root=${BACKUP_ROOT:-runtime/backups}
mysql_image=${MYSQL_RESTORE_IMAGE:-mysql:8.4}
mongo_image=${MONGO_RESTORE_IMAGE:-mongo:8.0}
test_mysql_container=
test_mongo_container=
test_work_dir=

usage() {
  cat <<'EOF'
Usage:
  scripts/restore-shopsphere.sh test BACKUP_DIR
  ALLOW_PRODUCTION_RESTORE=yes scripts/restore-shopsphere.sh \
    production BACKUP_DIR --confirm-production-restore

The default/safe workflow restores into disposable network-isolated containers.
Production mode is intentionally double-gated, creates a pre-restore backup, and
never deletes Compose volumes.
EOF
}

absolute_from_repo() {
  local path=$1
  if [[ $path == /* ]]; then
    printf '%s\n' "$path"
  else
    printf '%s/%s\n' "$repo_root" "$path"
  fi
}

if [[ ${1:-} == -h || ${1:-} == --help ]]; then
  usage
  exit 0
fi

mode=${1:-}
backup_dir=${2:-}
[[ -n $mode && -n $backup_dir ]] || { usage >&2; exit 2; }
shift 2

production_confirmed=false
while (($#)); do
  case $1 in
    --confirm-production-restore)
      production_confirmed=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

[[ $mode == test || $mode == production ]] || { usage >&2; exit 2; }
[[ -n $backup_dir ]] || { usage >&2; exit 2; }

env_file=$(absolute_from_repo "$env_file")
compose_file=$(absolute_from_repo "$compose_file")
backup_root=$(absolute_from_repo "$backup_root")
backup_dir=$(absolute_from_repo "$backup_dir")

[[ -f $env_file ]] || { echo "Environment file not found: $env_file" >&2; exit 1; }
[[ -f $compose_file ]] || { echo "Compose file not found: $compose_file" >&2; exit 1; }
[[ -d $backup_dir ]] || { echo "Backup directory not found: $backup_dir" >&2; exit 1; }
for required_command in docker gzip sha256sum awk sed od tr diff sort cut wc flock; do
  command -v "$required_command" >/dev/null || {
    echo "$required_command is required" >&2
    exit 1
  }
done

for file in mysql.sql.gz mongodb.archive.gz mysql-counts.tsv mongodb-counts.tsv SHA256SUMS METADATA; do
  [[ -f $backup_dir/$file && ! -L $backup_dir/$file ]] || {
    echo "Required regular backup file is missing: $file" >&2
    exit 1
  }
done

verify_checksum() {
  local filename=$1 expected actual
  expected=$(awk -v file="$filename" '$2 == file { print $1 }' "$backup_dir/SHA256SUMS")
  [[ $expected =~ ^[0-9a-fA-F]{64}$ ]] || {
    echo "Missing or invalid checksum for $filename" >&2
    exit 1
  }
  actual=$(sha256sum "$backup_dir/$filename")
  actual=${actual%% *}
  [[ $actual == "$expected" ]] || {
    echo "Checksum verification failed for $filename" >&2
    exit 1
  }
}

verify_checksum mysql.sql.gz
verify_checksum mongodb.archive.gz
verify_checksum mysql-counts.tsv
verify_checksum mongodb-counts.tsv
verify_checksum METADATA
gzip -t "$backup_dir/mysql.sql.gz"
gzip -t "$backup_dir/mongodb.archive.gz"
format_version=$(awk -F= '$1 == "format_version" { print $2 }' "$backup_dir/METADATA")
schema_profile=$(awk -F= '$1 == "schema_profile" { print $2 }' "$backup_dir/METADATA")
[[ $format_version =~ ^[12]$ && $schema_profile == shopsphere-production-v1 ]] || {
  echo "Unsupported backup metadata format or schema profile" >&2
  exit 1
}
echo "Backup integrity checks passed."
if [[ $format_version == 2 ]]; then
  for file in mysql-records.tsv mongodb-records.tsv; do
    [[ -f $backup_dir/$file && ! -L $backup_dir/$file ]] || exit 1
    verify_checksum "$file"
  done
fi

wait_for_mysql() {
  local container=$1
  for _ in {1..60}; do
    if docker exec "$container" sh -ec \
      'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysqladmin ping --user=root --silent' \
      >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

wait_for_mongo() {
  local container=$1
  for _ in {1..60}; do
    if docker exec "$container" sh -ec \
      'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "quit(db.adminCommand({ping: 1}).ok ? 0 : 1)"' \
      >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

restore_test() {
  local suffix password mysql_counts mysql_database_count mysql_table_count
  local mongo_counts mongo_database_count mongo_collection_count mongo_document_count
  local restored_mysql_manifest restored_mongo_manifest
  suffix="$(date -u +%Y%m%dT%H%M%SZ)-$$"
  test_mysql_container="shopsphere-restore-test-mysql-$suffix"
  test_mongo_container="shopsphere-restore-test-mongo-$suffix"
  test_work_dir=$(mktemp -d)
  chmod 0700 "$test_work_dir"
  password=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')

  printf 'MYSQL_ROOT_PASSWORD=%s\n' "$password" >"$test_work_dir/mysql.env"
  {
    printf 'MONGO_INITDB_ROOT_USERNAME=restore_test_admin\n'
    printf 'MONGO_INITDB_ROOT_PASSWORD=%s\n' "$password"
  } >"$test_work_dir/mongo.env"
  chmod 0600 "$test_work_dir"/*.env

  cleanup_test() {
    if [[ -n $test_mysql_container || -n $test_mongo_container ]]; then
      docker rm -f "$test_mysql_container" "$test_mongo_container" >/dev/null 2>&1 || true
    fi
    if [[ -n $test_work_dir && -d $test_work_dir ]]; then
      rm -rf -- "$test_work_dir"
    fi
  }
  trap cleanup_test EXIT
  trap 'exit 130' INT TERM

  echo "Starting isolated MySQL restore target..."
  docker run --detach --rm --name "$test_mysql_container" --network none \
    --tmpfs /var/lib/mysql:rw,nosuid,nodev,noexec,size=1g \
    --env-file "$test_work_dir/mysql.env" "$mysql_image" >/dev/null
  wait_for_mysql "$test_mysql_container" || {
    echo "Isolated MySQL did not become ready" >&2
    return 1
  }
  gzip -dc "$backup_dir/mysql.sql.gz" | docker exec -i "$test_mysql_container" sh -ec \
    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --user=root'

  mysql_counts=$(docker exec "$test_mysql_container" sh -ec '
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    mysql --batch --skip-column-names --user=root --execute="
      SELECT COUNT(*) FROM information_schema.schemata
      WHERE schema_name NOT IN (\"information_schema\",\"mysql\",\"performance_schema\",\"sys\");
      SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema NOT IN (\"information_schema\",\"mysql\",\"performance_schema\",\"sys\");"
  ')
  mysql_database_count=$(printf '%s\n' "$mysql_counts" | sed -n '1p')
  mysql_table_count=$(printf '%s\n' "$mysql_counts" | sed -n '2p')
  [[ $mysql_database_count =~ ^[0-9]+$ && $mysql_database_count -ge 3 ]] || {
    echo "MySQL restore test found fewer than three application databases" >&2
    return 1
  }
  [[ $mysql_table_count =~ ^[0-9]+$ && $mysql_table_count -gt 0 ]] || {
    echo "MySQL restore test found no application tables" >&2
    return 1
  }
  restored_mysql_manifest="$test_work_dir/mysql-counts.tsv"
  docker exec "$test_mysql_container" sh -ec '
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    mysql --batch --skip-column-names --user=root --execute="
      SELECT table_schema, table_name FROM information_schema.tables
      WHERE table_schema NOT IN ('\''information_schema'\'','\''mysql'\'','\''performance_schema'\'','\''sys'\'')
        AND table_type = '\''BASE TABLE'\''
      ORDER BY table_schema, table_name" | while IFS="$(printf '\''\t'\'')" read -r database table; do
        count=$(mysql --batch --skip-column-names --user=root \
          --execute="SELECT COUNT(*) FROM \`$database\`.\`$table\`")
        printf "%s\t%s\t%s\n" "$database" "$table" "$count"
      done
  ' >"$restored_mysql_manifest"
  if ! diff -u "$backup_dir/mysql-counts.tsv" "$restored_mysql_manifest"; then
    echo "MySQL restored table counts do not match the backup manifest" >&2
    return 1
  fi
  for table in users customer_addresses inventory orders order_items order_idempotency coupons coupon_usages; do
    awk -F '\t' -v expected="$table" '$2 == expected { found=1 } END { exit !found }' \
      "$restored_mysql_manifest" || {
        echo "Expected restored MySQL table is missing: $table" >&2
        return 1
      }
  done
  echo "MySQL restore verified: $mysql_database_count databases, $mysql_table_count tables."

  echo "Starting isolated MongoDB restore target..."
  docker run --detach --rm --name "$test_mongo_container" --network none \
    --tmpfs /data/db:rw,nosuid,nodev,noexec,size=1g \
    --env-file "$test_work_dir/mongo.env" "$mongo_image" >/dev/null
  wait_for_mongo "$test_mongo_container" || {
    echo "Isolated MongoDB did not become ready" >&2
    return 1
  }
  docker exec -i "$test_mongo_container" sh -ec '
    exec mongorestore \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      --archive \
      --gzip \
      --quiet
  ' <"$backup_dir/mongodb.archive.gz"

  mongo_counts=$(docker exec "$test_mongo_container" sh -ec '
    mongosh --quiet \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      --eval '\''
        const excluded = new Set(["admin", "config", "local"]);
        const names = db.adminCommand({listDatabases: 1}).databases
          .map(item => item.name).filter(name => !excluded.has(name));
        let collections = 0;
        let documents = 0;
        for (const name of names) {
          const target = db.getSiblingDB(name);
          const list = target.getCollectionNames();
          collections += list.length;
          for (const collection of list) documents += target[collection].countDocuments({});
        }
        print(names.length + " " + collections + " " + documents);
      '\''
  ')
  read -r mongo_database_count mongo_collection_count mongo_document_count <<<"$mongo_counts"
  [[ $mongo_database_count =~ ^[0-9]+$ && $mongo_database_count -gt 0 ]] || {
    echo "MongoDB restore test found no application database" >&2
    return 1
  }
  [[ $mongo_collection_count =~ ^[0-9]+$ && $mongo_collection_count -gt 0 ]] || {
    echo "MongoDB restore test found no collections" >&2
    return 1
  }
  [[ $mongo_document_count =~ ^[0-9]+$ ]] || {
    echo "MongoDB restore test returned an invalid document count" >&2
    return 1
  }
  restored_mongo_manifest="$test_work_dir/mongodb-counts.tsv"
  docker exec "$test_mongo_container" sh -ec '
    mongosh --quiet \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      --eval '\''
        const excluded = new Set(["admin", "config", "local"]);
        for (const name of db.adminCommand({listDatabases: 1}).databases
          .map(item => item.name).filter(name => !excluded.has(name)).sort()) {
          const target = db.getSiblingDB(name);
          for (const collection of target.getCollectionNames().sort()) {
            print(name + "\t" + collection + "\t" + target[collection].countDocuments({}));
          }
        }
      '\''
  ' >"$restored_mongo_manifest"
  if ! diff -u "$backup_dir/mongodb-counts.tsv" "$restored_mongo_manifest"; then
    echo "MongoDB restored collection counts do not match the backup manifest" >&2
    return 1
  fi
  for collection in products reviews wishlists; do
    awk -F '\t' -v expected="$collection" '$2 == expected { found=1 } END { exit !found }' \
      "$restored_mongo_manifest" || {
        echo "Expected restored MongoDB collection is missing: $collection" >&2
        return 1
      }
  done
  echo "MongoDB restore verified: $mongo_database_count database, $mongo_collection_count collections, $mongo_document_count documents."
  if [[ $format_version == 2 ]]; then
    bash "$script_dir/backup-record-fingerprints.sh" mysql "$test_mysql_container" "$restored_mysql_manifest" >"$test_work_dir/mysql-records.tsv"
    bash "$script_dir/backup-record-fingerprints.sh" mongodb "$test_mongo_container" "$restored_mongo_manifest" >"$test_work_dir/mongodb-records.tsv"
    for engine in mysql mongodb; do
      if ! cmp -s "$backup_dir/$engine-records.tsv" "$test_work_dir/$engine-records.tsv"; then
        echo "$engine restored record content differs from the backup fingerprint" >&2
        return 1
      fi
    done
    echo "Record-content fingerprints matched for every backed-up table and collection."
  else
    echo "Legacy v1 backup: counts verified; content fingerprints unavailable."
  fi
  echo "Isolated restore test passed; production databases and volumes were not touched."
  cleanup_test
  trap - EXIT INT TERM
}

restore_production() {
  local service container_id restore_started=false
  local live_mysql_manifest live_mongo_manifest health deadline all_healthy
  local -a app_services running_app_container_ids
  [[ ${ALLOW_PRODUCTION_RESTORE:-no} == yes && $production_confirmed == true ]] || {
    echo "Production restore refused. Read docs/BACKUP-RECOVERY.md and use both required confirmations." >&2
    exit 2
  }
  mkdir -p -- "$backup_root"
  backup_root=$(cd -- "$backup_root" && pwd -P)
  [[ $backup_root != / && $backup_root != "$repo_root" ]] || {
    echo "Refusing unsafe backup root: $backup_root" >&2
    exit 2
  }
  chmod 0700 "$backup_root"
  exec 8>"$backup_root/.restore.lock"
  flock -n 8 || { echo "Another ShopSphere restore is running" >&2; exit 1; }

  compose=(docker compose --env-file "$env_file" -f "$compose_file")
  for service in mysql mongodb; do
    "${compose[@]}" ps --status running --services | grep -Fxq "$service" || {
      echo "Required database service is not running: $service" >&2
      exit 1
    }
  done

  echo "Running the mandatory isolated restore test before production recovery..."
  restore_test

  app_services=(nginx frontend gateway auth-service product-service inventory-service cart-service order-service)
  running_app_container_ids=()
  for service in "${app_services[@]}"; do
    container_id=$("${compose[@]}" ps -q "$service")
    if [[ -n $container_id && $(docker inspect --format '{{.State.Running}}' "$container_id") == true ]]; then
      running_app_container_ids+=("$container_id")
    fi
  done

  restart_original_apps() {
    local status=$?
    [[ $restore_started == false ]] || return "$status"
    if ((${#running_app_container_ids[@]})); then
      echo "Recovery stopped before live data replacement; restarting the original application containers..." >&2
      docker start "${running_app_container_ids[@]}" >/dev/null || status=1
    fi
    return "$status"
  }
  trap restart_original_apps EXIT

  if ((${#running_app_container_ids[@]})); then
    echo "Stopping only application containers; databases and named volumes remain running..."
    docker stop --time 30 "${running_app_container_ids[@]}" >/dev/null
  fi

  echo "Creating mandatory pre-restore safety backup at a quiet application point..."
  bash "$script_dir/backup-shopsphere.sh" --backup-root "$backup_root" --no-prune

  # The safety-backup process has released this lock. Acquire it for the
  # restore so a scheduled backup cannot overlap database imports.
  exec 9>"$backup_root/.backup.lock"
  flock -n 9 || {
    echo "A backup started before the restore lock could be acquired; retry later" >&2
    exit 1
  }

  # After this point, an error deliberately leaves application traffic stopped
  # for operator investigation instead of serving a partially restored state.
  restore_started=true

  echo "Replacing only the configured MySQL application databases..."
  "${compose[@]}" exec -T mysql sh -ec '
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    exec mysql --user=root --execute="
      DROP DATABASE IF EXISTS \`$AUTH_DB_NAME\`;
      DROP DATABASE IF EXISTS \`$INVENTORY_DB_NAME\`;
      DROP DATABASE IF EXISTS \`$ORDER_DB_NAME\`;"
  '
  gzip -dc "$backup_dir/mysql.sql.gz" | "${compose[@]}" exec -T mysql sh -ec \
    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --user=root'

  echo "Replacing only the configured MongoDB product database..."
  "${compose[@]}" exec -T mongodb sh -s >/dev/null <<'MONGO_DROP'
set -eu
exec mongosh --quiet \
  --username "$MONGO_INITDB_ROOT_USERNAME" \
  --password "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB(process.env.MONGO_INITDB_DATABASE).dropDatabase()'
MONGO_DROP
  "${compose[@]}" exec -T mongodb sh -ec '
    exec mongorestore \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      --archive \
      --gzip \
      --drop \
      --quiet
  ' <"$backup_dir/mongodb.archive.gz"

  live_mysql_manifest=$(mktemp)
  live_mongo_manifest=$(mktemp)
  chmod 0600 "$live_mysql_manifest" "$live_mongo_manifest"
  "${compose[@]}" exec -T mysql sh -s <<'MYSQL_SH' | LC_ALL=C sort -t $'\t' -k1,1 -k2,2 >"$live_mysql_manifest"
set -eu
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
for database in "$AUTH_DB_NAME" "$INVENTORY_DB_NAME" "$ORDER_DB_NAME"; do
  mysql --batch --skip-column-names --user=root --execute="SELECT table_name FROM information_schema.tables WHERE table_schema = '$database' AND table_type = 'BASE TABLE' ORDER BY table_name" |
  while IFS= read -r table; do
    count=$(mysql --batch --skip-column-names --user=root --execute="SELECT COUNT(*) FROM \`$database\`.\`$table\`")
    printf "%s\t%s\t%s\n" "$database" "$table" "$count"
  done
done
MYSQL_SH
  "${compose[@]}" exec -T mongodb sh -s <<'MONGO_SH' | LC_ALL=C sort -t $'\t' -k1,1 -k2,2 >"$live_mongo_manifest"
set -eu
mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval '
const name = process.env.MONGO_INITDB_DATABASE;
const target = db.getSiblingDB(name);
for (const collection of target.getCollectionNames().sort()) {
  print(name + "\t" + collection + "\t" + target[collection].countDocuments({}));
}
'
MONGO_SH
  diff -u "$backup_dir/mysql-counts.tsv" "$live_mysql_manifest" >/dev/null || {
    echo "Restored production MySQL counts do not match the backup; applications remain stopped" >&2
    rm -f -- "$live_mysql_manifest" "$live_mongo_manifest"
    return 1
  }
  diff -u "$backup_dir/mongodb-counts.tsv" "$live_mongo_manifest" >/dev/null || {
    echo "Restored production MongoDB counts do not match the backup; applications remain stopped" >&2
    rm -f -- "$live_mysql_manifest" "$live_mongo_manifest"
    return 1
  }
  rm -f -- "$live_mysql_manifest" "$live_mongo_manifest"

  echo "Starting the same application containers without reconciling Compose or deleting volumes..."
  if ((${#running_app_container_ids[@]})); then
    docker start "${running_app_container_ids[@]}" >/dev/null
  fi
  deadline=$((SECONDS + 300))
  while ((SECONDS < deadline)); do
    all_healthy=true
    for container_id in "${running_app_container_ids[@]}"; do
      health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")
      if [[ $health != healthy && $health != running ]]; then
        all_healthy=false
        break
      fi
    done
    [[ $all_healthy == true ]] && break
    sleep 2
  done
  [[ $all_healthy == true ]] || {
    echo "Restored data counts match, but application containers did not become healthy" >&2
    return 1
  }
  trap - EXIT
  echo "Production restore completed. Run the documented application and data checks now."
}

case $mode in
  test) restore_test ;;
  production) restore_production ;;
esac
