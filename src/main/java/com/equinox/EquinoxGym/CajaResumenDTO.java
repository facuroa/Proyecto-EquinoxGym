package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CajaResumenDTO {

    private CajaSesion cajaActual;
    private List<MovimientoCaja> movimientos = new ArrayList<>();
    private List<CajaSesion> historial = new ArrayList<>();
    private BigDecimal cobrosEfectivo = BigDecimal.ZERO;
    private BigDecimal ingresosManuales = BigDecimal.ZERO;
    private BigDecimal egresos = BigDecimal.ZERO;
    private BigDecimal transferencias = BigDecimal.ZERO;
    private BigDecimal tarjetas = BigDecimal.ZERO;
    private BigDecimal otrosMedios = BigDecimal.ZERO;
    private BigDecimal saldoEsperado = BigDecimal.ZERO;
    private boolean administrador;

    public CajaSesion getCajaActual() { return cajaActual; }
    public void setCajaActual(CajaSesion cajaActual) { this.cajaActual = cajaActual; }
    public List<MovimientoCaja> getMovimientos() { return movimientos; }
    public void setMovimientos(List<MovimientoCaja> movimientos) { this.movimientos = movimientos; }
    public List<CajaSesion> getHistorial() { return historial; }
    public void setHistorial(List<CajaSesion> historial) { this.historial = historial; }
    public BigDecimal getCobrosEfectivo() { return cobrosEfectivo; }
    public void setCobrosEfectivo(BigDecimal cobrosEfectivo) { this.cobrosEfectivo = cobrosEfectivo; }
    public BigDecimal getIngresosManuales() { return ingresosManuales; }
    public void setIngresosManuales(BigDecimal ingresosManuales) { this.ingresosManuales = ingresosManuales; }
    public BigDecimal getEgresos() { return egresos; }
    public void setEgresos(BigDecimal egresos) { this.egresos = egresos; }
    public BigDecimal getTransferencias() { return transferencias; }
    public void setTransferencias(BigDecimal transferencias) { this.transferencias = transferencias; }
    public BigDecimal getTarjetas() { return tarjetas; }
    public void setTarjetas(BigDecimal tarjetas) { this.tarjetas = tarjetas; }
    public BigDecimal getOtrosMedios() { return otrosMedios; }
    public void setOtrosMedios(BigDecimal otrosMedios) { this.otrosMedios = otrosMedios; }
    public BigDecimal getSaldoEsperado() { return saldoEsperado; }
    public void setSaldoEsperado(BigDecimal saldoEsperado) { this.saldoEsperado = saldoEsperado; }
    public boolean isAdministrador() { return administrador; }
    public void setAdministrador(boolean administrador) { this.administrador = administrador; }
    public boolean isCajaAbierta() { return cajaActual != null && cajaActual.getEstado() == EstadoCaja.ABIERTA; }
}
