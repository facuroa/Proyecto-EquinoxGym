package com.equinox.EquinoxGym;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComprobanteNumeracionTests {

    @AfterEach
    void restaurarPrefijoPorDefecto() {
        // El prefijo es estatico: se deja como estaba para no condicionar
        // al resto de los tests.
        Pago.configurarPrefijo("EQX");
    }

    @Test
    void usaElPrefijoDeLaInstalacion() {
        new ComprobanteNumeracion("TRP");

        Pago pago = new Pago();
        pago.setId(12L);

        assertThat(pago.getNumeroComprobante()).isEqualTo("TRP-00000012");
    }

    @Test
    void unPagoSinGuardarQuedaPendiente() {
        new ComprobanteNumeracion("TRP");

        assertThat(new Pago().getNumeroComprobante()).isEqualTo("TRP-PENDIENTE");
    }

    @Test
    void sinConfigurarNadaSigueSiendoElPrefijoHistorico() {
        new ComprobanteNumeracion("EQX");

        Pago pago = new Pago();
        pago.setId(1L);

        assertThat(pago.getNumeroComprobante()).isEqualTo("EQX-00000001");
    }

    @Test
    void unPrefijoVacioNoPisaElQueYaEstaba() {
        new ComprobanteNumeracion("TRP");
        new ComprobanteNumeracion("   ");

        Pago pago = new Pago();
        pago.setId(7L);

        assertThat(pago.getNumeroComprobante()).isEqualTo("TRP-00000007");
    }
}
