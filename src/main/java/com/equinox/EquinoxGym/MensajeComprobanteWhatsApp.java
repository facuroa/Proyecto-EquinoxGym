package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;

/**
 * Texto del boton "Enviar por WhatsApp" del comprobante.
 *
 * <p>El aviso del mail y el del recordatorio previo al vencimiento solo se
 * agregan cuando de verdad ocurren: con el envio de emails activado
 * (EMAIL_NOTIFICACIONES_HABILITADO) y el socio con un mail cargado. Si no, el
 * socio leeria que le llego algo que nunca le llega.
 */
@Component
public class MensajeComprobanteWhatsApp {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String gymName;
    private final boolean emailHabilitado;
    private final int diasAnticipacion;

    public MensajeComprobanteWhatsApp(@Value("${equinox.branding.gym-name:Keep Fit Gym}") String gymName,
                                      @Value("${equinox.notificaciones.email.habilitado:false}") boolean emailHabilitado,
                                      @Value("${equinox.recordatorios.dias-anticipacion:3}") int diasAnticipacion) {
        this.gymName = gymName;
        this.emailHabilitado = emailHabilitado;
        this.diasAnticipacion = diasAnticipacion;
    }

    public String armar(Pago pago, Socio socio) {
        StringBuilder mensaje = new StringBuilder();
        String nombre = socio.getNombre() == null || socio.getNombre().isBlank() ? "" : " " + socio.getNombre().trim();
        mensaje.append("¡Hola").append(nombre).append("! Recibimos tu pago en ").append(gymName).append(".\n\n");

        mensaje.append("Monto: *$ ").append(monto(pago.getMonto())).append("*\n");
        if (pago.getFechaPago() != null) {
            mensaje.append("Fecha de pago: ").append(FORMATO_FECHA.format(pago.getFechaPago())).append("\n");
        }
        if (pago.getVigenteHasta() != null) {
            mensaje.append("Próximo vencimiento: *").append(FORMATO_FECHA.format(pago.getVigenteHasta())).append("*\n");
        }
        mensaje.append("Comprobante: ").append(pago.getNumeroComprobante()).append("\n");

        boolean llegaMail = emailHabilitado && socio.getEmail() != null && !socio.getEmail().isBlank();
        if (llegaMail) {
            mensaje.append("\nTe enviamos el comprobante a tu mail (").append(socio.getEmail().trim())
                    .append("). Si no lo encontrás, revisá la carpeta de spam o correo no deseado.");
            if (diasAnticipacion > 0) {
                mensaje.append("\n").append(diasAnticipacion).append(diasAnticipacion == 1 ? " día" : " días")
                        .append(" antes del vencimiento te vamos a enviar un recordatorio por mail.");
            }
            mensaje.append("\n");
        }

        mensaje.append("\n¡Gracias!");
        return mensaje.toString();
    }

    /** 50000.00 → "50.000"; 50000.50 → "50.000,50". */
    static String monto(BigDecimal monto) {
        if (monto == null) {
            return "0";
        }
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        boolean entero = monto.stripTrailingZeros().scale() <= 0;
        return new DecimalFormat(entero ? "#,##0" : "#,##0.00", simbolos).format(monto);
    }
}
