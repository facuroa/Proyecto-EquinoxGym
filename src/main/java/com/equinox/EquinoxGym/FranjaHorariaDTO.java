package com.equinox.EquinoxGym;

/** Cantidad de ingresos de una franja horaria, con el ancho de su barra. */
public class FranjaHorariaDTO {

    private final String hora;
    private final long cantidad;
    private final int porcentaje;
    private final boolean pico;

    public FranjaHorariaDTO(String hora, long cantidad, int porcentaje, boolean pico) {
        this.hora = hora;
        this.cantidad = cantidad;
        this.porcentaje = porcentaje;
        this.pico = pico;
    }

    public String getHora() { return hora; }
    public long getCantidad() { return cantidad; }
    public int getPorcentaje() { return porcentaje; }
    public boolean isPico() { return pico; }
}
