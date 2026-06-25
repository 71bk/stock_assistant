#!/bin/sh
# Restore a custom-format pg_dump produced by backup.sh.
#
# DESTRUCTIVE: --clean drops existing objects before recreating them. Make sure
# you are pointing at the intended database.
#
# Usage (from the host):
#   docker compose -f compose.prod.yaml run --rm db-backup restore /backups/<file>.dump
#
# To restore from R2 instead, first pull the object into /backups:
#   docker compose -f compose.prod.yaml run --rm db-backup \
#     sh -c 'rclone copy "R2:$R2_BUCKET/$R2_PREFIX/<file>.dump" /backups/'
set -eu

file="${1:-}"
if [ -z "$file" ]; then
  echo "Usage: restore.sh <path-to-.dump>"
  echo "Local backups in ${BACKUP_DIR:-/backups}:"
  ls -1 "${BACKUP_DIR:-/backups}"/*.dump 2>/dev/null || echo "  (none)"
  exit 1
fi
if [ ! -f "$file" ]; then
  echo "File not found: $file"
  exit 1
fi

echo "Restoring $file into '$PGDATABASE' on ${PGHOST}:${PGPORT:-5432} ..."
# --no-owner/--no-privileges so the restore works regardless of the original role.
pg_restore --clean --if-exists --no-owner --no-privileges --dbname="$PGDATABASE" "$file"
echo "Restore complete."
