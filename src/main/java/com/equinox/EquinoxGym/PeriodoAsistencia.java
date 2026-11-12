package com.equinox.EquinoxGym;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Periodo sobre el que se promedia el historial de asistencia. El tipo es
 * "1", "4" u "8" (semanas hacia atras desde ayer) o "rango" cuando el
 * usuario eligio las fechas a mano.
 */
public class PeriodoAsistencia {

    public static final String RANGO = "rango";

    private final String tipo;
    private final LocalDate desde;
    private final LocalDate hasta;
    private final String error;

    public PeriodoAsistencia(String tipo, LocalDate desde, LocalDate hasta, String error) {
        this.tipo = tipo;
        this.desde = desde;
        this.hasta = hasta;
        this.error = error;
    }

    public String getTipo() { return tipo; }
    public LocalDate getDesde() { return desde; }
    public LocalDate getHasta() { return hasta; }
    public String getError() { return error; }

    public boolean isRango() {
        return RANGO.equals(tipo);
    }

    public long getCantidadDias() {
        return ChronoUnit.DAYS.between(desde, hasta) + 1;
    }
}
