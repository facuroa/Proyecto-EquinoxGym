# Equinox Gym — instalación local en Linux Mint

Este kit instala una instancia aislada para **un solo gimnasio**. La aplicación, MySQL y los backups quedan en la notebook o PC que se use como servidor local. Los demás equipos del gimnasio solo necesitan un navegador.

## Antes de empezar

En Linux Mint, instalá los requisitos:

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless mysql-server mysql-client rclone
```

Copiá el archivo ZIP desde Google Drive, OneDrive o Dropbox a la notebook, descomprimilo y abrí una terminal en esa carpeta.

Como el ZIP se creó desde Windows, habilitá primero la ejecución de los scripts:

```bash
chmod +x instalar.sh crear-base-local.sh scripts/backup.sh
```

## Instalación

1. Instalá los archivos de Equinox:

   ```bash
   sudo ./instalar.sh
   ```

2. Creá la base de datos local y elegí las contraseñas:

   ```bash
   sudo ./crear-base-local.sh
   ```

   El script crea la base `equinoxgym`, un usuario exclusivo de MySQL y el usuario administrador inicial de Equinox.

3. Revisá la configuración privada si necesitás cambiar algún valor:

   ```bash
   sudo nano /etc/equinox-gym/equinox.env
   ```

4. Iniciá el sistema y dejalo configurado para arrancar al encender la notebook:

   ```bash
   sudo systemctl enable --now equinox-gym
   ```

5. Abrí en la notebook:

   ```text
   http://localhost:8085
   ```

## Acceso desde otras computadoras del gimnasio

Consultá la IP local de la notebook:

```bash
hostname -I
```

Si la IP fuera `192.168.1.50`, los equipos conectados a la misma red del gimnasio entran a:

```text
http://192.168.1.50:8085
```

Conviene reservar una IP fija para la notebook en el router. Si usás UFW, permití únicamente la subred local (ajustá la subred según tu red):

```bash
sudo ufw allow from 192.168.1.0/24 to any port 8085 proto tcp
```

No abras el puerto 8085 en el router hacia internet y no expongas MySQL fuera de la notebook.

## Backup diario local

El instalador configura un backup diario a las 02:30. Se guarda en:

```text
/var/backups/equinox-gym
```

Se conservan 30 copias locales. Para probarlo manualmente:

```bash
sudo systemctl start equinox-backup.service
sudo ls -lh /var/backups/equinox-gym
```

## Copia cifrada a Google Drive

La copia local funciona aunque Google Drive no esté configurado. Para sumar la nube:

1. Configurá una conexión de Google Drive como administrador. `rclone` abrirá un enlace para autorizar tu cuenta:

   ```bash
   sudo rclone config --config /etc/equinox-gym/rclone.conf
   ```

   Creá primero un remoto de tipo `drive` llamado `gdrive`. Luego creá un remoto de tipo `crypt` llamado `gdrive-crypt` que apunte a `gdrive:EquinoxGym-Backups`.

2. Editá la configuración del backup:

   ```bash
   sudo nano /etc/equinox-gym/backup.env
   ```

   Reemplazá esta línea:

   ```text
   RCLONE_REMOTE=
   ```

   por:

   ```text
   RCLONE_REMOTE=gdrive-crypt:
   ```

3. Protegé las configuraciones y probá el backup:

   ```bash
   sudo chmod 600 /etc/equinox-gym/backup.cnf /etc/equinox-gym/backup.env /etc/equinox-gym/rclone.conf
   sudo systemctl start equinox-backup.service
   sudo journalctl -u equinox-backup.service -n 30 --no-pager
   ```

La nube es una segunda copia: no reemplaza el backup local. No subas la base de datos sin cifrar.

## Restauración de una copia

Detené la aplicación antes de restaurar y hacelo solo con autorización del dueño del gimnasio:

```bash
sudo systemctl stop equinox-gym
gunzip -c /var/backups/equinox-gym/equinoxgym-AAAA-MM-DD.sql.gz | \
  mysql --defaults-extra-file=/etc/equinox-gym/backup.cnf equinoxgym
sudo systemctl start equinox-gym
```

Antes de depender de los backups, hacé una prueba de restauración en una base de prueba, no sobre la base que está en uso.
