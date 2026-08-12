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
    private String gymName;

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

    public String getGymName() { return gymName; }
    public void setGymName(String gymName) { this.gymName = gymName; }

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
        if (socio == null) {
            return null;
        }
        return WhatsAppLinkBuilder.construirUrl(socio.getTelefono(), getMensajeRecordatorio());
    }

    public String getMensajeRecordatorio() {
        if (socio == null) {
            return null;
        }
        String marca = (gymName == null || gymName.isBlank()) ? "el gimnasio" : gymName;
        String nombre = (socio.getNombre() == null || socio.getNombre().isBlank()) ? "" : (" " + socio.getNombre());
        String situacion = diasAtraso > 0
                ? "tenés una cuota vencida hace " + diasAtraso + (diasAtraso == 1 ? " día" : " días")
                : "tu cuota está por vencer";
        String vencimiento = vencimientoMasAntiguo == null ? "" : " (vencimiento " + vencimientoMasAntiguo + ")";
        return "Hola" + nombre + ", te escribimos de " + marca + ". Te recordamos que " + situacion
                + vencimiento + ", por un total de $ " + saldoPendiente
                + ". ¡Te esperamos para ponerte al día!";
    }
}
