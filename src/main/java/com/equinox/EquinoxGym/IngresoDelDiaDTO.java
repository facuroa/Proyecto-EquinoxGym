package com.equinox.EquinoxGym;

/** Una fila de la tabla "Ingresos de hoy". */
public class IngresoDelDiaDTO {

    private final Long id;
    private final String hora;
    private final String franja;
    private final Long socioId;
    private final String nombreCompleto;
    private final String dni;
    private final String estado;
    private final boolean segundoTurno;
    private final String registradoPor;

    public IngresoDelDiaDTO(Long id, String hora, String franja, Long socioId, String nombreCompleto,
                            String dni, String estado, boolean segundoTurno, String registradoPor) {
        this.id = id;
        this.hora = hora;
        this.franja = franja;
        this.socioId = socioId;
        this.nombreCompleto = nombreCompleto;
        this.dni = dni;
        this.estado = estado;
        this.segundoTurno = segundoTurno;
        this.registradoPor = registradoPor;
    }

    public Long getId() { return id; }
    public String getHora() { return hora; }
    public String getFranja() { return franja; }
    public Long getSocioId() { return socioId; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getDni() { return dni; }
    public String getEstado() { return estado; }
    public boolean isSegundoTurno() { return segundoTurno; }
    public String getRegistradoPor() { return registradoPor; }

    public boolean isConDeuda() {
        return "MOROSO".equals(estado) || "INACTIVO".equals(estado);
    }
}
