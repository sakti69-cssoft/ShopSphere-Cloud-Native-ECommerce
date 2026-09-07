#!/usr/bin/env bash
# Hash database content inside its container; never print records or credentials.
set -Eeuo pipefail
set +x
engine=${1:?engine required}
container=${2:?container required}
manifest=${3:?count manifest required}
while IFS=$'\t' read -r database object count; do
  [[ $database =~ ^[A-Za-z0-9_]+$ && $object =~ ^[A-Za-z0-9_]+$ && $count =~ ^[0-9]+$ ]] || exit 2
  case $engine in
    mysql)
      digest=$(docker exec "$container" bash -o pipefail -ec '
        export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
        mysqldump -uroot --no-create-info --skip-comments --skip-extended-insert \
          --hex-blob --order-by-primary --set-gtid-purged=OFF "$1" "$2" |
          awk "/^INSERT INTO /" | sha256sum | cut -d " " -f1
      ' fingerprint "$database" "$object")
      ;;
    mongodb)
      digest=$(docker exec -i "$container" sh -s -- "$database" "$object" <<'CONTAINER'
set -eu
export SAMPLE_DATABASE="$1" SAMPLE_COLLECTION="$2"
mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval '
const hash = require("crypto").createHash("sha256");
const collection = db.getSiblingDB(process.env.SAMPLE_DATABASE).getCollection(process.env.SAMPLE_COLLECTION);
collection.find({}).sort({_id:1}).forEach(record => hash.update(EJSON.stringify(record, {relaxed:false}) + "\n"));
print(hash.digest("hex"));
'
CONTAINER
)
      ;;
    *) exit 2 ;;
  esac
  [[ $digest =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid record fingerprint' >&2; exit 1; }
  printf '%s\t%s\t%s\n' "$database" "$object" "$digest"
done <"$manifest"
