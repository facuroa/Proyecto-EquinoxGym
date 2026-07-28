package com.equinox.EquinoxGym;

import java.math.BigDecimal;

public class ReporteMedioPagoDTO {

    private final String medioPago;
    private final BigDecimal total;
    private final long cantidad;
    private final int porcentaje;

    public ReporteMedioPagoDTO(String medioPago, BigDecimal total, long cantidad, int porcentaje) {
        this.medioPago = medioPago;
        this.total = total;
        this.cantidad = cantidad;
        this.porcentaje = porcentaje;
    }

    public String getMedioPago() { return medioPago; }
    public BigDecimal getTotal() { return total; }
    public long getCantidad() { return cantidad; }
    public int getPorcentaje() { return porcentaje; }
}
