package com.equinox.EquinoxGym;

import java.math.BigDecimal;

public class DashboardDTO {

    private long totalSocios;
    private long sociosActivos;
    private long sociosMorosos;
    private long sociosInactivos;
    private long cuotasVencidas;
    private long cuotasPagadasMes;
    private BigDecimal recaudadoMes;

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
}