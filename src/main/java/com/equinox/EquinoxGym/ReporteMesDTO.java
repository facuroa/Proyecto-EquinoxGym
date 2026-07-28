package com.equinox.EquinoxGym;

import java.math.BigDecimal;

public class ReporteMesDTO {

    private final String etiqueta;
    private final BigDecimal ingresos;
    private final long altas;
    private final long renovaciones;
    private int porcentajeAltura;

    public ReporteMesDTO(String etiqueta, BigDecimal ingresos, long altas, long renovaciones) {
        this.etiqueta = etiqueta;
        this.ingresos = ingresos;
        this.altas = altas;
        this.renovaciones = renovaciones;
    }

    public String getEtiqueta() { return etiqueta; }
    public BigDecimal getIngresos() { return ingresos; }
    public long getAltas() { return altas; }
    public long getRenovaciones() { return renovaciones; }
    public int getPorcentajeAltura() { return porcentajeAltura; }
    public void setPorcentajeAltura(int porcentajeAltura) { this.porcentajeAltura = porcentajeAltura; }
}
