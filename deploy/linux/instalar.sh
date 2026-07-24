#!/usr/bin/env bash
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Ejecutá este script con sudo."
  exit 1
fi

for command in java mysql mysqldump systemctl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Falta $command. Instalá los requisitos indicados en README.md."
    exit 1
  fi
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="/opt/equinox-gym"
CONFIG_DIR="/etc/equinox-gym"
BACKUP_DIR="/var/backups/equinox-gym"

id -u equinox >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin equinox

install -d -o equinox -g equinox -m 750 "$APP_DIR/app"
install -d -o root -g equinox -m 750 "$CONFIG_DIR"
install -d -o root -g root -m 700 "$BACKUP_DIR"

install -o equinox -g equinox -m 640 "$SCRIPT_DIR/app/EquinoxGym.jar" "$APP_DIR/app/EquinoxGym.jar"
install -o root -g root -m 750 "$SCRIPT_DIR/scripts/backup.sh" "$APP_DIR/backup.sh"
install -o root -g root -m 750 "$SCRIPT_DIR/crear-base-local.sh" "$APP_DIR/crear-base-local.sh"

if [[ ! -f "$CONFIG_DIR/equinox.env" ]]; then
  install -o root -g equinox -m 640 "$SCRIPT_DIR/config/equinox.env.example" "$CONFIG_DIR/equinox.env"
fi
if [[ ! -f "$CONFIG_DIR/backup.env" ]]; then
  install -o root -g root -m 600 "$SCRIPT_DIR/config/backup.env.example" "$CONFIG_DIR/backup.env"
fi
if [[ ! -f "$CONFIG_DIR/backup.cnf" ]]; then
  install -o root -g root -m 600 "$SCRIPT_DIR/config/backup.cnf.example" "$CONFIG_DIR/backup.cnf"
fi

install -o root -g root -m 644 "$SCRIPT_DIR/systemd/equinox-gym.service" /etc/systemd/system/equinox-gym.service
install -o root -g root -m 644 "$SCRIPT_DIR/systemd/equinox-backup.service" /etc/systemd/system/equinox-backup.service
install -o root -g root -m 644 "$SCRIPT_DIR/systemd/equinox-backup.timer" /etc/systemd/system/equinox-backup.timer

systemctl daemon-reload
systemctl enable equinox-backup.timer

echo "Kit instalado. Ejecutá ahora: sudo $APP_DIR/crear-base-local.sh"
echo "Después iniciá la aplicación con: sudo systemctl enable --now equinox-gym"
