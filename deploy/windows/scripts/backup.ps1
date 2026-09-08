$ErrorActionPreference = "Stop"
. "$PSScriptRoot\logging.ps1"

$ConfigDir = "C:\ProgramData\EquinoxGym\config"
$BackupDir = "C:\ProgramData\EquinoxGym\backups"
$EnvFile = Join-Path $ConfigDir "backup.env"
$DefaultsFile = Join-Path $ConfigDir "backup.cnf"

$dbName = "equinoxgym"
$retentionDays = 30

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $linea = $_.Trim()
        if ($linea -and -not $linea.StartsWith("#") -and $linea.Contains("=")) {
            $partes = $linea.Split("=", 2)
            switch ($partes[0].Trim()) {
                "DB_NAME" { $dbName = $partes[1].Trim() }
                "RETENTION_DAYS" { $retentionDays = [int]$partes[1].Trim() }
            }
        }
    }
}

$mysqldumpExe = Find-MySqlDumpExe
if (-not $mysqldumpExe) {
    Exit-WithError "No se encontro 'mysqldump.exe'. No se pudo generar el backup."
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$sqlFile = Join-Path $BackupDir "$dbName-$timestamp.sql"
$zipFile = "$sqlFile.zip"

# --result-file hace que mysqldump escriba el archivo directamente: evita que
# PowerShell reinterprete la salida como texto y corrompa acentos (nombres, etc).
& $mysqldumpExe "--defaults-extra-file=$DefaultsFile" --single-transaction --routines --events --triggers `
    "--result-file=$sqlFile" $dbName

if ($LASTEXITCODE -ne 0) {
    Exit-WithError "mysqldump fallo (codigo $LASTEXITCODE). Revisa $DefaultsFile."
}

Compress-Archive -Path $sqlFile -DestinationPath $zipFile -Force
Remove-Item $sqlFile

$hash = Get-FileHash -Path $zipFile -Algorithm SHA256
"$($hash.Hash)  $(Split-Path $zipFile -Leaf)" | Set-Content -Path "$zipFile.sha256" -Encoding ASCII

Get-ChildItem -Path $BackupDir -Filter "*.zip*" | Where-Object {
    $_.LastWriteTime -lt (Get-Date).AddDays(-$retentionDays)
} | Remove-Item -Force

Write-Log "Backup generado: $zipFile"
