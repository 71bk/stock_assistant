#!/bin/sh
# Dispatch container commands:
#   (none) / loop   -> continuous daily backup loop
#   once            -> single backup then exit
#   restore <file>  -> restore a dump
#   <anything else> -> run as-is (e.g. `rclone ...`, `sh -c '...'`)
set -eu

case "${1:-loop}" in
  restore)
    shift
    exec /usr/local/bin/restore.sh "$@"
    ;;
  once | loop)
    exec /usr/local/bin/backup.sh "$@"
    ;;
  *)
    exec "$@"
    ;;
esac
