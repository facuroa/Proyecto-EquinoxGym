package com.equinox.EquinoxGym;

import java.time.LocalDate;

/** Socio con la cuota al dia que dejo de venir. */
public class SocioAusenteDTO {

    private final Long socioId;
    private final String nombreCompleto;
    private final String dni;
    private final LocalDate ultimaVisita;
    private final long diasSinVenir;
    private final LocalDate vencimientoPlan;

    public SocioAusenteDTO(Long socioId, String nombreCompleto, String dni,
                           LocalDate ultimaVisita, long diasSinVenir, LocalDate vencimientoPlan) {
        this.socioId = socioId;
        this.nombreCompleto = nombreCompleto;
        this.dni = dni;
        this.ultimaVisita = ultimaVisita;
        this.diasSinVenir = diasSinVenir;
        this.vencimientoPlan = vencimientoPlan;
    }

    public Long getSocioId() { return socioId; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getDni() { return dni; }
    /** null cuando el socio nunca registro un ingreso. */
    public LocalDate getUltimaVisita() { return ultimaVisita; }
    public long getDiasSinVenir() { return diasSinVenir; }
    public LocalDate getVencimientoPlan() { return vencimientoPlan; }
}
