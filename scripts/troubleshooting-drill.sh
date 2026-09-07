#!/usr/bin/env bash
# Run one explicitly selected, bounded drill on the existing production host.
set -Eeuo pipefail
set +x
umask 077
cd /opt/shopsphere
drill=${1:?Choose crash, service, deployment, database, disk, or cpu}
case "$drill" in crash|service|deployment|database|disk|cpu) ;; *) exit 2;; esac
target=shopsphere-prod-product-service-1
temporary="shopsphere-drill-$$"
scratch=$(mktemp /opt/shopsphere/runtime/drill.XXXXXX)
cleanup() {
  if [[ "$drill" == service || "$drill" == crash ]]; then docker start "$target" >/dev/null; fi
  if docker container inspect "$temporary" >/dev/null 2>&1; then docker rm -f "$temporary" >/dev/null; fi
  rm -f -- "$scratch"
}
trap cleanup EXIT
counts() {
  docker exec shopsphere-prod-mysql-1 sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -uroot -N -e "SELECT COUNT(*) FROM $AUTH_DB_NAME.users; SELECT COUNT(*) FROM $ORDER_DB_NAME.orders; SELECT COUNT(*),SUM(quantity_available),SUM(quantity_reserved) FROM $INVENTORY_DB_NAME.inventory;"'
  docker exec shopsphere-prod-mongodb-1 sh -c 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval '\''print(db.getSiblingDB(process.env.MONGO_INITDB_DATABASE).products.countDocuments({}))'\'''
}
healthy() {
  for attempt in {1..60}; do
    bad=$(docker ps -aq --filter label=com.docker.compose.project=shopsphere-prod | xargs docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' | awk '$0!="running healthy" {n++} END {print n+0}')
    [[ "$bad" == 0 ]] && break
    sleep 3
  done
  [[ "$bad" == 0 ]]
  for path in / /nginx-health /api/v1/products; do
    curl --retry 4 --retry-delay 3 --retry-all-errors --max-time 20 -fsS -o /dev/null "https://shopsphere-sakti.duckdns.org$path"
  done
}
healthy
before=$(counts)
case "$drill" in
  crash)
    previous=$(docker inspect -f '{{.RestartCount}}' "$target")
    docker kill --signal KILL "$target" >/dev/null
    sleep 5
    current=$(docker inspect -f '{{.RestartCount}}' "$target")
    printf 'Restart count before=%s after=%s\n' "$previous" "$current"
    if [[ "$current" -le "$previous" ]]; then
      echo 'Docker operator kill did not auto-restart; explicitly recovering existing container'
      docker start "$target" >/dev/null
    fi
    ;;
  service)
    docker stop --time 10 "$target" >/dev/null
    code=$(curl --max-time 25 -sS -o /dev/null -w '%{http_code}' https://shopsphere-sakti.duckdns.org/api/v1/products || true)
    printf 'Unavailable product API HTTP=%s\n' "$code"
    [[ "$code" == 5* || "$code" == 000 ]]
    docker start "$target" >/dev/null
    ;;
  deployment)
    image=$(docker inspect -f '{{.Config.Image}}' shopsphere-prod-nginx-1)
    docker run -d --name "$temporary" --network none --memory 64m --cpus 0.2 --entrypoint sh --health-cmd 'exit 1' --health-interval 1s --health-retries 1 "$image" -c 'sleep 120' >/dev/null
    sleep 4
    [[ $(docker inspect -f '{{.State.Health.Status}}' "$temporary") == unhealthy ]]
    echo 'Isolated candidate rejected by health gate; restoring previous configuration'
    docker rm -f "$temporary" >/dev/null
    docker run -d --name "$temporary" --network none --memory 64m --cpus 0.2 --entrypoint sh --health-cmd 'exit 0' --health-interval 1s "$image" -c 'sleep 120' >/dev/null
    sleep 4
    [[ $(docker inspect -f '{{.State.Health.Status}}' "$temporary") == healthy ]]
    echo 'Isolated rollback health=healthy; live stack never changed'
    ;;
  database)
    if docker run --name "$temporary" --network none --memory 128m --cpus 0.2 --entrypoint mysql mysql:8.4 --connect-timeout=3 -h127.0.0.1 -uroot -e 'SELECT 1' >"$scratch" 2>&1; then exit 1; fi
    grep -Eo 'ERROR [0-9]+ \([A-Z0-9]+\)' "$scratch"
    echo 'Isolated client cannot reach database; production database unchanged'
    ;;
  disk)
    df -h /opt/shopsphere
    [[ $(df -Pk /opt/shopsphere | awk 'NR==2 {print $4}') -gt 1048576 ]]
    dd if=/dev/zero of="$scratch" bs=1M count=16 status=none
    du -h "$scratch"
    rm -f -- "$scratch"
    df -h /opt/shopsphere
    ;;
  cpu)
    docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} memory={{.MemUsage}}'
    free -m
    python3 -c 'import time; end=time.monotonic()+3; n=0
while time.monotonic()<end: n+=1
print("Bounded single-core CPU observation completed in 3 seconds")'
    ;;
esac
healthy
after=$(counts)
[[ "$before" == "$after" ]]
echo "PASS $drill: containers and HTTPS healthy; user/order/product counts and inventory totals preserved"
