#Requires -RunAsAdministrator
<#
Instala los archivos de EquinoxGym, el auto-inicio (Programador de tareas) y
los accesos directos.

-Silent lo usa el instalador grafico (Setup.exe): en ese caso no se valida que
"java"/"mysql" esten en el PATH (el runtime de Java viaja bundleado y MySQL se
verifica/instala en un paso aparte), y no hace falta interaccion.
#>
param(
    [switch]$Silent
)

$ErrorActionPreference = "Stop"

$ScriptDir = $PSScriptRoot
$AppDir = "C:\ProgramData\EquinoxGym\app"
$RuntimeDir = "C:\ProgramData\EquinoxGym\runtime"
$ConfigDir = "C:\ProgramData\EquinoxGym\config"
$BackupDir = "C:\ProgramData\EquinoxGym\backups"
$BrandingDir = "C:\ProgramData\EquinoxGym\branding"

. "$ScriptDir\scripts\logging.ps1"

Write-Log "=== Instalando EquinoxGym ==="

if (-not $Silent) {
    foreach ($comando in @("java", "mysql", "mysqldump")) {
        if (-not (Get-Command $comando -ErrorAction SilentlyContinue)) {
            Exit-WithError "Falta '$comando' en el PATH. Instala los requisitos indicados en README.md antes de continuar."
        }
    }
}

$jarOrigen = Join-Path $ScriptDir "app\EquinoxGym.jar"
if (-not (Test-Path $jarOrigen)) {
    Exit-WithError "No se encontro $jarOrigen. Compila el proyecto y copia el jar ahi antes de instalar."
}

New-Item -ItemType Directory -Force -Path $AppDir | Out-Null
New-Item -ItemType Directory -Force -Path $ConfigDir | Out-Null
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
New-Item -ItemType Directory -Force -Path $BrandingDir | Out-Null

Copy-Item -Path $jarOrigen -Destination (Join-Path $AppDir "EquinoxGym.jar") -Force
Copy-Item -Path (Join-Path $ScriptDir "iniciar-equinox.ps1") -Destination (Join-Path $AppDir "iniciar-equinox.ps1") -Force
Copy-Item -Path (Join-Path $ScriptDir "scripts\backup.ps1") -Destination (Join-Path $AppDir "backup.ps1") -Force
Copy-Item -Path (Join-Path $ScriptDir "scripts\logging.ps1") -Destination (Join-Path $AppDir "logging.ps1") -Force

$runtimeOrigen = Join-Path $ScriptDir "runtime"
if (Test-Path $runtimeOrigen) {
    Write-Log "Copiando el runtime de Java bundleado..."
    New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
    Copy-Item -Path "$runtimeOrigen\*" -Destination $RuntimeDir -Recurse -Force
}

$equinoxEnv = Join-Path $ConfigDir "equinox.env"
if (-not (Test-Path $equinoxEnv)) {
    Copy-Item -Path (Join-Path $ScriptDir "config\equinox.env.example") -Destination $equinoxEnv
    Write-Log "Se creo $equinoxEnv con valores de ejemplo. Se completa con crear-base-local.ps1."
}

$backupEnv = Join-Path $ConfigDir "backup.env"
if (-not (Test-Path $backupEnv)) {
    Copy-Item -Path (Join-Path $ScriptDir "config\backup.env.example") -Destination $backupEnv
}

Write-Log "Configurando el Firewall de Windows (solo red local)..."
if (-not (Get-NetFirewallRule -DisplayName "EquinoxGym (LAN)" -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName "EquinoxGym (LAN)" -Direction Inbound -Protocol TCP `
        -LocalPort 8085 -RemoteAddress LocalSubnet4,LocalSubnet6 -Action Allow -Profile Any | Out-Null
}

Write-Log "Registrando el inicio automatico (Programador de tareas)..."
$accionInicio = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$AppDir\iniciar-equinox.ps1`""
$disparadorInicio = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
$configuracion = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask -TaskName "EquinoxGym" -Action $accionInicio -Trigger $disparadorInicio `
    -Principal $principal -Settings $configuracion -Force | Out-Null

Write-Log "Registrando el backup diario (02:30)..."
$accionBackup = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$AppDir\backup.ps1`""
$disparadorBackup = New-ScheduledTaskTrigger -Daily -At 2:30am

Register-ScheduledTask -TaskName "EquinoxGymBackup" -Action $accionBackup -Trigger $disparadorBackup `
    -Principal $principal -Settings $configuracion -Force | Out-Null

$launcherOrigen = Join-Path $ScriptDir "installer\assets\abrir-equinox.vbs"
if (Test-Path $launcherOrigen) {
    Write-Log "Creando accesos directos..."
    $launcherDestino = Join-Path $AppDir "abrir-equinox.vbs"
    Copy-Item -Path $launcherOrigen -Destination $launcherDestino -Force

    $iconoOrigen = Join-Path $ScriptDir "installer\assets\icono.ico"
    $shell = New-Object -ComObject WScript.Shell

    function New-EquinoxShortcut([string]$rutaLnk) {
        $acceso = $shell.CreateShortcut($rutaLnk)
        $acceso.TargetPath = "wscript.exe"
        $acceso.Arguments = "`"$launcherDestino`""
        $acceso.WorkingDirectory = $AppDir
        if (Test-Path $iconoOrigen) { $acceso.IconLocation = $iconoOrigen }
        $acceso.Description = "Abrir EquinoxGym"
        $acceso.Save()
    }

    New-EquinoxShortcut (Join-Path ([Environment]::GetFolderPath("CommonDesktopDirectory")) "EquinoxGym.lnk")

    $menuInicioDir = Join-Path ([Environment]::GetFolderPath("CommonPrograms")) "EquinoxGym"
    New-Item -ItemType Directory -Force -Path $menuInicioDir | Out-Null
    New-EquinoxShortcut (Join-Path $menuInicioDir "EquinoxGym.lnk")
}

Write-Log ""
Write-Log "Kit instalado."
if (-not $Silent) {
    Write-Log "Ahora ejecuta: .\crear-base-local.ps1 (como administrador)"
    Write-Log "Despues inicia el sistema con: Start-ScheduledTask -TaskName EquinoxGym"
}
