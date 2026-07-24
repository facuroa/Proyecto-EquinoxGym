#!/usr/bin/env bash
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Ejecutá este script con sudo."
  exit 1
fi

read -r -p "Nombre de la base [equinoxgym]: " DB_NAME
DB_NAME=${DB_NAME:-equinoxgym}
if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "El nombre de la base solo puede usar letras, números y guion bajo."
  exit 1
fi

read -r -p "Usuario de MySQL [equinox_app]: " DB_USER
DB_USER=${DB_USER:-equinox_app}
if [[ ! "$DB_USER" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "El usuario de MySQL solo puede usar letras, números y guion bajo."
  exit 1
fi
read -r -s -p "Contraseña nueva para MySQL: " DB_PASSWORD
echo
read -r -s -p "Contraseña del administrador inicial de Equinox: " ADMIN_PASSWORD
echo

if [[ ${#DB_PASSWORD} -lt 12 || ${#ADMIN_PASSWORD} -lt 12 ]]; then
  echo "Usá contraseñas de al menos 12 caracteres."
  exit 1
fi

DB_PASSWORD_SQL=${DB_PASSWORD//\'/\'\'}

mysql -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -e "CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD_SQL';"
mysql -e "ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD_SQL';"
mysql -e "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost'; FLUSH PRIVILEGES;"

install -d -o root -g equinox -m 750 /etc/equinox-gym
cat > /etc/equinox-gym/equinox.env <<EOF
SERVER_PORT=8085
DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASSWORD
JPA_DDL_AUTO=update
THYMELEAF_CACHE=true
EQUINOX_ADMIN_USERNAME=admin
EQUINOX_ADMIN_PASSWORD=$ADMIN_PASSWORD
EOF
chown root:equinox /etc/equinox-gym/equinox.env
chmod 640 /etc/equinox-gym/equinox.env

cat > /etc/equinox-gym/backup.cnf <<EOF
[client]
host=localhost
user=$DB_USER
password=$DB_PASSWORD
EOF
chown root:root /etc/equinox-gym/backup.cnf
chmod 600 /etc/equinox-gym/backup.cnf

echo "Base creada y configuración privada guardada."
