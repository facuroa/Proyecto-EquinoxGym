<#
Genera el instalador Setup.exe de EquinoxGym para Windows:
  1. Compila el jar de la aplicacion (mvnw).
  2. Genera un runtime de Java privado con jlink (no depende de Java del sistema).
  3. Compila deploy/windows/installer/EquinoxGym.iss con Inno Setup (ISCC.exe).

Requiere: JDK con jlink (Java 17+), Inno Setup 6, y el instalador offline de
MySQL en deploy/windows/installer/bin/mysql-installer-community-*.msi.
#>
param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$InstallerDir = $PSScriptRoot
$DeployWindowsDir = Split-Path $InstallerDir -Parent
$RepoRoot = Split-Path (Split-Path $DeployWindowsDir -Parent) -Parent

$AppTargetDir = Join-Path $DeployWindowsDir "app"
$RuntimeDir = Join-Path $DeployWindowsDir "runtime"

function Write-Step($mensaje) {
    Write-Host ""
    Write-Host "=== $mensaje ===" -ForegroundColor Cyan
}

# --- 1. Compilar el jar ------------------------------------------------
Write-Step "Compilando EquinoxGym.jar"
Push-Location $RepoRoot
try {
    $mvnArgs = @("clean", "package")
    if ($SkipTests) { $mvnArgs += "-DskipTests" }
    & "$RepoRoot\mvnw.cmd" @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "mvnw fallo con codigo $LASTEXITCODE" }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $RepoRoot "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*.original" } |
    Select-Object -First 1
if (-not $jar) { throw "No se encontro el jar compilado en target\." }

New-Item -ItemType Directory -Force -Path $AppTargetDir | Out-Null
Copy-Item -Path $jar.FullName -Destination (Join-Path $AppTargetDir "EquinoxGym.jar") -Force
Write-Host "Jar copiado: $($jar.Name) -> deploy\windows\app\EquinoxGym.jar"

# --- 2. Runtime de Java privado (jlink) --------------------------------
Write-Step "Generando runtime de Java (jlink)"

$jlinkExe = (Get-Command jlink -ErrorAction SilentlyContinue).Source
if (-not $jlinkExe) { throw "No se encontro 'jlink'. Instala un JDK 17+ y agregalo al PATH." }
$javaHome = Split-Path (Split-Path $jlinkExe -Parent) -Parent

if (Test-Path $RuntimeDir) {
    Remove-Item -Path $RuntimeDir -Recurse -Force
}

# Se incluyen todos los modulos del JDK (ALL-MODULE-PATH) en vez de calcular el
# subconjunto minimo: Spring Boot usa reflexion en tiempo de ejecucion (JMX,
# JNDI, seguridad, compresion de Tomcat, etc.) y un jdeps estatico puede no
# detectar todo lo que hace falta. Pesa mas (~150-200 MB) pero es confiable.
& $jlinkExe --module-path "$javaHome\jmods" --add-modules ALL-MODULE-PATH `
    --strip-debug --no-header-files --no-man-pages --compress=2 `
    --output $RuntimeDir
if ($LASTEXITCODE -ne 0) { throw "jlink fallo con codigo $LASTEXITCODE" }
Write-Host "Runtime generado en deploy\windows\runtime\"

# --- 3. Compilar el instalador con Inno Setup ---------------------------
Write-Step "Compilando Setup.exe con Inno Setup"

$mysqlMsi = Get-ChildItem (Join-Path $InstallerDir "bin") -Filter "mysql-installer-community-*.msi" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $mysqlMsi) {
    throw "Falta el instalador offline de MySQL en deploy\windows\installer\bin\. " +
        "Descargalo de https://dev.mysql.com/downloads/installer/ (paquete 'offline', no el 'web')."
}

$iscc = (Get-Command ISCC -ErrorAction SilentlyContinue).Source
if (-not $iscc) {
    $candidatos = @(
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
        "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
        "C:\Program Files\Inno Setup 6\ISCC.exe"
    )
    $iscc = $candidatos | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $iscc) { throw "No se encontro ISCC.exe (Inno Setup 6). Instalalo con: winget install --id JRSoftware.InnoSetup" }

& $iscc (Join-Path $InstallerDir "EquinoxGym.iss")
if ($LASTEXITCODE -ne 0) { throw "ISCC fallo con codigo $LASTEXITCODE" }

Write-Step "Listo"
Write-Host "Setup.exe generado en deploy\windows\installer\Output\" -ForegroundColor Green
