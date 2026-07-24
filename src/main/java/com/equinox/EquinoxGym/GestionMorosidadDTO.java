package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.time.LocalDate;

public class GestionMorosidadDTO {

    private Socio socio;
    private BigDecimal saldoPendiente = BigDecimal.ZERO;
    private LocalDate vencimientoMasAntiguo;
    private long diasAtraso;
    private int cantidadCuotas;
    private String categoria;
    private SeguimientoMorosidad ultimoSeguimiento;

    public Socio getSocio() { return socio; }
    public void setSocio(Socio socio) { this.socio = socio; }

    public BigDecimal getSaldoPendiente() { return saldoPendiente; }
    public void setSaldoPendiente(BigDecimal saldoPendiente) { this.saldoPendiente = saldoPendiente; }

    public LocalDate getVencimientoMasAntiguo() { return vencimientoMasAntiguo; }
    public void setVencimientoMasAntiguo(LocalDate vencimientoMasAntiguo) { this.vencimientoMasAntiguo = vencimientoMasAntiguo; }

    public long getDiasAtraso() { return diasAtraso; }
    public void setDiasAtraso(long diasAtraso) { this.diasAtraso = diasAtraso; }

    public int getCantidadCuotas() { return cantidadCuotas; }
    public void setCantidadCuotas(int cantidadCuotas) { this.cantidadCuotas = cantidadCuotas; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public SeguimientoMorosidad getUltimoSeguimiento() { return ultimoSeguimiento; }
    public void setUltimoSeguimiento(SeguimientoMorosidad ultimoSeguimiento) { this.ultimoSeguimiento = ultimoSeguimiento; }

    public String getTextoSituacion() {
        if (diasAtraso > 0) {
            return diasAtraso == 1 ? "1 día de atraso" : diasAtraso + " días de atraso";
        }
        if ("HOY".equals(categoria)) {
            return "Vence hoy";
        }
        return "Renovación próxima";
    }

    public int getOrdenPrioridad() {
        return switch (categoria) {
            case "MAS_30" -> 0;
            case "8_30" -> 1;
            case "1_7" -> 2;
            case "HOY" -> 3;
            default -> 4;
        };
    }

    public String getWhatsappUrl() {
        if (socio == null || socio.getTelefono() == null) {
            return null;
        }
        String numero = socio.getTelefono().replaceAll("\\D", "");
        if (numero.startsWith("00")) {
            numero = numero.substring(2);
        }
        if (numero.startsWith("0") && numero.length() == 11) {
            numero = numero.substring(1);
        }
        if (numero.startsWith("549") && numero.length() == 13) {
            return "https://wa.me/" + numero;
        }
        if (numero.startsWith("54") && numero.length() == 12) {
            return "https://wa.me/549" + numero.substring(2);
        }
        if (numero.length() == 10) {
            return "https://wa.me/549" + numero;
        }
        return null;
    }
}
