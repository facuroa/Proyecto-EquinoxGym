; Instalador profesional de EquinoxGym para Windows.
; Se compila con: deploy\windows\installer\build.ps1 (usa ISCC.exe de Inno Setup 6).
;
; Que hace este instalador, en orden:
;   1. Pide la contraseña de MySQL (root) y los datos del administrador inicial.
;   2. Verifica MySQL; si no esta instalado, lo instala en modo silencioso.
;   3. Copia la app, el runtime de Java (jlink) y registra el auto-inicio.
;   4. Crea la base de datos, el usuario dedicado de la app y el administrador.
;   5. Crea accesos directos y, opcionalmente, abre el sistema en el navegador.

#define MyAppName "EquinoxGym"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "EquinoxGym"

[Setup]
AppId={{6C1B5B2A-9F1E-4E7B-9E52-EQUINOXGYM01}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=Output
OutputBaseFilename=EquinoxGym-Setup
SetupIconFile=assets\icono.ico
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
WizardStyle=modern
UninstallDisplayIcon={app}\installer\assets\icono.ico

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Files]
Source: "..\app\EquinoxGym.jar"; DestDir: "{app}\app"; Flags: ignoreversion
Source: "..\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\scripts\*.ps1"; DestDir: "{app}\scripts"; Flags: ignoreversion
Source: "..\instalar.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\crear-base-local.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\iniciar-equinox.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\config\*.example"; DestDir: "{app}\config"; Flags: ignoreversion
Source: "assets\abrir-equinox.vbs"; DestDir: "{app}\installer\assets"; Flags: ignoreversion
Source: "assets\icono.ico"; DestDir: "{app}\installer\assets"; Flags: ignoreversion
Source: "bin\mysql-installer-community-*.msi"; DestDir: "{app}\installer\bin"; Flags: ignoreversion; Check: ShouldCopyMySqlInstaller

[Run]
Filename: "wscript.exe"; Parameters: """{app}\installer\assets\abrir-equinox.vbs"""; \
    Description: "Iniciar EquinoxGym ahora"; Flags: postinstall skipifsilent nowait

[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -WindowStyle Hidden -Command ""Stop-ScheduledTask -TaskName 'EquinoxGym' -ErrorAction SilentlyContinue; Stop-ScheduledTask -TaskName 'EquinoxGymBackup' -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName 'EquinoxGym' -Confirm:$false -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName 'EquinoxGymBackup' -Confirm:$false -ErrorAction SilentlyContinue"""; \
    Flags: runhidden waituntilterminated

[UninstallDelete]
Type: files; Name: "{commondesktop}\EquinoxGym.lnk"
Type: filesandordirs; Name: "{commonprograms}\EquinoxGym"

; Nota: el desinstalador NO borra C:\ProgramData\EquinoxGym (config, base de
; datos, backups, logs) a proposito, para no perder datos del gimnasio por
; accidente. Si hay que borrar todo, se hace a mano.

[Code]
var
  MySQLPage: TInputQueryWizardPage;
  AdminPage: TInputQueryWizardPage;
  MySQLDetected: Boolean;
  ProgressPage: TOutputProgressWizardPage;

function ShouldCopyMySqlInstaller: Boolean;
begin
  Result := not MySQLDetected;
end;

function DetectMySQL(): Boolean;
var
  ResultCode: Integer;
  Ok: Boolean;
begin
  Ok := Exec('powershell.exe',
    '-NoProfile -Command "$s = Get-Service | Where-Object { $_.Name -like ''MySQL*'' }; if ($s) { exit 1 } else { exit 0 }"',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  if not Ok then
    // No se pudo ni lanzar powershell.exe para chequear: en vez de asumir
    // "no hay MySQL" (lo que llevaria a instalar otro Server encima de uno
    // que quizas si existe), asumimos que si hay. En el peor caso el usuario
    // reintenta con la contraseña correcta; en el mejor de los casos evitamos
    // el choque de instalar MySQL Server dos veces.
    Result := True
  else
    Result := (ResultCode = 1);
end;

function EscapeArg(const S: String): String;
begin
  Result := S;
  StringChangeEx(Result, '"', '\"', True);
end;

function GenerateRandomPassword(PwLen: Integer): String;
var
  Chars: String;
  I: Integer;
begin
  Chars := 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
  Result := '';
  for I := 1 to PwLen do
    Result := Result + Chars[Random(Length(Chars)) + 1];
end;

procedure InitializeWizard;
begin
  MySQLDetected := DetectMySQL();

  MySQLPage := CreateInputQueryPage(wpSelectDir,
    'Configuracion de MySQL', 'Contraseña de MySQL',
    'EquinoxGym necesita esta contraseña para crear su propia base de datos.');
  if MySQLDetected then
    MySQLPage.Add('Ya hay MySQL instalado en esta PC. Ingresa la contraseña actual del usuario root:', True)
  else
    MySQLPage.Add('No se detecto MySQL en esta PC. Elegi una contraseña para el usuario root (se va a instalar MySQL Server automaticamente):', True);

  AdminPage := CreateInputQueryPage(MySQLPage.ID,
    'Administrador del sistema', 'Datos del administrador inicial',
    'Con estos datos vas a iniciar sesion la primera vez que entres al sistema.');
  AdminPage.Add('Nombre del gimnasio:', False);
  AdminPage.Add('Usuario administrador:', False);
  AdminPage.Add('Contraseña del administrador:', True);
  AdminPage.Add('Confirmar contraseña:', True);
  AdminPage.Values[0] := 'Mi Gimnasio';
  AdminPage.Values[1] := 'admin';
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;

  if CurPageID = MySQLPage.ID then
  begin
    if Length(MySQLPage.Values[0]) < 4 then
    begin
      MsgBox('Ingresa una contraseña de MySQL de al menos 4 caracteres.', mbError, MB_OK);
      Result := False;
    end;
  end
  else if CurPageID = AdminPage.ID then
  begin
    if Trim(AdminPage.Values[0]) = '' then
    begin
      MsgBox('Ingresa el nombre del gimnasio.', mbError, MB_OK);
      Result := False;
    end
    else if Trim(AdminPage.Values[1]) = '' then
    begin
      MsgBox('Ingresa el usuario administrador.', mbError, MB_OK);
      Result := False;
    end
    else if Length(AdminPage.Values[2]) < 12 then
    begin
      MsgBox('La contraseña del administrador debe tener al menos 12 caracteres.', mbError, MB_OK);
      Result := False;
    end
    else if AdminPage.Values[2] <> AdminPage.Values[3] then
    begin
      MsgBox('Las contraseñas no coinciden.', mbError, MB_OK);
      Result := False;
    end;
  end;
end;

function ReadLastError(): String;
var
  ErrorTextAnsi: AnsiString;
  ErrorText: String;
  ErrorFile: String;
  Leido: Boolean;
begin
  ErrorFile := ExpandConstant('{commonappdata}\EquinoxGym\last-error.txt');
  ErrorText := '';
  if FileExists(ErrorFile) then
  begin
    Leido := LoadStringFromFile(ErrorFile, ErrorTextAnsi);
    ErrorText := String(ErrorTextAnsi);
  end;
  if Trim(ErrorText) = '' then
    ErrorText := 'Ocurrio un error inesperado. Revisa el log en C:\ProgramData\EquinoxGym\install.log.';
  Result := ErrorText;
end;

procedure RunStepOrAbort(const StatusText, Exe, Params: String);
var
  ResultCode: Integer;
  Ok: Boolean;
begin
  ProgressPage.SetText(StatusText, '');
  Ok := Exec(Exe, Params, '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  if (not Ok) or (ResultCode <> 0) then
  begin
    ProgressPage.Hide;
    MsgBox(ReadLastError(), mbCriticalError, MB_OK);
    Abort;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ps: String;
  appDir: String;
  mysqlMsiPath: String;
  dbAppPassword: String;
  msiFiles: TFindRec;
  ResultCode: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    appDir := ExpandConstant('{app}');
    dbAppPassword := GenerateRandomPassword(20);

    mysqlMsiPath := '';
    if FindFirst(appDir + '\installer\bin\mysql-installer-community-*.msi', msiFiles) then
    begin
      mysqlMsiPath := appDir + '\installer\bin\' + msiFiles.Name;
      FindClose(msiFiles);
    end;

    ProgressPage := CreateOutputProgressPage('Configurando EquinoxGym', 'Esto puede tardar varios minutos, no cierres esta ventana...');
    ProgressPage.Show;
    try
      ps := Format('-NoProfile -ExecutionPolicy Bypass -File "%s\scripts\verificar-instalar-mysql.ps1" -RootPassword "%s" -MysqlInstallerMsiPath "%s"', [appDir, EscapeArg(MySQLPage.Values[0]), EscapeArg(mysqlMsiPath)]);
      RunStepOrAbort('Verificando e instalando MySQL...', 'powershell.exe', ps);

      ps := Format('-NoProfile -ExecutionPolicy Bypass -File "%s\instalar.ps1" -Silent', [appDir]);
      RunStepOrAbort('Instalando archivos y accesos directos...', 'powershell.exe', ps);

      ps := Format('-NoProfile -ExecutionPolicy Bypass -File "%s\crear-base-local.ps1" -NonInteractive -RootPassword "%s" -DbPassword "%s" -AdminUsername "%s" -AdminPassword "%s" -GymName "%s"', [appDir, EscapeArg(MySQLPage.Values[0]), EscapeArg(dbAppPassword), EscapeArg(AdminPage.Values[1]), EscapeArg(AdminPage.Values[2]), EscapeArg(AdminPage.Values[0])]);
      RunStepOrAbort('Creando la base de datos y el administrador...', 'powershell.exe', ps);

      ProgressPage.SetText('Iniciando EquinoxGym...', '');
      Exec('powershell.exe', '-NoProfile -WindowStyle Hidden -Command "Start-ScheduledTask -TaskName ''EquinoxGym''"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

      // El instalador offline de MySQL ya no hace falta una vez usado: libera ~566 MB.
      if mysqlMsiPath <> '' then
        DeleteFile(mysqlMsiPath);
    finally
      ProgressPage.Hide;
    end;
  end;
end;
