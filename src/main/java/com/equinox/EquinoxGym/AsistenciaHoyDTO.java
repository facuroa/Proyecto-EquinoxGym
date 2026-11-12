package com.equinox.EquinoxGym;

import java.util.List;

/** Todo lo que muestra la solapa "Hoy" de Asistencia. */
public class AsistenciaHoyDTO {

    private final List<IngresoDelDiaDTO> ingresos;
    private final List<FranjaHorariaDTO> franjas;
    private final long sociosConDeuda;

    public AsistenciaHoyDTO(List<IngresoDelDiaDTO> ingresos, List<FranjaHorariaDTO> franjas, long sociosConDeuda) {
        this.ingresos = ingresos;
        this.franjas = franjas;
        this.sociosConDeuda = sociosConDeuda;
    }

    public List<IngresoDelDiaDTO> getIngresos() { return ingresos; }
    public List<FranjaHorariaDTO> getFranjas() { return franjas; }
    public long getSociosConDeuda() { return sociosConDeuda; }

    public int getTotal() {
        return ingresos.size();
    }
}
