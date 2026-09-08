# Guía: actualizar una instalación existente con un jar nuevo

Esta guía es para **actualizar el programa en la PC de un gimnasio que ya lo
tiene instalado**, sin volver a correr el `Setup.exe` de 686 MB. Solo se
reemplaza el `.jar`.

> **Si estás leyendo esto desde una sesión nueva de Claude Code en la PC del
> cliente:** este archivo es el procedimiento a seguir. Los datos del gimnasio
> (base de datos, configuración, backups) **no se tocan** en ninguno de estos
> pasos.

## Qué se lleva en el pendrive

Un solo archivo: el jar compilado, que en la PC de desarrollo queda en
`target\EquinoxGym-0.0.1-SNAPSHOT.jar`.

⚠️ **Importante:** en la PC del cliente el archivo tiene que llamarse
**`EquinoxGym.jar`** (sin la versión). Si lo copiás con el nombre largo, el
sistema no arranca.

## Datos de la instalación (para ubicarse)

| Qué | Dónde |
|---|---|
| El programa | `C:\ProgramData\EquinoxGym\app\EquinoxGym.jar` |
| Configuración del gimnasio | `C:\ProgramData\EquinoxGym\config\equinox.env` |
| Backups automáticos | `C:\ProgramData\EquinoxGym\backups\` |
| Log de instalación | `C:\ProgramData\EquinoxGym\install.log` |
| Java embebido | `C:\ProgramData\EquinoxGym\runtime\bin\javaw.exe` |
| Tarea de arranque | `EquinoxGym` (Programador de tareas) |
| Tarea de backup | `EquinoxGymBackup` (diaria, 02:30) |
| La app corre en | `http://localhost:8085` |

## Procedimiento (todo en PowerShell **como administrador**)

### Paso 1 — Backup de la base ANTES de tocar nada

```powershell
Start-ScheduledTask -TaskName EquinoxGymBackup
Start-Sleep -Seconds 20
Get-ChildItem C:\ProgramData\EquinoxGym\backups | Sort-Object LastWriteTime -Descending | Select-Object -First 3
```

**No sigas si no aparece un `.zip` con la fecha de hoy.**

### Paso 2 — Detener la aplicación

```powershell
Stop-ScheduledTask -TaskName EquinoxGym
Get-Process java, javaw -ErrorAction SilentlyContinue | Stop-Process -Force
```

### Paso 3 — Guardar el jar actual (para poder volver atrás)

```powershell
Copy-Item "C:\ProgramData\EquinoxGym\app\EquinoxGym.jar" `
          "C:\ProgramData\EquinoxGym\app\EquinoxGym.jar.backup" -Force
```

### Paso 4 — Copiar el jar nuevo desde el pendrive

Reemplazá `E:` por la letra que tenga el pendrive:

```powershell
Copy-Item "E:\EquinoxGym-0.0.1-SNAPSHOT.jar" `
          "C:\ProgramData\EquinoxGym\app\EquinoxGym.jar" -Force
```

### Paso 5 — Levantar de nuevo

```powershell
Start-ScheduledTask -TaskName EquinoxGym
Start-Sleep -Seconds 40
Start-Process "http://localhost:8085"
```

La primera vez que arranca con un jar nuevo tarda más de lo normal (ajusta la
base de datos si hubo cambios de estructura; eso es automático).

## Paso 6 — Verificar el nombre del gimnasio

El nombre **no está dentro del jar**: sale de `GYM_NAME` en `equinox.env`, y
reemplazar el jar no toca ese archivo. Aun así conviene confirmarlo, porque de
ahí sale el nombre del inicio, de los comprobantes (pantalla y PDF) y de los
emails.

```powershell
Select-String -Path C:\ProgramData\EquinoxGym\config\equinox.env -Pattern "GYM_NAME"
```

Tiene que devolver exactamente:

```text
GYM_NAME=Keep Fit Gym
```

Si está vacío, dice otra cosa o la línea no existe, corregirlo y reiniciar:

```powershell
notepad C:\ProgramData\EquinoxGym\config\equinox.env
# dejar la linea: GYM_NAME=Keep Fit Gym
Restart-ScheduledTask -TaskName EquinoxGym
```

No hace falta recompilar ni volver a copiar el jar: el nombre se lee al
arrancar.

## Paso 7 — Verificar que cargó el jar nuevo

No alcanza con que abra: hay que confirmar que está corriendo **la versión
nueva**. Señales visibles según los últimos cambios:

- [ ] Las fechas se ven como **`24/08/2026`** y no como `2026-08-24`.
      *(Si siguen en formato ISO, quedó el jar viejo.)*
- [ ] En **Cuotas**, los botones Cobrar / Editar / Eliminar están separados,
      no pegados.
- [ ] En **Socios**, un socio dado de alta sin cobrar muestra el badge
      **"Sin cobro"** en vez de "Vigente".
- [ ] Al editar la fecha de inicio del plan de un socio y guardar, el
      **dashboard refleja el cambio** en "Vencen en 7 días".
- [ ] **El arreglo que pidió el gimnasio:** buscar un socio cuya cuota venza en
      pocos días, cobrarle la renovación **antes del vencimiento**, y confirmar
      que en el listado de Socios la columna "Vence plan" **avanza un período**
      (no se queda en "Faltan 3 días").

Además, chequeo general:

- [ ] Se puede iniciar sesión.
- [ ] El listado de socios trae los datos de siempre (no se perdió nada).
- [ ] Se puede registrar un cobro de prueba y anularlo después.
- [ ] En el inicio y en un comprobante figura **Keep Fit Gym** (ver Paso 6).

## Si algo sale mal: volver atrás

```powershell
Stop-ScheduledTask -TaskName EquinoxGym
Copy-Item "C:\ProgramData\EquinoxGym\app\EquinoxGym.jar.backup" `
          "C:\ProgramData\EquinoxGym\app\EquinoxGym.jar" -Force
Start-ScheduledTask -TaskName EquinoxGym
```

Con esto vuelve a la versión anterior. La base de datos no se toca en ningún
momento, así que no se pierde información.

## Diagnóstico de problemas

### La app no levanta

```powershell
# ¿Está corriendo el proceso?
Get-Process java, javaw -ErrorAction SilentlyContinue

# ¿Alguien está usando el puerto 8085?
Get-NetTCPConnection -LocalPort 8085 -ErrorAction SilentlyContinue

# Estado de la tarea programada
Get-ScheduledTask -TaskName EquinoxGym | Get-ScheduledTaskInfo
```

**Para ver el error real, arrancar la app a mano en primer plano** (así se ve
la salida en pantalla en vez de quedar oculta):

```powershell
Stop-ScheduledTask -TaskName EquinoxGym
& "C:\ProgramData\EquinoxGym\runtime\bin\java.exe" -jar "C:\ProgramData\EquinoxGym\app\EquinoxGym.jar"
```

Ahí se ve el stack trace completo. `Ctrl+C` para cortar.

### Errores frecuentes y qué significan

| Mensaje | Causa | Solución |
|---|---|---|
| `Access denied for user` | La contraseña de MySQL en `equinox.env` no coincide | Revisar `DB_PASSWORD` en `equinox.env` |
| `Communications link failure` | El servicio de MySQL está caído | `Start-Service MySQL80` (o el nombre que tenga) |
| `Port 8085 was already in use` | Quedó un proceso viejo colgado | Matar el proceso java y reintentar |
| `Table ... doesn't exist` | La base está vacía o apunta a otra base | Revisar `DB_URL` en `equinox.env` |

### Estado de MySQL

```powershell
Get-Service | Where-Object { $_.Name -like 'MySQL*' } | Select-Object Name, Status
```

### Ver la configuración actual (sin exponer contraseñas)

```powershell
Get-Content C:\ProgramData\EquinoxGym\config\equinox.env | Where-Object { $_ -notmatch 'PASSWORD' }
```

## Cosas que NO hay que hacer

- ❌ **No borrar ni editar** `C:\ProgramData\EquinoxGym\config\equinox.env` —
  tiene la contraseña de la base y el nombre del gimnasio.
- ❌ **No correr el `Setup.exe`** de nuevo para actualizar: es para
  instalaciones desde cero.
- ❌ **No borrar** `C:\ProgramData\EquinoxGym\` — ahí vive la base de datos.
- ❌ No copiar el jar con el nombre `EquinoxGym-0.0.1-SNAPSHOT.jar`: tiene que
  quedar como `EquinoxGym.jar`.

## Activar los emails automáticos (opcional)

Si el gimnasio quiere que salgan los comprobantes y recordatorios por mail,
ver el "Paso 3" de [GUIA-INSTALACION.md](GUIA-INSTALACION.md).
