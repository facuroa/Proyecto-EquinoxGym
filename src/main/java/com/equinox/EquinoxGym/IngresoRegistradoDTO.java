package com.equinox.EquinoxGym;

import java.io.Serializable;

/**
 * Lo que el mostrador ve despues de registrar un ingreso: quien es el socio,
 * como esta su cuota y que paso con el registro. Viaja como atributo flash
 * entre el POST y la pantalla, por eso solo lleva datos simples.
 */
public class IngresoRegistradoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private ResultadoIngreso resultado;
    private Long socioId;
    private String nombreCompleto;
    private String dni;
    private String estado;
    private String textoCuota;
    private String hora;
    private String horaIngresoAnterior;
    private boolean segundoTurno;
    private int maximoPorDia;

    public ResultadoIngreso getResultado() { return resultado; }
    public void setResultado(ResultadoIngreso resultado) { this.resultado = resultado; }

    public Long getSocioId() { return socioId; }
    public void setSocioId(Long socioId) { this.socioId = socioId; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getTextoCuota() { return textoCuota; }
    public void setTextoCuota(String textoCuota) { this.textoCuota = textoCuota; }

    public String getHora() { return hora; }
    public void setHora(String hora) { this.hora = hora; }

    public String getHoraIngresoAnterior() { return horaIngresoAnterior; }
    public void setHoraIngresoAnterior(String horaIngresoAnterior) { this.horaIngresoAnterior = horaIngresoAnterior; }

    public boolean isSegundoTurno() { return segundoTurno; }
    public void setSegundoTurno(boolean segundoTurno) { this.segundoTurno = segundoTurno; }

    public int getMaximoPorDia() { return maximoPorDia; }
    public void setMaximoPorDia(int maximoPorDia) { this.maximoPorDia = maximoPorDia; }

    public boolean isRegistrado() {
        return resultado == ResultadoIngreso.REGISTRADO;
    }

    /** El socio entra igual, pero el mostrador tiene que saber que debe. */
    public boolean isConDeuda() {
        return "MOROSO".equals(estado) || "INACTIVO".equals(estado);
    }

    /** Variante visual del panel: ok, aviso o repetido. */
    public String getTono() {
        if (!isRegistrado()) {
            return "repetido";
        }
        return isConDeuda() ? "aviso" : "ok";
    }
}
