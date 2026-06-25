#!/bin/sh
# Daily PostgreSQL logical backup with local rotation + offsite copy to Cloudflare R2.
#
# Runs as a long-lived sidecar (see compose.prod.yaml). Each cycle takes a
# custom-format pg_dump, prunes old local dumps, then (if R2 is configured)
# uploads the dump and prunes old remote objects. R2 is optional: leave the
# R2_* vars blank to keep backups local-only.
#
# Run a one-off backup with:  docker compose -f compose.prod.yaml run --rm db-backup once
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
BACKUP_INTERVAL_SECONDS="${BACKUP_INTERVAL_SECONDS:-86400}"
R2_PREFIX="${R2_PREFIX:-postgres}"
R2_KEEP_DAYS="${R2_KEEP_DAYS:-30}"

log() { echo "[db-backup $(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"; }

r2_enabled() {
  [ -n "${RCLONE_CONFIG_R2_ACCESS_KEY_ID:-}" ] \
    && [ -n "${RCLONE_CONFIG_R2_ENDPOINT:-}" ] \
    && [ -n "${R2_BUCKET:-}" ]
}

run_backup() {
  mkdir -p "$BACKUP_DIR"
  ts="$(date -u '+%Y%m%d-%H%M%SZ')"
  file="$BACKUP_DIR/${PGDATABASE}_${ts}.dump"

  log "starting pg_dump of '$PGDATABASE' -> $(basename "$file")"
  if ! pg_dump --format=custom --compress=6 --file="$file"; then
    log "ERROR: pg_dump failed; removing partial file and keeping previous backups"
    rm -f "$file"
    return 1
  fi
  log "pg_dump done ($(du -h "$file" | cut -f1))"

  # Local rotation: drop dumps older than BACKUP_KEEP_DAYS.
  find "$BACKUP_DIR" -maxdepth 1 -name '*.dump' -type f -mtime "+${BACKUP_KEEP_DAYS}" -print -delete \
    | while read -r old; do log "pruned local $(basename "$old")"; done

  # Offsite copy to Cloudflare R2 (S3-compatible).
  if r2_enabled; then
    if rclone copyto "$file" "R2:${R2_BUCKET}/${R2_PREFIX}/$(basename "$file")"; then
      log "uploaded to R2:${R2_BUCKET}/${R2_PREFIX}/"
      if rclone delete "R2:${R2_BUCKET}/${R2_PREFIX}" --min-age "${R2_KEEP_DAYS}d"; then
        log "pruned R2 objects older than ${R2_KEEP_DAYS}d"
      fi
    else
      log "ERROR: R2 upload failed; local backup is still kept"
    fi
  else
    log "R2 not configured (set R2_* vars to enable offsite upload); local-only"
  fi
}

# One-shot mode for manual runs / testing.
if [ "${1:-}" = "once" ]; then
  run_backup
  exit $?
fi

log "backup loop started: every ${BACKUP_INTERVAL_SECONDS}s, local keep ${BACKUP_KEEP_DAYS}d, R2 keep ${R2_KEEP_DAYS}d"
while true; do
  run_backup || log "backup iteration failed; will retry next cycle"
  sleep "$BACKUP_INTERVAL_SECONDS"
done
