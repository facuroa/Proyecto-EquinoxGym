package com.equinox.EquinoxGym;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EstadoScheduler {

    private final SocioService socioService;

    public EstadoScheduler(SocioService socioService) {
        this.socioService = socioService;
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void actualizarEstadosDiario() {
        socioService.listarTodosActualizados();
        System.out.println(">>> Estados de socios y cuotas actualizados automáticamente.");
    }
}
