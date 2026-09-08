$ErrorActionPreference = "Stop"

$AppDir = "C:\ProgramData\EquinoxGym\app"
$EnvFile = "C:\ProgramData\EquinoxGym\config\equinox.env"

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $linea = $_.Trim()
        if ($linea -and -not $linea.StartsWith("#") -and $linea.Contains("=")) {
            $partes = $linea.Split("=", 2)
            [System.Environment]::SetEnvironmentVariable($partes[0].Trim(), $partes[1].Trim(), "Process")
        }
    }
}

$jar = Join-Path $AppDir "EquinoxGym.jar"

# El instalador (Setup.exe) bundlea su propio runtime de Java (jlink) para no
# depender de que haya un Java instalado en el sistema. Si no esta (instalacion
# manual del kit), se usa el "javaw" del PATH como antes.
$javawBundleado = "C:\ProgramData\EquinoxGym\runtime\bin\javaw.exe"
$javaw = if (Test-Path $javawBundleado) { $javawBundleado } else { "javaw" }

# -Wait: si el proceso de Java se cae, este script termina con error y el
# Programador de tareas lo reinicia solo (ver RestartCount en instalar.ps1),
# igual que el "Restart=on-failure" del kit de Linux.
$proceso = Start-Process -FilePath $javaw -ArgumentList "-jar", "`"$jar`"" `
    -WorkingDirectory $AppDir -PassThru -Wait -WindowStyle Hidden

exit $proceso.ExitCode
