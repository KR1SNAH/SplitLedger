#!/usr/bin/env bash
# Dumps the database to ~/backups (compressed) and keeps the last 14 dumps.
# Run from the repository root on the server, e.g. daily from cron:
#   0 3 * * * cd ~/splitledger && bash deploy/backup.sh >> ~/backups/backup.log 2>&1
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP="${KEEP:-14}"
mkdir -p "$BACKUP_DIR"

file="$BACKUP_DIR/splitledger-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
docker compose -f docker-compose.prod.yml exec -T db \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$file"
echo "$(date -u +%FT%TZ) wrote $file ($(du -h "$file" | cut -f1))"

# Delete all but the newest $KEEP dumps.
ls -1t "$BACKUP_DIR"/splitledger-*.sql.gz | tail -n +"$((KEEP + 1))" | xargs -r rm --
