package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RecordatorioVencimientoScheduler {

    private final CuotaRepository cuotaRepository;
    private final SeguimientoMorosidadRepository seguimientoRepository;
    private final NotificacionEmailService notificacionEmailService;
    private final int diasAnticipacion;

    public RecordatorioVencimientoScheduler(CuotaRepository cuotaRepository,
                                            SeguimientoMorosidadRepository seguimientoRepository,
                                            NotificacionEmailService notificacionEmailService,
                                            @Value("${equinox.recordatorios.dias-anticipacion:3}") int diasAnticipacion) {
        this.cuotaRepository = cuotaRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.notificacionEmailService = notificacionEmailService;
        this.diasAnticipacion = diasAnticipacion;
    }

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void enviarRecordatoriosDiarios() {
        LocalDate fechaObjetivo = LocalDate.now().plusDays(diasAnticipacion);
        List<Cuota> cuotasAVencer = cuotaRepository.findByFechaPagoIsNullAndFechaVencimiento(fechaObjetivo);

        for (Cuota cuota : cuotasAVencer) {
            Socio socio = cuota.getSocio();
            if (socio == null || socio.getEmail() == null || socio.getEmail().isBlank()) {
                continue;
            }

            notificacionEmailService.enviarRecordatorioVencimiento(cuota);

            SeguimientoMorosidad seguimiento = new SeguimientoMorosidad();
            seguimiento.setSocio(socio);
            seguimiento.setCanal(CanalSeguimiento.EMAIL);
            seguimiento.setNota("Recordatorio automático de vencimiento (" + fechaObjetivo + ") enviado por email.");
            seguimiento.setFechaRegistro(LocalDateTime.now());
            seguimiento.setRegistradoPor("sistema");
            seguimientoRepository.save(seguimiento);
        }

        System.out.println(">>> Recordatorios de vencimiento enviados: " + cuotasAVencer.size());
    }
}
