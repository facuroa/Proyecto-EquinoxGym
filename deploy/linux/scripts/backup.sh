#!/usr/bin/env bash
set -euo pipefail

CONFIG_DIR="/etc/equinox-gym"
BACKUP_DIR="/var/backups/equinox-gym"
source "$CONFIG_DIR/backup.env"

DB_NAME=${DB_NAME:-equinoxgym}
RETENTION_DAYS=${RETENTION_DAYS:-30}
TIMESTAMP=$(date +%F_%H-%M-%S)
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}-${TIMESTAMP}.sql.gz"
CHECKSUM_FILE="$BACKUP_FILE.sha256"
TEMP_FILE="${BACKUP_FILE}.tmp"

mkdir -p "$BACKUP_DIR"
trap 'rm -f "$TEMP_FILE"' EXIT

mysqldump --defaults-extra-file="$CONFIG_DIR/backup.cnf" \
  --single-transaction --routines --events --triggers "$DB_NAME" | gzip > "$TEMP_FILE"
mv "$TEMP_FILE" "$BACKUP_FILE"
sha256sum "$BACKUP_FILE" > "$CHECKSUM_FILE"

find "$BACKUP_DIR" -type f \( -name '*.sql.gz' -o -name '*.sql.gz.sha256' \) -mtime "+$RETENTION_DAYS" -delete

if [[ -n "${RCLONE_REMOTE:-}" ]]; then
  if ! command -v rclone >/dev/null 2>&1; then
    echo "rclone no está instalado; se conserva el backup local, pero no se subió a la nube." >&2
    exit 1
  fi
  rclone --config "$CONFIG_DIR/rclone.conf" copy "$BACKUP_FILE" "$RCLONE_REMOTE"
  rclone --config "$CONFIG_DIR/rclone.conf" copy "$CHECKSUM_FILE" "$RCLONE_REMOTE"
fi

echo "Backup generado: $BACKUP_FILE"
