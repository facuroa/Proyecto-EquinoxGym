package com.equinox.EquinoxGym;

import java.time.LocalDate;
import java.util.List;

/** Ficha de asistencia de un socio: ultima visita, conteos y ultimas semanas. */
public class ResumenAsistenciaSocioDTO {

    private final Long socioId;
    private final String nombreCompleto;
    private final String dni;
    private final String telefono;
    private final LocalDate ultimaVisita;
    private final Long diasSinVenir;
    private final long ingresosEsteMes;
    private final long ingresosMesPasado;
    private final List<Semana> semanas;
    private final String whatsappUrl;

    public ResumenAsistenciaSocioDTO(Long socioId, String nombreCompleto, String dni, String telefono,
                                     LocalDate ultimaVisita, Long diasSinVenir,
                                     long ingresosEsteMes, long ingresosMesPasado,
                                     List<Semana> semanas, String whatsappUrl) {
        this.socioId = socioId;
        this.nombreCompleto = nombreCompleto;
        this.dni = dni;
        this.telefono = telefono;
        this.ultimaVisita = ultimaVisita;
        this.diasSinVenir = diasSinVenir;
        this.ingresosEsteMes = ingresosEsteMes;
        this.ingresosMesPasado = ingresosMesPasado;
        this.semanas = semanas;
        this.whatsappUrl = whatsappUrl;
    }

    public Long getSocioId() { return socioId; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getDni() { return dni; }
    public String getTelefono() { return telefono; }
    /** null cuando el socio nunca registro un ingreso. */
    public LocalDate getUltimaVisita() { return ultimaVisita; }
    public Long getDiasSinVenir() { return diasSinVenir; }
    public long getIngresosEsteMes() { return ingresosEsteMes; }
    public long getIngresosMesPasado() { return ingresosMesPasado; }
    public List<Semana> getSemanas() { return semanas; }
    public String getWhatsappUrl() { return whatsappUrl; }

    /** Una semana de lunes a domingo; cada dia es "vino", "falto" o "futuro". */
    public static class Semana {

        private final LocalDate inicio;
        private final List<String> dias;

        public Semana(LocalDate inicio, List<String> dias) {
            this.inicio = inicio;
            this.dias = dias;
        }

        public LocalDate getInicio() { return inicio; }
        public List<String> getDias() { return dias; }
    }
}
