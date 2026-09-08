#Requires -RunAsAdministrator
<#
Crea la base de datos local, el usuario dedicado de MySQL y el usuario
administrador inicial del sistema.

Se puede usar de dos formas:
  - Manual (como hasta ahora): sin parametros, pregunta todo con Read-Host.
  - No interactiva (usada por el instalador Setup.exe): pasando todos los
    parametros de una, no pregunta nada.
#>
param(
    [string]$DbName = "",
    [string]$DbUser = "",
    [string]$RootPassword = "",
    [string]$DbPassword = "",
    [string]$AdminUsername = "",
    [string]$AdminPassword = "",
    [string]$GymName = "",
    [switch]$NonInteractive
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\scripts\logging.ps1"

function ConvertFrom-SecureStringPlain($secure) {
    return [System.Net.NetworkCredential]::new('', $secure).Password
}

Write-Log "=== Configuracion de la base de datos local ==="

if ($NonInteractive) {
    if (-not $DbName) { $DbName = "equinoxgym" }
    if (-not $DbUser) { $DbUser = "equinox_app" }
    if (-not $AdminUsername) { $AdminUsername = "admin" }
    if (-not $GymName) { $GymName = "Gym System" }
    foreach ($valor in @{ RootPassword = $RootPassword; DbPassword = $DbPassword; AdminPassword = $AdminPassword }.GetEnumerator()) {
        if (-not $valor.Value) { Exit-WithError "Falta el parametro $($valor.Key) en modo no interactivo." }
    }
} else {
    if (-not $DbName) {
        $DbName = Read-Host "Nombre de la base [equinoxgym]"
        if ([string]::IsNullOrWhiteSpace($DbName)) { $DbName = "equinoxgym" }
    }
    if (-not $DbUser) {
        $DbUser = Read-Host "Usuario de MySQL para la app [equinox_app]"
        if ([string]::IsNullOrWhiteSpace($DbUser)) { $DbUser = "equinox_app" }
    }
    if (-not $RootPassword) {
        $RootPassword = ConvertFrom-SecureStringPlain (Read-Host "Contraseña de root de MySQL (la que configuraste al instalar MySQL)" -AsSecureString)
    }
    if (-not $DbPassword) {
        $DbPassword = ConvertFrom-SecureStringPlain (Read-Host "Contraseña nueva para el usuario $DbUser" -AsSecureString)
    }
    if (-not $AdminUsername) {
        $AdminUsername = Read-Host "Usuario administrador inicial [admin]"
        if ([string]::IsNullOrWhiteSpace($AdminUsername)) { $AdminUsername = "admin" }
    }
    if (-not $AdminPassword) {
        $AdminPassword = ConvertFrom-SecureStringPlain (Read-Host "Contraseña del administrador inicial del sistema" -AsSecureString)
    }
    if (-not $GymName) {
        $GymName = Read-Host "Nombre del gimnasio, tal como aparece en el sistema [Gym System]"
        if ([string]::IsNullOrWhiteSpace($GymName)) { $GymName = "Gym System" }
    }
}

if ($DbName -notmatch '^[A-Za-z0-9_]+$') {
    Exit-WithError "El nombre de la base solo puede usar letras, numeros y guion bajo."
}
if ($DbUser -notmatch '^[A-Za-z0-9_]+$') {
    Exit-WithError "El usuario de MySQL solo puede usar letras, numeros y guion bajo."
}
if ($DbPassword.Length -lt 12 -or $AdminPassword.Length -lt 12) {
    Exit-WithError "Las contraseñas de la base y del administrador deben tener al menos 12 caracteres."
}

$mysqlExe = Find-MySqlClientExe
if (-not $mysqlExe) {
    Exit-WithError "No se encontro 'mysql.exe'. Verifica que MySQL Server este instalado correctamente."
}

$dbPasswordSql = $DbPassword.Replace("'", "''")

$sqlComandos = @(
    "CREATE DATABASE IF NOT EXISTS ``$DbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
    "CREATE USER IF NOT EXISTS '$DbUser'@'localhost' IDENTIFIED BY '$dbPasswordSql';",
    "ALTER USER '$DbUser'@'localhost' IDENTIFIED BY '$dbPasswordSql';",
    "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP ON ``$DbName``.* TO '$DbUser'@'localhost'; FLUSH PRIVILEGES;"
)

Write-Log "Creando la base '$DbName' y el usuario '$DbUser'..."
foreach ($sql in $sqlComandos) {
    $salidaError = & $mysqlExe -u root "--password=$RootPassword" -e $sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        if ($salidaError -match "Access denied") {
            Exit-WithError "La contraseña de root es incorrecta."
        }
        Exit-WithError "Fallo un comando de MySQL: $salidaError"
    }
}

$configDir = "C:\ProgramData\EquinoxGym\config"
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

$dbUrl = "jdbc:mysql://localhost:3306/$DbName" + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"

@"
SERVER_PORT=8085
DB_URL=$dbUrl
DB_USERNAME=$DbUser
DB_PASSWORD=$DbPassword
JPA_DDL_AUTO=update
THYMELEAF_CACHE=true
EQUINOX_ADMIN_USERNAME=$AdminUsername
EQUINOX_ADMIN_PASSWORD=$AdminPassword
GYM_NAME=$GymName
GYM_LOGO_PATH=

# Envio automatico de comprobantes y recordatorios por email (desactivado
# hasta cargar una cuenta de Gmail con "contrasena de aplicacion"; ver
# deploy/windows/README.md).
EMAIL_NOTIFICACIONES_HABILITADO=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
RECORDATORIO_DIAS_ANTICIPACION=3
"@ | Set-Content -Path (Join-Path $configDir "equinox.env") -Encoding UTF8

@"
[client]
host=localhost
user=$DbUser
password=$DbPassword
"@ | Set-Content -Path (Join-Path $configDir "backup.cnf") -Encoding ASCII

Write-Log "Base creada y configuracion guardada en $configDir."
if (-not $NonInteractive) {
    Write-Log "Ahora podes iniciar el sistema con: Start-ScheduledTask -TaskName EquinoxGym"
}
