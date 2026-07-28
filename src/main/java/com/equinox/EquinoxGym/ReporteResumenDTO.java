package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReporteResumenDTO {

    private LocalDate desde;
    private LocalDate hasta;
    private BigDecimal ingresos = BigDecimal.ZERO;
    private BigDecimal ticketPromedio = BigDecimal.ZERO;
    private long pagosConfirmados;
    private long altas;
    private long renovaciones;
    private List<ReporteMedioPagoDTO> mediosPago = new ArrayList<>();
    private List<ReporteMesDTO> evolucionMensual = new ArrayList<>();

    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
    public BigDecimal getIngresos() { return ingresos; }
    public void setIngresos(BigDecimal ingresos) { this.ingresos = ingresos; }
    public BigDecimal getTicketPromedio() { return ticketPromedio; }
    public void setTicketPromedio(BigDecimal ticketPromedio) { this.ticketPromedio = ticketPromedio; }
    public long getPagosConfirmados() { return pagosConfirmados; }
    public void setPagosConfirmados(long pagosConfirmados) { this.pagosConfirmados = pagosConfirmados; }
    public long getAltas() { return altas; }
    public void setAltas(long altas) { this.altas = altas; }
    public long getRenovaciones() { return renovaciones; }
    public void setRenovaciones(long renovaciones) { this.renovaciones = renovaciones; }
    public List<ReporteMedioPagoDTO> getMediosPago() { return mediosPago; }
    public void setMediosPago(List<ReporteMedioPagoDTO> mediosPago) { this.mediosPago = mediosPago; }
    public List<ReporteMesDTO> getEvolucionMensual() { return evolucionMensual; }
    public void setEvolucionMensual(List<ReporteMesDTO> evolucionMensual) { this.evolucionMensual = evolucionMensual; }
}
