# ShopSphere backup and recovery

This runbook protects the durable ShopSphere application stores on the existing
EC2 host without stopping the database containers. To make the two archives and
their exact count manifests describe one quiet application point, the backup
briefly stops only the running application/proxy containers and then restarts the
same container IDs. It does not recreate containers or delete Docker volumes.

## Scope and recovery objectives

The backup contains:

- the `AUTH_DB_NAME`, `INVENTORY_DB_NAME`, and `ORDER_DB_NAME` MySQL databases;
- the `PRODUCT_DB_NAME` MongoDB database.

Redis is a persisted cart/cache store, but it is deliberately excluded from the
database backup. Its existing `redis-data` volume is never modified by these
scripts. A database recovery can therefore leave carts newer than the restored
catalog/order state; clear or inspect affected carts through an approved
application operation if that situation occurs.

The recommended portfolio schedule is one backup every 24 hours, giving an RPO
of up to 24 hours. Allow an RTO of 60 minutes for integrity checks, isolated
restore validation, the controlled restore, and application verification.

## Storage and security model

By default, backups are written to `runtime/backups` on the EC2 EBS filesystem.
Each UTC timestamp directory contains:

```text
runtime/backups/20260906T120000Z/
  METADATA
  SHA256SUMS
  mysql.sql.gz
  mysql-counts.tsv
  mongodb.archive.gz
  mongodb-counts.tsv
```

The scripts set a `077` umask, directories to mode `0700`, and files to mode
`0600`. They never source, echo, or copy `.env.production`; database commands
consume credentials already present inside their database containers. A lock
prevents concurrent backup/restore operations. Files are first written to a
hidden incomplete directory, validated, checksummed (including metadata), and
atomically renamed. A failure trap restarts any application containers that the
backup quiesced and removes only its incomplete directory.

`runtime/` is Git-ignored. Never add backup archives, production environment
files, database credentials, or restore-test environment files to Git.

> A backup on the same EBS volume protects against logical/application failure,
> but not instance-volume loss or an AWS account incident. For disaster recovery,
> copy completed encrypted archives to a separate encrypted, access-controlled
> destination under an approved lifecycle policy. Do not add cloud storage until
> its IAM scope, encryption, retention, and cost have been reviewed.

## Create a backup

Run from the repository root on the existing host:

```bash
bash scripts/backup-shopsphere.sh
```

Expect a short application maintenance interval while logical archives and count
manifests are captured; MySQL, MongoDB, Redis, and their volumes remain mounted.
The default retention is 14 days. Override it for one run without changing
tracked source:

```bash
BACKUP_RETENTION_DAYS=30 bash scripts/backup-shopsphere.sh
bash scripts/backup-shopsphere.sh --retention-days 30
bash scripts/backup-shopsphere.sh --no-prune
```

Only complete timestamp-named directories older than the configured retention
period are pruned. The current backup and hidden incomplete work directories are
not retention targets.

For a daily systemd timer, execute the script as the deployment user from the
repository working directory. Capture only routine status output in journald;
do not enable shell tracing. Alert on a non-zero exit and periodically check EBS
free space. Installation of a timer is an operations change and is intentionally
not performed by this repository script.

## Prove that a backup can be restored

Run the isolated test before trusting a new backup and at least monthly:

```bash
bash scripts/restore-shopsphere.sh test runtime/backups/20260906T120000Z
```

Test mode:

1. verifies supported, checksummed metadata, both archives, both count manifests, and the gzip streams;
2. starts temporary MySQL 8.4 and MongoDB 8.0 containers with no network;
3. uses tmpfs storage and random, short-lived test credentials;
4. restores both archives and compares canonically sorted, exact per-table/per-collection counts with the backup manifests;
5. confirms representative business structures for users, addresses, inventory, orders/items, idempotency, coupons, products, reviews, and wishlists;
6. force-removes only those temporary test containers and their tmpfs data.

It does not connect to, stop, write to, or recreate the production database
containers or named volumes. The Docker host may pull the two versioned database
images if they are not already cached.

## Controlled production recovery

Production restore is destructive to the selected live database contents and
must run only during an approved maintenance window. Never use it merely to test
a backup. Before proceeding:

1. confirm the selected timestamp and incident scope;
2. run isolated `test` mode successfully against that backup;
3. record current container health and application counts;
4. ensure sufficient EBS free space for a mandatory pre-restore backup;
5. notify users of the maintenance window;
6. retain the current `.env.production` and Docker named volumes.

The command is intentionally double-gated:

```bash
ALLOW_PRODUCTION_RESTORE=yes bash scripts/restore-shopsphere.sh \
  production runtime/backups/20260906T120000Z \
  --confirm-production-restore
```

Production mode rechecks archive integrity, automatically proves the selected
backup in isolated containers, stops only currently running application/proxy
containers, and creates a new safety backup while application writes are quiet.
It then replaces only the three configured MySQL databases and configured MongoDB
database, imports the archives, and requires their exact counts to match before
restarting the same application container IDs. It does not run `docker compose
down`, does not use `down -v`, and does not remove the MySQL, MongoDB, or Redis
named volumes. If an import or count check fails after replacement begins, it
deliberately leaves application traffic stopped for operator investigation; the
pre-restore timestamp is the rollback source.

## Post-recovery verification

After an approved production recovery, verify all of the following before ending
the maintenance window:

```bash
docker compose --env-file .env.production -f compose.prod.yaml ps
curl --fail --silent https://shopsphere-sakti.duckdns.org/nginx-health
curl --fail --silent https://shopsphere-sakti.duckdns.org/api/v1/products >/dev/null
```

- all eleven long-running containers are healthy;
- the expected product, user, inventory, and order counts are present;
- a known order and its order items can be read;
- authentication, catalog, cart, and a synthetic checkout work;
- HTTPS remains trusted and HTTP redirects to HTTPS;
- only TCP 80 and 443 are publicly reachable;
- the recovery did not create or replace AWS infrastructure.

Record the restored timestamp, operator, reason, integrity-test result, start/end
times, and application verification outcome. Never record passwords, tokens, or
environment-file contents.
