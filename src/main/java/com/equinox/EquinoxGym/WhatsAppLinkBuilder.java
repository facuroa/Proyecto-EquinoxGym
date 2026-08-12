package com.equinox.EquinoxGym;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Arma links "wa.me" con mensaje pre-cargado. La normalizacion de numero
 * apunta a celulares argentinos (formato wa.me exige el prefijo "549"),
 * que es el mercado de esta instalacion.
 */
public final class WhatsAppLinkBuilder {

    private WhatsAppLinkBuilder() {
    }

    public static String construirUrl(String telefono, String mensaje) {
        String numero = normalizarNumeroArgentino(telefono);
        if (numero == null) {
            return null;
        }
        String url = "https://wa.me/" + numero;
        if (mensaje != null && !mensaje.isBlank()) {
            url += "?text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8);
        }
        return url;
    }

    private static String normalizarNumeroArgentino(String telefono) {
        if (telefono == null) {
            return null;
        }
        String numero = telefono.replaceAll("\\D", "");
        if (numero.startsWith("00")) {
            numero = numero.substring(2);
        }
        if (numero.startsWith("0") && numero.length() == 11) {
            numero = numero.substring(1);
        }
        if (numero.startsWith("549") && numero.length() == 13) {
            return numero;
        }
        if (numero.startsWith("54") && numero.length() == 12) {
            return "549" + numero.substring(2);
        }
        if (numero.length() == 10) {
            return "549" + numero;
        }
        return null;
    }
}
