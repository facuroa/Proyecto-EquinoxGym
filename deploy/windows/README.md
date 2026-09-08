# EquinoxGym — instalación local en Windows

Este kit instala una instancia aislada para **un solo gimnasio**. La
aplicación, MySQL y los backups quedan en la PC que se use como servidor
local (por ejemplo, la de recepción). Los demás equipos del gimnasio solo
necesitan un navegador.

> Para instalar en la PC real de un cliente, seguí la guía corta y lineal en
> [GUIA-INSTALACION.md](GUIA-INSTALACION.md). Lo que sigue acá abajo es la
> referencia detallada de cada paso, la instalación manual/avanzada y las
> tareas de mantenimiento (backups, restauración, cambiar el logo, etc.).

## Instalación con Setup.exe (recomendado para clientes)

Para entregar el sistema a un cliente, el proceso es:

1. Bajar el paquete (`EquinoxGym-Setup.exe`, ~1 GB) desde el Google Drive.
2. Ejecutar `EquinoxGym-Setup.exe` (pide permisos de administrador).
3. Completar la contraseña de MySQL (si MySQL ya está instalado, pide la
   contraseña actual de `root`; si no, pide una nueva y se instala solo).
4. Completar el nombre del gimnasio, el usuario administrador y su contraseña.
5. Esperar unos minutos mientras instala MySQL (si hacía falta), crea la base,
   el usuario dedicado `equinox_app` y el administrador, y registra el
   inicio automático.
6. Hacer clic en Finalizar (con "Iniciar EquinoxGym ahora" tildado).
7. Se abre solo el navegador en `http://localhost:8085`, listo para usar.

No hace falta instalar Java a mano: el instalador trae su propio runtime de
Java (generado con `jlink`), separado del resto del sistema.

Todo el detalle de errores queda en `C:\ProgramData\EquinoxGym\install.log`.
Si algo falla, el instalador corta y muestra el mensaje puntual (por ejemplo
"La contraseña de root es incorrecta." o "No se pudo iniciar el servicio
MySQL.").

Desinstalar (Panel de control > Programas) detiene y borra el inicio
automático y los accesos directos, pero **no borra** `C:\ProgramData\EquinoxGym`
(config, base de datos, backups, logs) — así no se pierde información del
gimnasio por accidente.

### Generar el Setup.exe (para quien mantiene el proyecto)

Requiere, en la PC donde se genera el instalador (no en la del cliente):

- JDK 17+ con `jlink` en el PATH (ya lo usa `mvnw`).
- Inno Setup 6: `winget install --id JRSoftware.InnoSetup`
- El instalador offline de MySQL en
  `deploy\windows\installer\bin\mysql-installer-community-*.msi`
  (bajarlo una vez de https://dev.mysql.com/downloads/installer/ — el paquete
  "offline", **no** el "web", pesa ~566 MB).

Después:

```powershell
powershell -ExecutionPolicy Bypass -File deploy\windows\installer\build.ps1
```

Esto compila el jar, genera el runtime en `deploy\windows\runtime\` y deja
`EquinoxGym-Setup.exe` en `deploy\windows\installer\Output\`. Usar
`-SkipTests` para iterar más rápido mientras se prueba el instalador.

### Checklist de prueba en PC limpia (antes de entregar a un cliente real)

Esta PC de desarrollo ya tiene Java y MySQL instalados, así que solo se probó
acá la rama "MySQL ya instalado". **Antes de entregarlo a un cliente real**,
probar una vez en una PC (o VM) sin Java ni MySQL — ver el "Paso 0" de
[GUIA-INSTALACION.md](GUIA-INSTALACION.md) para el checklist completo.

## Instalación manual / avanzada (sin Setup.exe)

Alternativa para quien prefiera instalar Java y MySQL a mano, o reinstalar
solo una parte. Requiere ejecutar los `.bat`/`.ps1` de esta carpeta uno por
uno, como se detalla abajo.

### Antes de empezar

Instalá en la PC del gimnasio, en este orden:

1. **Java 17 o superior** — [Microsoft Build of OpenJDK](https://learn.microsoft.com/java/openjdk/download)
   o [Adoptium Temurin](https://adoptium.net/) (instalador `.msi`, dejá tildada
   la opción de agregarlo al `PATH`).
2. **MySQL Community Server** — descargalo desde
   [dev.mysql.com/downloads/installer](https://dev.mysql.com/downloads/installer/).
   Durante la instalación:
   - Anotá la contraseña de `root` que elijas (la vas a necesitar en el paso 3).
   - Dejá tildada la opción de agregar los binarios (`mysql`, `mysqldump`) al `PATH`.

Verificá que quedaron accesibles abriendo una consola nueva:

```powershell
java -version
mysql --version
mysqldump --version
```

Copiá la carpeta de instalación (o el ZIP) a esa PC.

### Instalación

Todos los scripts hay que ejecutarlos como administrador. Si hacés doble
click en los `.bat`, ellos mismos piden la elevación (aparece el cuadro de
Windows "¿Permitir que esta app...?").

1. **Instalá los archivos:**

   Doble click en `instalar.bat` (o, desde una consola de administrador,
   `.\instalar.ps1`). Esto:
   - Copia la aplicación a `C:\ProgramData\EquinoxGym\`.
   - Abre el puerto 8085 en el Firewall de Windows, **solo para la red local**.
   - Registra el inicio automático en el Programador de tareas de Windows
     (arranca solo al prender la PC, incluso sin iniciar sesión).
   - Registra el backup diario automático (02:30).

2. **Creá la base de datos local:**

   Doble click en `crear-base-local.bat`. Te va a pedir:
   - La contraseña de `root` de MySQL (la del paso de instalación).
   - Una contraseña nueva para el usuario de la aplicación.
   - La contraseña del administrador inicial del sistema.
   - **El nombre del gimnasio** (aparece en el sistema en vez de "Gym System").

   Estos datos quedan guardados en `C:\ProgramData\EquinoxGym\config\equinox.env`.

3. **Revisá la configuración privada si necesitás cambiar algún valor:**

   ```powershell
   notepad C:\ProgramData\EquinoxGym\config\equinox.env
   ```

   Si cambiás algo acá, hay que reiniciar la tarea (`Restart-ScheduledTask -TaskName EquinoxGym`).

4. **Iniciá el sistema:**

   ```powershell
   Start-ScheduledTask -TaskName EquinoxGym
   ```

   Ya va a quedar iniciando solo cada vez que se prenda la PC.

5. **Abrí en esa misma PC:**

   ```text
   http://localhost:8085
   ```

### Cambiar el logo del gimnasio

Copiá el logo del cliente (PNG o JPG) a
`C:\ProgramData\EquinoxGym\branding\logo.png`, y editá `equinox.env` para
que `GYM_LOGO_PATH` apunte a esa ruta. Por ejemplo:

```text
GYM_LOGO_PATH=C:\ProgramData\EquinoxGym\branding\logo.png
```

Reiniciá con `Restart-ScheduledTask -TaskName EquinoxGym` y listo — no hace
falta recompilar nada.

### Comprobantes y recordatorios automáticos por email

El sistema puede mandar el comprobante de pago por email apenas se registra
un cobro, y un recordatorio unos días antes de que venza una cuota (a los
socios que tengan email cargado). Viene apagado por defecto. Para activarlo,
ver el "Paso 3" de [GUIA-INSTALACION.md](GUIA-INSTALACION.md) — en resumen,
se completan `MAIL_USERNAME`/`MAIL_PASSWORD` (una contraseña de aplicación de
Gmail) en `equinox.env` y se pone `EMAIL_NOTIFICACIONES_HABILITADO=true`.

Los días de anticipación del recordatorio se controlan con
`RECORDATORIO_DIAS_ANTICIPACION` (por defecto 3). El envío por WhatsApp
(desde el comprobante de pago y desde la pantalla de Morosidad) no depende
de esta configuración: siempre arma un link a `wa.me` con el mensaje ya
escrito, listo para que quien esté en recepción lo mande con un clic.

### Acceso desde otras computadoras del gimnasio

Consultá la IP local de la PC servidor:

```powershell
ipconfig
```

Si la IP fuera `192.168.1.50`, los equipos conectados a la misma red del
gimnasio entran a:

```text
http://192.168.1.50:8085
```

Conviene reservar esa IP en el router (DHCP fijo) para que no cambie. La
regla de Firewall que crea `instalar.ps1` ya limita el acceso a la red
local — no hace falta (ni conviene) abrir el puerto 8085 en el router hacia
internet.

### Backup diario local

El instalador configura un backup diario a las 02:30. Se guarda comprimido en:

```text
C:\ProgramData\EquinoxGym\backups
```

Se conservan 30 copias locales. Para probarlo manualmente:

```powershell
Start-ScheduledTask -TaskName EquinoxGymBackup
Get-ChildItem C:\ProgramData\EquinoxGym\backups
```

### Restauración de una copia

Detené la aplicación antes de restaurar, y hacelo solo con autorización del
dueño del gimnasio:

```powershell
Stop-ScheduledTask -TaskName EquinoxGym

Expand-Archive -Path "C:\ProgramData\EquinoxGym\backups\equinoxgym-AAAA-MM-DD_HH-mm-ss.sql.zip" `
    -DestinationPath "$env:TEMP\equinox-restore" -Force
mysql --defaults-extra-file="C:\ProgramData\EquinoxGym\config\backup.cnf" equinoxgym `
    -e "source $env:TEMP\equinox-restore\equinoxgym-AAAA-MM-DD_HH-mm-ss.sql"

Start-ScheduledTask -TaskName EquinoxGym
```

Antes de depender de los backups, hacé una prueba de restauración en una
base de prueba, no sobre la base que está en uso.

### Checklist de prueba manual (antes de llevarlo a un cliente)

Probá esto una vez en una PC real (o una máquina virtual) antes de la
primera instalación en un gimnasio:

- [ ] `instalar.bat` corre sin errores y crea `C:\ProgramData\EquinoxGym\`.
- [ ] La tarea `EquinoxGym` aparece en el Programador de tareas y está habilitada.
- [ ] Después de `crear-base-local.bat`, `equinox.env` tiene los valores correctos.
- [ ] `Start-ScheduledTask -TaskName EquinoxGym` deja el sistema accesible en `http://localhost:8085`.
- [ ] Reiniciar la PC: el sistema vuelve a estar arriba solo, sin iniciar sesión.
- [ ] Matar el proceso `javaw` a mano (Administrador de tareas): el Programador de tareas lo reinicia solo en un minuto.
- [ ] Desde otra PC de la misma red, `http://<ip-del-servidor>:8085` funciona.
- [ ] `Start-ScheduledTask -TaskName EquinoxGymBackup` genera un `.zip` en `backups`.
