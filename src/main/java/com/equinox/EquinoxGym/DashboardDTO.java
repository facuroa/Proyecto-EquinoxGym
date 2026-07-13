package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashboardDTO {

    private long totalSocios;
    private long sociosActivos;
    private long sociosMorosos;
    private long sociosInactivos;
    private long cuotasVencidas;
    private long cuotasPagadasMes;
    private BigDecimal recaudadoMes;
    private BigDecimal recaudadoHoy;
    private long pagosHoy;
    private long vencenProximos;
    private List<Socio> cumpleaniosHoy = new ArrayList<>();
    private List<Socio> cumpleaniosProximos = new ArrayList<>();
    private List<Cuota> cuotasVencidasPrioritarias = new ArrayList<>();
    private List<Cuota> cuotasProximas = new ArrayList<>();
    private List<Pago> ultimosPagosHoy = new ArrayList<>();

    public long getTotalSocios() {
        return totalSocios;
    }

    public void setTotalSocios(long totalSocios) {
        this.totalSocios = totalSocios;
    }

    public long getSociosActivos() {
        return sociosActivos;
    }

    public void setSociosActivos(long sociosActivos) {
        this.sociosActivos = sociosActivos;
    }

    public long getSociosMorosos() {
        return sociosMorosos;
    }

    public void setSociosMorosos(long sociosMorosos) {
        this.sociosMorosos = sociosMorosos;
    }

    public long getSociosInactivos() {
        return sociosInactivos;
    }

    public void setSociosInactivos(long sociosInactivos) {
        this.sociosInactivos = sociosInactivos;
    }

    public long getCuotasVencidas() {
        return cuotasVencidas;
    }

    public void setCuotasVencidas(long cuotasVencidas) {
        this.cuotasVencidas = cuotasVencidas;
    }

    public long getCuotasPagadasMes() {
        return cuotasPagadasMes;
    }

    public void setCuotasPagadasMes(long cuotasPagadasMes) {
        this.cuotasPagadasMes = cuotasPagadasMes;
    }

    public BigDecimal getRecaudadoMes() {
        return recaudadoMes;
    }

    public void setRecaudadoMes(BigDecimal recaudadoMes) {
        this.recaudadoMes = recaudadoMes;
    }

    public List<Socio> getCumpleaniosHoy() {
        return cumpleaniosHoy;
    }

    public void setCumpleaniosHoy(List<Socio> cumpleaniosHoy) {
        this.cumpleaniosHoy = cumpleaniosHoy;
    }

    public BigDecimal getRecaudadoHoy() { return recaudadoHoy; }
    public void setRecaudadoHoy(BigDecimal recaudadoHoy) { this.recaudadoHoy = recaudadoHoy; }
    public long getPagosHoy() { return pagosHoy; }
    public void setPagosHoy(long pagosHoy) { this.pagosHoy = pagosHoy; }
    public long getVencenProximos() { return vencenProximos; }
    public void setVencenProximos(long vencenProximos) { this.vencenProximos = vencenProximos; }
    public List<Socio> getCumpleaniosProximos() { return cumpleaniosProximos; }
    public void setCumpleaniosProximos(List<Socio> cumpleaniosProximos) { this.cumpleaniosProximos = cumpleaniosProximos; }
    public List<Cuota> getCuotasVencidasPrioritarias() { return cuotasVencidasPrioritarias; }
    public void setCuotasVencidasPrioritarias(List<Cuota> cuotasVencidasPrioritarias) { this.cuotasVencidasPrioritarias = cuotasVencidasPrioritarias; }
    public List<Cuota> getCuotasProximas() { return cuotasProximas; }
    public void setCuotasProximas(List<Cuota> cuotasProximas) { this.cuotasProximas = cuotasProximas; }
    public List<Pago> getUltimosPagosHoy() { return ultimosPagosHoy; }
    public void setUltimosPagosHoy(List<Pago> ultimosPagosHoy) { this.ultimosPagosHoy = ultimosPagosHoy; }
}
