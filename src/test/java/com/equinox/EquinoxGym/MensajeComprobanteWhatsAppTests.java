package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MensajeComprobanteWhatsAppTests {

    @Test
    void conMailAvisaDondeBuscarElComprobanteYElRecordatorio() {
        String mensaje = new MensajeComprobanteWhatsApp("Tropa Gym", true, 3).armar(pago(), socio("lucia@gmail.com"));

        assertThat(mensaje).isEqualTo("""
                ¡Hola Lucia! Recibimos tu pago en Tropa Gym.

                Monto: *$ 50.000*
                Fecha de pago: 18/11/2026
                Próximo vencimiento: *25/12/2026*
                Comprobante: TRP-00000012

                Te enviamos el comprobante a tu mail (lucia@gmail.com). Si no lo encontrás, revisá la carpeta de spam o correo no deseado.
                3 días antes del vencimiento te vamos a enviar un recordatorio por mail.

                ¡Gracias!""");
    }

    @Test
    void sinMailCargadoNoPrometeNingunMail() {
        String mensaje = new MensajeComprobanteWhatsApp("Tropa Gym", true, 3).armar(pago(), socio(null));

        assertThat(mensaje).contains("Recibimos tu pago", "Próximo vencimiento: *25/12/2026*")
                .doesNotContain("mail", "recordatorio");
    }

    @Test
    void conLosEmailsApagadosTampocoPrometeMails() {
        String mensaje = new MensajeComprobanteWhatsApp("Tropa Gym", false, 3).armar(pago(), socio("lucia@gmail.com"));

        assertThat(mensaje).doesNotContain("mail", "recordatorio");
    }

    @Test
    void elRecordatorioUsaLosDiasConfigurados() {
        assertThat(new MensajeComprobanteWhatsApp("Tropa Gym", true, 1).armar(pago(), socio("a@b.com")))
                .contains("1 día antes del vencimiento");
        assertThat(new MensajeComprobanteWhatsApp("Tropa Gym", true, 5).armar(pago(), socio("a@b.com")))
                .contains("5 días antes del vencimiento");
    }

    @Test
    void losMontosVanConPuntoDeMiles() {
        assertThat(MensajeComprobanteWhatsApp.monto(new BigDecimal("50000.00"))).isEqualTo("50.000");
        assertThat(MensajeComprobanteWhatsApp.monto(new BigDecimal("1250000"))).isEqualTo("1.250.000");
        assertThat(MensajeComprobanteWhatsApp.monto(new BigDecimal("50000.5"))).isEqualTo("50.000,50");
    }

    private static Socio socio(String email) {
        Socio socio = new Socio();
        socio.setNombre("Lucia");
        socio.setEmail(email);
        Plan plan = new Plan();
        plan.setNombre("Crossfit");
        plan.setDuracionMeses(1);
        socio.setPlan(plan);
        return socio;
    }

    private static Pago pago() {
        Cuota cuota = new Cuota();
        cuota.setFechaVencimiento(LocalDate.of(2026, 11, 25));
        Cuota renovacion = new Cuota();
        renovacion.setFechaVencimiento(LocalDate.of(2026, 12, 25));
        Pago pago = new Pago() {
            @Override
            public String getNumeroComprobante() {
                return "TRP-00000012";
            }
        };
        pago.setCuota(cuota);
        pago.setCuotaRenovacionGenerada(renovacion);
        pago.setMonto(new BigDecimal("50000.00"));
        pago.setFechaPago(LocalDate.of(2026, 11, 18));
        return pago;
    }
}
