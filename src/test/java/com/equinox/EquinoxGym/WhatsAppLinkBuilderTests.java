package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppLinkBuilderTests {

    @Test
    void normalizaNumeroCelularArgentinoDeDiezDigitos() {
        assertThat(WhatsAppLinkBuilder.construirUrl("381 555-1234", null))
                .isEqualTo("https://wa.me/5493815551234");
    }

    @Test
    void normalizaNumeroConCodigoDePaisSinNueve() {
        assertThat(WhatsAppLinkBuilder.construirUrl("+54 381 555-1234", null))
                .isEqualTo("https://wa.me/5493815551234");
    }

    @Test
    void normalizaNumeroConCeroInicial() {
        assertThat(WhatsAppLinkBuilder.construirUrl("0381 555-1234", null))
                .isEqualTo("https://wa.me/5493815551234");
    }

    @Test
    void numeroInvalidoDevuelveNull() {
        assertThat(WhatsAppLinkBuilder.construirUrl("12345", null)).isNull();
        assertThat(WhatsAppLinkBuilder.construirUrl(null, "hola")).isNull();
    }

    @Test
    void agregaMensajeCodificadoComoParametroText() {
        String url = WhatsAppLinkBuilder.construirUrl("381 555-1234", "Hola! ¿Cómo estás?");

        assertThat(url).startsWith("https://wa.me/5493815551234?text=");
        assertThat(url).doesNotContain(" ");
        assertThat(url).doesNotContain("?Cómo");
    }

    @Test
    void sinMensajeNoAgregaParametroText() {
        assertThat(WhatsAppLinkBuilder.construirUrl("381 555-1234", ""))
                .isEqualTo("https://wa.me/5493815551234");
        assertThat(WhatsAppLinkBuilder.construirUrl("381 555-1234", "   "))
                .isEqualTo("https://wa.me/5493815551234");
    }
}
