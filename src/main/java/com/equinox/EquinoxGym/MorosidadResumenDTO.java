package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MorosidadResumenDTO {

    private List<GestionMorosidadDTO> gestiones = new ArrayList<>();
    private long sociosMorosos;
    private BigDecimal deudaVencida = BigDecimal.ZERO;
    private long vencenHoy;
    private long vencenProximos;

    public List<GestionMorosidadDTO> getGestiones() { return gestiones; }
    public void setGestiones(List<GestionMorosidadDTO> gestiones) { this.gestiones = gestiones; }

    public long getSociosMorosos() { return sociosMorosos; }
    public void setSociosMorosos(long sociosMorosos) { this.sociosMorosos = sociosMorosos; }

    public BigDecimal getDeudaVencida() { return deudaVencida; }
    public void setDeudaVencida(BigDecimal deudaVencida) { this.deudaVencida = deudaVencida; }

    public long getVencenHoy() { return vencenHoy; }
    public void setVencenHoy(long vencenHoy) { this.vencenHoy = vencenHoy; }

    public long getVencenProximos() { return vencenProximos; }
    public void setVencenProximos(long vencenProximos) { this.vencenProximos = vencenProximos; }
}

