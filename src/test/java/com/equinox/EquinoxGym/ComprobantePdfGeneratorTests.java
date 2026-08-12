package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ComprobantePdfGeneratorTests {

    @Test
    void generaUnPdfValidoConDatosCompletos() throws Exception {
        Socio socio = new Socio();
        socio.setNombre("María José");
        socio.setApellido("Núñez");
        socio.setDni("30111222");

        Cuota cuota = new Cuota();
        cuota.setFechaVencimiento(LocalDate.of(2026, 9, 10));
        cuota.setMonto(new BigDecimal("45000"));

        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(new BigDecimal("45000"));
        pago.setMedioPago("Transferencia");
        pago.setFechaPago(LocalDate.of(2026, 8, 10));
        pago.setFechaRegistro(LocalDateTime.of(2026, 8, 10, 22, 4));
        pago.setRegistradoPor("admin");

        byte[] pdf = ComprobantePdfGenerator.generar(pago, socio, "Keep Fit Gym");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void generaUnPdfAunSinSocioNiCuota() throws Exception {
        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("10000"));
        pago.setMedioPago("Efectivo");
        pago.setFechaPago(LocalDate.now());

        byte[] pdf = ComprobantePdfGenerator.generar(pago, null, "Keep Fit Gym");

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void incluyeElLogoCuandoSePasaUnaImagenValida() throws Exception {
        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("10000"));
        pago.setMedioPago("Efectivo");
        pago.setFechaPago(LocalDate.now());
        byte[] logo = new org.springframework.core.io.ClassPathResource("static/img/icono.png")
                .getContentAsByteArray();

        byte[] pdfConLogo = ComprobantePdfGenerator.generar(pago, null, "Keep Fit Gym", logo);
        byte[] pdfSinLogo = ComprobantePdfGenerator.generar(pago, null, "Keep Fit Gym", null);

        assertThat(pdfConLogo).isNotEmpty();
        // El PDF con la imagen embebida es sensiblemente mas pesado que el que solo tiene texto.
        assertThat(pdfConLogo.length).isGreaterThan(pdfSinLogo.length);
    }

    @Test
    void noRompeSiElLogoEsInvalido() throws Exception {
        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("10000"));
        pago.setMedioPago("Efectivo");
        pago.setFechaPago(LocalDate.now());

        byte[] pdf = ComprobantePdfGenerator.generar(pago, null, "Keep Fit Gym", "esto no es una imagen".getBytes());

        assertThat(pdf).isNotEmpty();
    }
}
