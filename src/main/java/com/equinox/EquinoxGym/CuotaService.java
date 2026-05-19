package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class CuotaService {

    public void actualizarEstadoCuota(Cuota cuota) {
        if (cuota == null || cuota.getFechaVencimiento() == null) {
            return;
        }

        LocalDate hoy = LocalDate.now();

        if (cuota.getFechaPago() != null) {
            cuota.setEstado(EstadoCuota.PAGADA);
        } else if (!hoy.isAfter(cuota.getFechaVencimiento())) {
            cuota.setEstado(EstadoCuota.PENDIENTE);
        } else {
            cuota.setEstado(EstadoCuota.VENCIDA);
        }
    }
}