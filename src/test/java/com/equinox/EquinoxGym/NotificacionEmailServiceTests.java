package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

class NotificacionEmailServiceTests {

    @Test
    void noHaceNadaCuandoElFlagEstaApagado() {
        NotificacionEmailService servicio = new NotificacionEmailService(null, null, null, false, "Keep Fit Gym");

        Socio socio = new Socio();
        socio.setEmail("socio@correo.com");
        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setMonto(BigDecimal.TEN);
        cuota.setFechaVencimiento(LocalDate.now());
        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(BigDecimal.TEN);
        pago.setFechaPago(LocalDate.now());
        pago.setMedioPago("Efectivo");

        // No debe lanzar excepcion ni intentar usar mailSender/templateEngine (son null).
        servicio.enviarComprobantePago(pago);
        servicio.enviarRecordatorioVencimiento(cuota);
    }

    @Test
    void noHaceNadaSiElSocioNoTieneEmailAunConElFlagEncendido() {
        NotificacionEmailService servicio = new NotificacionEmailService(null, null, null, true, "Keep Fit Gym");

        Socio socio = new Socio();
        Cuota cuota = new Cuota();
        cuota.setSocio(socio);

        servicio.enviarRecordatorioVencimiento(cuota);
    }
}
