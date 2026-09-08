#Requires -RunAsAdministrator
<#
Verifica si hay un MySQL Server instalado y corriendo en esta PC. Si no lo hay,
lo instala en modo silencioso usando el instalador offline bundleado (el .msi
completo de MySQL Installer, que ya trae adentro los componentes del servidor,
sin necesitar internet).

Sintaxis de instalacion silenciosa segun la referencia oficial:
https://dev.mysql.com/doc/refman/8.0/en/MySQLInstallerConsole.html

Nota importante: esta rama (instalar MySQL desde cero) esta armada segun esa
documentacion pero no se probo en una instalacion silenciosa real durante el
desarrollo (para no romper el MySQL que ya esta usando el equipo de desarrollo).
Antes de entregar el instalador a un cliente, probarla una vez en una PC o VM
limpia (ver checklist en README.md).
#>
param(
    [Parameter(Mandatory = $true)][string]$RootPassword,
    [Parameter(Mandatory = $true)][string]$MysqlInstallerMsiPath,
    [string]$DbName = "equinoxgym"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\logging.ps1"

Write-Log "=== Verificando MySQL Server ==="

function Find-MySqlInstallerConsole {
    $rutas = @(
        "C:\Program Files\MySQL\MySQL Installer for Windows\MySQLInstallerConsole.exe",
        "C:\Program Files (x86)\MySQL\MySQL Installer for Windows\MySQLInstallerConsole.exe"
    )
    foreach ($ruta in $rutas) {
        if (Test-Path $ruta) { return $ruta }
    }
    return $null
}

$servicio = Get-MySqlService

if (-not $servicio) {
    Write-Log "No se detecto un servicio de MySQL. Instalando MySQL Server (esto puede tardar varios minutos)..."

    if (-not (Test-Path $MysqlInstallerMsiPath)) {
        Exit-WithError "MySQL no esta instalado y no se encontro el instalador bundleado en '$MysqlInstallerMsiPath'."
    }

    $msiLog = "C:\ProgramData\EquinoxGym\mysql-installer-setup.log"
    Write-Log "Instalando MySQL Installer for Windows (msiexec /quiet)..."
    $proceso = Start-Process -FilePath "msiexec.exe" `
        -ArgumentList "/i", "`"$MysqlInstallerMsiPath`"", "/quiet", "/norestart", "/log", "`"$msiLog`"" `
        -Wait -PassThru
    if ($proceso.ExitCode -ne 0 -and $proceso.ExitCode -ne 3010) {
        Exit-WithError "No se pudo instalar MySQL Installer (codigo $($proceso.ExitCode)). Revise $msiLog."
    }

    $consola = Find-MySqlInstallerConsole
    if (-not $consola) {
        Exit-WithError "MySQL Installer se instalo pero no se encontro MySQLInstallerConsole.exe."
    }

    Write-Log "Instalando MySQL Server 8.0 (modo consola silenciosa)..."
    $rootPasswordEscapada = $RootPassword.Replace('"', '""')
    $configServer = "type=config;open_win_firewall=true;general_log=false;bin_log=true;" +
        "server_id=1;tcp_ip=true;port=3306;root_passwd=$rootPasswordEscapada;" +
        "install_dir=`"C:\Program Files\MySQL\MySQL Server 8.0`""

    $argsInstalarServer = @("--install", "server;8.0.46;x64:*:$configServer", "--silent")
    $serverInstallLog = "C:\ProgramData\EquinoxGym\mysql-server-install.log"
    $proceso = Start-Process -FilePath $consola -ArgumentList $argsInstalarServer `
        -Wait -PassThru -RedirectStandardOutput $serverInstallLog -RedirectStandardError "$serverInstallLog.err"
    if ($proceso.ExitCode -ne 0) {
        Exit-WithError "No se pudo instalar MySQL Server (codigo $($proceso.ExitCode)). Revise $serverInstallLog."
    }

    Start-Sleep -Seconds 5
    $servicio = Get-MySqlService
    if (-not $servicio) {
        Exit-WithError "MySQL Server se instalo pero no aparece ningun servicio MySQL* en Windows."
    }
    Write-Log "MySQL Server instalado correctamente (servicio '$($servicio.Name)')."
} else {
    Write-Log "Servicio de MySQL detectado: '$($servicio.Name)'."
}

if ($servicio.Status -ne "Running") {
    Write-Log "El servicio '$($servicio.Name)' esta detenido. Iniciandolo..."
    try {
        Start-Service -Name $servicio.Name
        Start-Sleep -Seconds 3
        $servicio.Refresh()
    } catch {
        Exit-WithError "No se pudo iniciar el servicio MySQL ('$($servicio.Name)'): $($_.Exception.Message)"
    }
    if ($servicio.Status -ne "Running") {
        Exit-WithError "No se pudo iniciar el servicio MySQL ('$($servicio.Name)')."
    }
}
Write-Log "El servicio de MySQL esta corriendo."

$mysqlExe = Find-MySqlClientExe
if (-not $mysqlExe) {
    Exit-WithError "MySQL Server esta corriendo pero no se encontro el cliente 'mysql.exe' para verificar la conexion."
}

Write-Log "Probando la conexion con la contraseña de root indicada..."
$salidaError = & $mysqlExe -u root "--password=$RootPassword" -e "SELECT 1;" 2>&1
if ($LASTEXITCODE -ne 0) {
    if ($salidaError -match "Access denied") {
        Exit-WithError "La contraseña de root es incorrecta."
    }
    Exit-WithError "No se pudo conectar a MySQL: $salidaError"
}

Write-Log "Conexion a MySQL verificada correctamente."
exit 0
