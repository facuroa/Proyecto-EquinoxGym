package com.equinox.EquinoxGym;

public enum OrigenMovimientoCaja {
    PAGO_EFECTIVO("Cobro en efectivo", true),
    PAGO_TRANSFERENCIA("Cobro por transferencia", false),
    PAGO_TARJETA("Cobro con tarjeta", false),
    PAGO_OTRO("Cobro por otro medio", false),
    INGRESO_MANUAL("Ingreso manual", true),
    EGRESO_MANUAL("Egreso manual", true),
    ANULACION_PAGO("Anulación en efectivo", true),
    ANULACION_TRANSFERENCIA("Anulación de transferencia", false),
    ANULACION_TARJETA("Anulación de tarjeta", false),
    ANULACION_OTRO("Anulación de otro medio", false);

    private final String descripcion;
    private final boolean afectaEfectivo;

    OrigenMovimientoCaja(String descripcion, boolean afectaEfectivo) {
        this.descripcion = descripcion;
        this.afectaEfectivo = afectaEfectivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isAfectaEfectivo() {
        return afectaEfectivo;
    }

    public boolean isTransferencia() {
        return this == PAGO_TRANSFERENCIA || this == ANULACION_TRANSFERENCIA;
    }

    public boolean isTarjeta() {
        return this == PAGO_TARJETA || this == ANULACION_TARJETA;
    }

    public boolean isOtroMedio() {
        return this == PAGO_OTRO || this == ANULACION_OTRO;
    }
}
