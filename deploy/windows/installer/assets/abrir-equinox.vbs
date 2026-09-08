' Acceso directo de EquinoxGym: si el sistema todavia no respondio (recien
' prendio la PC), espera un poco a que el puerto 8085 conteste y recien ahi
' abre el navegador. No muestra ninguna consola.
Option Explicit

Dim shell, http, intentos, maxIntentos, listo
Set shell = CreateObject("WScript.Shell")

' Por si la tarea programada todavia no arranco (por ejemplo, justo despues
' de instalar), la disparamos: si ya esta corriendo, esto no hace nada.
shell.Run "powershell.exe -NoProfile -WindowStyle Hidden -Command ""Start-ScheduledTask -TaskName 'EquinoxGym' -ErrorAction SilentlyContinue""", 0, True

maxIntentos = 60
intentos = 0
listo = False

Do While intentos < maxIntentos And Not listo
    On Error Resume Next
    Set http = CreateObject("MSXML2.ServerXMLHTTP.6.0")
    http.setTimeouts 1000, 1000, 1000, 1000
    http.Open "GET", "http://localhost:8085/login", False
    http.Send()
    If Err.Number = 0 And http.Status > 0 Then
        listo = True
    End If
    Err.Clear
    On Error Goto 0
    Set http = Nothing

    If Not listo Then
        WScript.Sleep 1000
        intentos = intentos + 1
    End If
Loop

shell.Run "http://localhost:8085", 1, False
