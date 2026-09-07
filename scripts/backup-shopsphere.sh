#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repo_root=$(cd -- "$script_dir/.." && pwd -P)

env_file=${ENV_FILE:-.env.production}
compose_file=${COMPOSE_FILE:-compose.prod.yaml}
backup_root=${BACKUP_ROOT:-runtime/backups}
retention_days=${BACKUP_RETENTION_DAYS:-14}
prune_enabled=true

usage() {
  cat <<'EOF'
Usage: scripts/backup-shopsphere.sh [options]

Create compressed online logical ShopSphere database backups.

Options:
  --backup-root DIR      Destination (default: runtime/backups)
  --retention-days DAYS Delete complete backups older than DAYS (default: 14)
  --no-prune             Keep all existing backups
  -h, --help             Show this help

ENV_FILE and COMPOSE_FILE may select another existing Compose environment.
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

while (($#)); do
  case $1 in
    --backup-root)
      (($# >= 2)) || { echo "--backup-root requires a directory" >&2; exit 2; }
      backup_root=$2
      shift 2
      ;;
    --retention-days)
      (($# >= 2)) || { echo "--retention-days requires a number" >&2; exit 2; }
      retention_days=$2
      shift 2
      ;;
    --no-prune)
      prune_enabled=false
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

[[ $retention_days =~ ^[0-9]+$ ]] || {
  echo "Retention days must be a non-negative integer" >&2
  exit 2
}

env_file=$(absolute_from_repo "$env_file")
compose_file=$(absolute_from_repo "$compose_file")
backup_root=$(absolute_from_repo "$backup_root")

[[ -f $env_file ]] || { echo "Environment file not found: $env_file" >&2; exit 1; }
[[ -f $compose_file ]] || { echo "Compose file not found: $compose_file" >&2; exit 1; }
for required_command in docker gzip sha256sum flock awk sort cut wc; do
  command -v "$required_command" >/dev/null || {
    echo "$required_command is required" >&2
    exit 1
  }
done

mkdir -p -- "$backup_root"
backup_root=$(cd -- "$backup_root" && pwd -P)
[[ $backup_root != / && $backup_root != "$repo_root" ]] || {
  echo "Refusing unsafe backup root: $backup_root" >&2
  exit 2
}
chmod 0700 "$backup_root"

# Serialize backup and restore operations without ever reading credentials into
# the host shell. The database containers already hold their required secrets.
exec 9>"$backup_root/.backup.lock"
flock -n 9 || { echo "Another ShopSphere backup or restore is running" >&2; exit 1; }

compose=(docker compose --env-file "$env_file" -f "$compose_file")
for service in mysql mongodb; do
  if ! "${compose[@]}" ps --status running --services | grep -Fxq "$service"; then
    echo "Required database service is not running: $service" >&2
    exit 1
  fi
done

# Briefly stop only the currently running application/proxy containers so the
# two database dumps and their count manifests describe the same quiet
# application point. Database containers and every named volume stay mounted.
app_services=(nginx frontend gateway auth-service product-service inventory-service cart-service order-service)
quiesced_container_ids=()
apps_quiesced=false

restart_quiesced_apps() {
  local deadline all_healthy health container_id
  [[ $apps_quiesced == true ]] || return 0
  ((${#quiesced_container_ids[@]})) || { apps_quiesced=false; return 0; }

  echo "Restarting the same application containers..."
  docker start "${quiesced_container_ids[@]}" >/dev/null
  deadline=$((SECONDS + 300))
  while ((SECONDS < deadline)); do
    all_healthy=true
    for container_id in "${quiesced_container_ids[@]}"; do
      health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")
      if [[ $health != healthy && $health != running ]]; then
        all_healthy=false
        break
      fi
    done
    if [[ $all_healthy == true ]]; then
      apps_quiesced=false
      echo "Application containers recovered after the backup snapshot."
      return 0
    fi
    sleep 2
  done
  echo "Application containers did not recover within 300 seconds" >&2
  return 1
}

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
incomplete_dir="$backup_root/.${timestamp}.incomplete"
final_dir="$backup_root/$timestamp"
[[ ! -e $incomplete_dir && ! -e $final_dir ]] || {
  echo "Backup destination already exists for timestamp $timestamp" >&2
  exit 1
}

cleanup() {
  local status=$?
  if [[ -d $incomplete_dir ]]; then
    rm -rf -- "$incomplete_dir"
  fi
  if ! restart_quiesced_apps; then
    status=1
  fi
  trap - EXIT
  exit "$status"
}
trap cleanup EXIT
mkdir -- "$incomplete_dir"
chmod 0700 "$incomplete_dir"

for service in "${app_services[@]}"; do
  container_id=$("${compose[@]}" ps -q "$service")
  if [[ -n $container_id && $(docker inspect --format '{{.State.Running}}' "$container_id") == true ]]; then
    quiesced_container_ids+=("$container_id")
  fi
done
if ((${#quiesced_container_ids[@]})); then
  apps_quiesced=true
  echo "Quiescing running application containers for a consistent backup point..."
  docker stop --time 30 "${quiesced_container_ids[@]}" >/dev/null
fi

echo "Creating consistent MySQL backup..."
"${compose[@]}" exec -T mysql sh -ec '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysqldump \
    --user=root \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --add-drop-database \
    --set-gtid-purged=OFF \
    --databases "$AUTH_DB_NAME" "$INVENTORY_DB_NAME" "$ORDER_DB_NAME"
' | gzip -9 >"$incomplete_dir/mysql.sql.gz"

echo "Capturing exact MySQL table counts..."
"${compose[@]}" exec -T mysql sh -s <<'CONTAINER_SH' |
  set -eu
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  for database in "$AUTH_DB_NAME" "$INVENTORY_DB_NAME" "$ORDER_DB_NAME"; do
    mysql --batch --skip-column-names --user=root --execute="
      SELECT table_name FROM information_schema.tables
      WHERE table_schema = '$database' AND table_type = 'BASE TABLE'
      ORDER BY table_name" | while IFS= read -r table; do
        count=$(mysql --batch --skip-column-names --user=root \
          --execute="SELECT COUNT(*) FROM \`$database\`.\`$table\`")
        printf "%s\t%s\t%s\n" "$database" "$table" "$count"
      done
  done
CONTAINER_SH
  LC_ALL=C sort -t $'\t' -k1,1 -k2,2 >"$incomplete_dir/mysql-counts.tsv"

echo "Creating consistent MongoDB backup..."
"${compose[@]}" exec -T mongodb sh -ec '
  exec mongodump \
    --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin \
    --db "$MONGO_INITDB_DATABASE" \
    --archive \
    --gzip \
    --quiet
' >"$incomplete_dir/mongodb.archive.gz"

echo "Capturing exact MongoDB collection counts..."
"${compose[@]}" exec -T mongodb sh -s <<'CONTAINER_SH' |
  set -eu
  mongosh --quiet \
    --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin \
    --eval '
      const name = process.env.MONGO_INITDB_DATABASE;
      const target = db.getSiblingDB(name);
      for (const collection of target.getCollectionNames().sort()) {
        print(name + "\t" + collection + "\t" + target[collection].countDocuments({}));
      }
    '
CONTAINER_SH
  LC_ALL=C sort -t $'\t' -k1,1 -k2,2 >"$incomplete_dir/mongodb-counts.tsv"

validate_count_manifest() {
  local file=$1 label=$2 expected_database_count=$3 database_count
  [[ -s $file ]] || { echo "$label count manifest is empty" >&2; return 1; }
  LC_ALL=C sort -c -t $'\t' -k1,1 -k2,2 "$file" || {
    echo "$label count manifest is not canonically sorted" >&2
    return 1
  }
  awk -F '\t' '
    NF != 3 || $1 == "" || $2 == "" || $3 !~ /^[0-9]+$/ { exit 1 }
    seen[$1 SUBSEP $2]++ > 0 { exit 1 }
  ' "$file" || { echo "$label count manifest is invalid" >&2; return 1; }
  database_count=$(cut -f1 "$file" | LC_ALL=C sort -u | wc -l)
  database_count=${database_count//[[:space:]]/}
  [[ $database_count == "$expected_database_count" ]] || {
    echo "$label count manifest identifies $database_count databases; expected $expected_database_count" >&2
    return 1
  }
}

require_object() {
  local file=$1 object=$2 label=$3
  awk -F '\t' -v expected="$object" '$2 == expected { found=1 } END { exit !found }' "$file" || {
    echo "Expected $label object is absent from the backup manifest: $object" >&2
    return 1
  }
}

validate_count_manifest "$incomplete_dir/mysql-counts.tsv" MySQL 3
validate_count_manifest "$incomplete_dir/mongodb-counts.tsv" MongoDB 1
for table in users customer_addresses inventory orders order_items order_idempotency coupons coupon_usages; do
  require_object "$incomplete_dir/mysql-counts.tsv" "$table" "MySQL table"
done
for collection in products reviews wishlists; do
  require_object "$incomplete_dir/mongodb-counts.tsv" "$collection" "MongoDB collection"
done

echo "Fingerprinting database content without exporting records to logs..."
bash "$script_dir/backup-record-fingerprints.sh" mysql "$("${compose[@]}" ps -q mysql)" "$incomplete_dir/mysql-counts.tsv" >"$incomplete_dir/mysql-records.tsv"
bash "$script_dir/backup-record-fingerprints.sh" mongodb "$("${compose[@]}" ps -q mongodb)" "$incomplete_dir/mongodb-counts.tsv" >"$incomplete_dir/mongodb-records.tsv"

# End the brief write quiescence before compression/checksum housekeeping.
restart_quiesced_apps

gzip -t "$incomplete_dir/mysql.sql.gz"
gzip -t "$incomplete_dir/mongodb.archive.gz"

cat >"$incomplete_dir/METADATA" <<EOF
format_version=2
schema_profile=shopsphere-production-v1
created_at_utc=$timestamp
mysql_format=mysqldump-sql-gzip
mongodb_format=mongodump-archive-gzip
verification=mysql-and-mongodb-exact-object-counts
application_writes_quiesced=true
EOF
chmod 0600 "$incomplete_dir"/*
(
  cd -- "$incomplete_dir"
  sha256sum METADATA mysql.sql.gz mongodb.archive.gz mysql-counts.tsv mongodb-counts.tsv mysql-records.tsv mongodb-records.tsv >SHA256SUMS
)
chmod 0600 "$incomplete_dir/SHA256SUMS"

# A rename on the same EBS filesystem makes a complete backup visible atomically.
mv -- "$incomplete_dir" "$final_dir"
trap - EXIT
echo "Backup completed: $final_dir"

if [[ $prune_enabled == true ]]; then
  while IFS= read -r -d '' candidate; do
    name=${candidate##*/}
    if [[ $name =~ ^[0-9]{8}T[0-9]{6}Z$ && $candidate != "$final_dir" ]]; then
      rm -rf -- "$candidate"
      echo "Pruned expired backup: $name"
    fi
  done < <(find "$backup_root" -mindepth 1 -maxdepth 1 -type d \
    -name '20??????T??????Z' -mtime "+$retention_days" -print0)
fi
