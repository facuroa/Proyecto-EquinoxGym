package com.equinox.EquinoxGym;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Crea en la base el indice que impide dos socios con el mismo DNI.
 *
 * <p>Hibernate ({@code ddl-auto=update}) solo arma los indices unicos cuando
 * crea una tabla nueva: en las instalaciones que ya tenian la tabla de socios
 * el {@code @Column(unique = true)} no llegaba nunca a la base. Se intenta al
 * arrancar y cada vez que un administrador elimina un socio, porque mientras
 * haya DNI repetidos MySQL no lo puede crear: en ese caso solo queda anotado
 * en el registro y el sistema sigue funcionando (las altas igual se controlan
 * de a una, ver {@link ReservaDni}).
 */
@Component
public class IndiceDniUnico {

    private static final Logger log = LoggerFactory.getLogger(IndiceDniUnico.class);
    static final String NOMBRE = "uk_socio_dni";

    private final DataSource dataSource;
    private final SocioRepository socioRepository;
    private volatile boolean creado;

    public IndiceDniUnico(DataSource dataSource, SocioRepository socioRepository) {
        this.dataSource = dataSource;
        this.socioRepository = socioRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        asegurar();
    }

    /** @return true si al terminar la base ya no acepta DNI repetidos */
    public synchronized boolean asegurar() {
        if (creado) {
            return true;
        }
        try (Connection conexion = dataSource.getConnection()) {
            if (existe(conexion)) {
                creado = true;
                return true;
            }
            List<String> repetidos = socioRepository.dnisRepetidos();
            if (!repetidos.isEmpty()) {
                log.warn("Hay socios con el mismo DNI ({}). Un administrador puede eliminar los repetidos desde "
                        + "la ficha del socio; despues de eso se activa el control de DNI en la base.",
                        String.join(", ", repetidos));
                return false;
            }
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute("ALTER TABLE socio ADD CONSTRAINT " + NOMBRE + " UNIQUE (dni)");
            }
            log.info("Control de DNI repetido activado en la base (indice {}).", NOMBRE);
            creado = true;
            return true;
        } catch (SQLException | RuntimeException e) {
            log.warn("No se pudo activar el control de DNI repetido en la base: {}", e.getMessage());
            return false;
        }
    }

    private boolean existe(Connection conexion) throws SQLException {
        DatabaseMetaData datos = conexion.getMetaData();
        for (String tabla : new String[] {"socio", "SOCIO"}) {
            try (ResultSet indices = datos.getIndexInfo(conexion.getCatalog(), conexion.getSchema(), tabla, true, false)) {
                while (indices.next()) {
                    if ("dni".equalsIgnoreCase(indices.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
