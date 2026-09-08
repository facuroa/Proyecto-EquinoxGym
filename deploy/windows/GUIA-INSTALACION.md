# Guía rápida: instalar en la PC del gimnasio

Seguí esta guía en orden, de punta a punta, la primera vez que instalás el
sistema en la PC real del gimnasio. Es la versión corta y lineal; el detalle
de cada paso (instalación manual, backups, restauración, acceso desde otras
PCs) sigue en [README.md](README.md).

## Paso 0 — Obligatorio antes de ir al gimnasio: probar en una PC limpia

El instalador nunca se probó de punta a punta en una PC sin Java ni MySQL
(la rama que instala MySQL desde cero, en
`scripts/verificar-instalar-mysql.ps1`, está armada según la documentación
oficial pero no se ejecutó nunca en modo silencioso real). Antes de ir al
gimnasio:

- [ ] Conseguí una PC o una VM Windows 10/11 limpia (sin Java ni MySQL).
- [ ] Copiá `EquinoxGym-Setup.exe` y ejecutalo ahí.
- [ ] Confirmá que detecta que no hay MySQL y lo instala solo.
- [ ] Confirmá que al terminar el servicio `MySQL80` queda "Running".
- [ ] Confirmá que el sistema abre solo en `http://localhost:8085`.
- [ ] Reiniciá esa PC y confirmá que el sistema vuelve a levantar solo.

Si algo de esto falla, revisá `C:\ProgramData\EquinoxGym\install.log` (y
`mysql-server-install.log` si el paso que falla es el de MySQL) **antes** de
instalar en la PC real del gimnasio.

## Paso 1 — Instalar en la PC del gimnasio

1. Copiá `EquinoxGym-Setup.exe` a la PC que va a quedar de servidor (la de
   recepción, prendida todo el horario del gimnasio).
2. Ejecutalo (pide permisos de administrador).
3. Contraseña de MySQL: si ya hay MySQL instalado, la contraseña actual de
   `root`; si no, elegí una nueva (se instala MySQL solo).
4. Datos del gimnasio: nombre del gimnasio, usuario administrador y su
   contraseña (mínimo 12 caracteres).
5. Esperá unos minutos. No cierres la ventana.
6. Al terminar, dejá tildado "Iniciar EquinoxGym ahora" y hacé clic en
   Finalizar.
7. Se abre solo el navegador en `http://localhost:8085`. Iniciá sesión con
   el usuario administrador que creaste en el paso 4.

Si algo falla, el instalador corta y muestra el motivo puntual. El detalle
completo queda en `C:\ProgramData\EquinoxGym\install.log`.

## Paso 2 — Verificación post-instalación

- [ ] `http://localhost:8085` abre y podés iniciar sesión.
- [ ] El nombre y el logo que aparecen en el sistema son los correctos.
- [ ] Reiniciar la PC: el sistema vuelve a estar arriba solo, sin iniciar sesión.
- [ ] Desde otra PC de la red del gimnasio, `http://<ip-del-servidor>:8085`
      funciona (ver "Acceso desde otras computadoras" en el README para
      obtener la IP).
- [ ] `Start-ScheduledTask -TaskName EquinoxGymBackup` genera un `.zip` en
      `C:\ProgramData\EquinoxGym\backups`.

## Paso 3 — Opcional: activar comprobantes y recordatorios por email

Sin este paso, el sistema funciona igual — solo no manda los emails
automáticos (el link de WhatsApp con el mensaje pre-armado funciona siempre,
no depende de esto).

1. En la cuenta de Gmail del gimnasio: Seguridad → Verificación en dos pasos
   (activarla si no está) → Contraseñas de aplicaciones → generar una.
2. Abrí `C:\ProgramData\EquinoxGym\config\equinox.env` con el Bloc de notas
   y completá:
   ```text
   EMAIL_NOTIFICACIONES_HABILITADO=true
   MAIL_USERNAME=el-email-del-gimnasio@gmail.com
   MAIL_PASSWORD=la-contrasena-de-aplicacion-de-16-caracteres
   ```
3. Reiniciá el sistema:
   ```powershell
   Restart-ScheduledTask -TaskName EquinoxGym
   ```
4. Probá registrando un pago a un socio con email cargado: debería llegarle
   el comprobante en unos segundos.

## Paso 4 — Opcional: logo del gimnasio

Ver "Cambiar el logo del gimnasio" en el [README.md](README.md).

## Si algo sale mal

- Log de instalación: `C:\ProgramData\EquinoxGym\install.log`.
- Log de instalación de MySQL (solo si se instaló desde cero):
  `C:\ProgramData\EquinoxGym\mysql-server-install.log`.
- Reiniciar el sistema después de tocar `equinox.env`:
  `Restart-ScheduledTask -TaskName EquinoxGym`.
- Desinstalar (Panel de control → Programas) **no borra**
  `C:\ProgramData\EquinoxGym` (config, base de datos, backups, logs).
