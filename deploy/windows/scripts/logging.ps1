# Logging compartido para el instalador y los scripts de EquinoxGym.
# Uso: . "$PSScriptRoot\logging.ps1"  (o la ruta que corresponda) y despues Write-Log "mensaje".

$Script:EquinoxLogDir = "C:\ProgramData\EquinoxGym"
$Script:EquinoxLogFile = Join-Path $Script:EquinoxLogDir "install.log"

function Write-Log {
    param(
        [Parameter(Mandatory = $true)][string]$Message,
        [ValidateSet("INFO", "WARN", "ERROR")][string]$Level = "INFO"
    )

    New-Item -ItemType Directory -Force -Path $Script:EquinoxLogDir | Out-Null
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$timestamp] [$Level] $Message"

    Add-Content -Path $Script:EquinoxLogFile -Value $line -Encoding UTF8

    switch ($Level) {
        "ERROR" { Write-Host $Message -ForegroundColor Red }
        "WARN"  { Write-Host $Message -ForegroundColor Yellow }
        default { Write-Host $Message }
    }
}

function Write-LogError {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Log -Message $Message -Level "ERROR"
}

function Write-LogWarn {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Log -Message $Message -Level "WARN"
}

function Exit-WithError {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-LogError $Message
    # El instalador grafico (Setup.exe) lee este archivo con LoadStringFromFile
    # (AnsiString) para mostrar el mensaje de error exacto en un cuadro de
    # dialogo, por eso se guarda en la codificacion ANSI del sistema y no en UTF8.
    Set-Content -Path (Join-Path $Script:EquinoxLogDir "last-error.txt") -Value $Message -Encoding Default
    exit 1
}

# Todas las instalaciones de MySQL "a mano" que no tildaron la opcion de
# agregar los binarios al PATH terminan rompiendo estos scripts con un
# "'mysql' no se reconoce...". Por eso ningun script llama a `mysql`/
# `mysqldump` directamente: siempre resuelven la ruta completa con estas
# funciones (PATH primero, y si no, buscan bajo Program Files).
function Find-MySqlExecutable {
    param([Parameter(Mandatory = $true)][string]$NombreExe)

    $enPath = Get-Command $NombreExe -ErrorAction SilentlyContinue
    if ($enPath) { return $enPath.Source }

    foreach ($base in @("C:\Program Files\MySQL", "C:\Program Files (x86)\MySQL")) {
        $candidatos = Get-ChildItem -Path $base -Recurse -Filter $NombreExe -ErrorAction SilentlyContinue
        if ($candidatos) { return ($candidatos | Select-Object -First 1).FullName }
    }

    return $null
}

function Find-MySqlClientExe { Find-MySqlExecutable -NombreExe "mysql.exe" }
function Find-MySqlDumpExe { Find-MySqlExecutable -NombreExe "mysqldump.exe" }

function Get-MySqlService {
    # Si por alguna razon hay mas de un servicio MySQL* (una instalacion
    # vieja sin desinstalar, por ejemplo), se prefiere el que esta corriendo
    # en vez del primero que devuelva Get-Service en cualquier orden.
    Get-Service -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "MySQL*" } |
        Sort-Object -Property @{ Expression = { $_.Status -eq "Running" }; Descending = $true } |
        Select-Object -First 1
}
