package com.equinox.EquinoxGym;

import org.springframework.boot.system.ApplicationHome;

import java.io.File;

/**
 * Elige donde se guarda el archivo de registro (logs) cuando no se indico
 * {@code LOG_FILE}.
 *
 * <p>Una ruta relativa como {@code logs/equinoxgym.log} depende de la carpeta
 * desde la que arranca el proceso: la tarea programada de Windows puede
 * arrancar en {@code C:\Windows\System32}. Por eso se usa una ruta fija:
 * <ol>
 *   <li>En Windows, si existe {@code C:\ProgramData\EquinoxGym} (lo crea el
 *       instalador, ahi estan la configuracion y los backups): su carpeta
 *       {@code logs}.</li>
 *   <li>Si no, la carpeta {@code logs} al lado del jar.</li>
 * </ol>
 * Si la carpeta no se puede escribir el sistema arranca igual, solo que sin
 * archivo de registro.
 */
final class UbicacionRegistro {

    private static final String VARIABLE = "LOG_FILE";
    private static final String ARCHIVO = "equinoxgym.log";

    private UbicacionRegistro() {
    }

    static void configurar() {
        if (System.getenv(VARIABLE) != null || System.getProperty(VARIABLE) != null) {
            return;
        }
        try {
            System.setProperty(VARIABLE, new File(carpeta(), ARCHIVO).getPath());
        } catch (RuntimeException e) {
            // Sin una ubicacion fija queda la de application.properties.
        }
    }

    private static File carpeta() {
        String programData = System.getenv("ProgramData");
        if (programData != null) {
            File instalacion = new File(programData, "EquinoxGym");
            if (instalacion.isDirectory()) {
                return new File(instalacion, "logs");
            }
        }
        return new File(new ApplicationHome(Application.class).getDir(), "logs");
    }
}
