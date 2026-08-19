package com.equinox.EquinoxGym;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Genera un PDF de una pagina con el mismo contenido que el comprobante
 * impreso (comprobante-pago.html), para adjuntarlo al email.
 */
public final class ComprobantePdfGenerator {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final float MARGEN = 55f;
    private static final float ANCHO_ETIQUETA = 150f;

    private ComprobantePdfGenerator() {
    }

    public static byte[] generar(Pago pago, Socio socio, String gymName) throws IOException {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);

            PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            float anchoUtil = pagina.getMediaBox().getWidth() - (2 * MARGEN);
            float y = pagina.getMediaBox().getHeight() - MARGEN;

            try (PDPageContentStream cs = new PDPageContentStream(documento, pagina)) {
                y = escribir(cs, negrita, 20, MARGEN, y, gymName);
                y -= 6;
                y = escribir(cs, normal, 11, MARGEN, y, "Comprobante de pago");
                y = escribir(cs, normal, 10, MARGEN, y, "Comprobante " + pago.getNumeroComprobante());
                y -= 10;
                lineaHorizontal(cs, MARGEN, y, anchoUtil);
                y -= 24;

                if (pago.isAnulado()) {
                    y = escribir(cs, negrita, 13, MARGEN, y, "COMPROBANTE ANULADO");
                    y -= 10;
                }

                y = renglon(cs, normal, negrita, y, "Socio",
                        socio != null ? socio.getNombreCompleto() : "Sin socio asociado");
                y = renglon(cs, normal, negrita, y, "DNI",
                        socio != null && socio.getDni() != null ? socio.getDni() : "-");
                y = renglon(cs, normal, negrita, y, "Fecha de pago",
                        pago.getFechaPago() != null ? FORMATO_FECHA.format(pago.getFechaPago()) : "-");
                y = renglon(cs, normal, negrita, y, "Medio de pago",
                        pago.getMedioPago() != null ? pago.getMedioPago() : "-");
                if (pago.getCuota() != null && pago.getCuota().getFechaVencimiento() != null) {
                    y = renglon(cs, normal, negrita, y, "Concepto",
                            "Cuota de gimnasio - vencimiento " + FORMATO_FECHA.format(pago.getCuota().getFechaVencimiento()));
                }

                y -= 14;
                lineaHorizontal(cs, MARGEN, y, anchoUtil);
                y -= 30;

                y = escribir(cs, negrita, 16, MARGEN, y, "Total abonado: $ " + pago.getMonto());
                y -= 20;

                y = renglon(cs, normal, negrita, y, "Registrado por",
                        pago.getRegistradoPor() != null ? pago.getRegistradoPor() : "-");
                if (pago.getFechaRegistro() != null) {
                    y = renglon(cs, normal, negrita, y, "Fecha y hora de registro",
                            FORMATO_FECHA_HORA.format(pago.getFechaRegistro()));
                }

                if (pago.isAnulado() && pago.getMotivoAnulacion() != null) {
                    y -= 10;
                    y = renglon(cs, normal, negrita, y, "Motivo de anulacion", pago.getMotivoAnulacion());
                }

                y -= 30;
                escribir(cs, normal, 8, MARGEN, y,
                        "Este comprobante corresponde a un movimiento registrado en " + gymName + ".");
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private static float renglon(PDPageContentStream cs, PDFont normal, PDFont negrita,
                                 float y, String etiqueta, String valor) throws IOException {
        escribir(cs, negrita, 10, MARGEN, y, etiqueta + ":");
        escribir(cs, normal, 10, MARGEN + ANCHO_ETIQUETA, y, valor);
        return y - 18;
    }

    private static float escribir(PDPageContentStream cs, PDFont fuente, float tamano,
                                  float x, float y, String texto) throws IOException {
        cs.beginText();
        cs.setFont(fuente, tamano);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitizar(texto));
        cs.endText();
        return y - (tamano + 6);
    }

    private static void lineaHorizontal(PDPageContentStream cs, float x, float y, float ancho) throws IOException {
        cs.setLineWidth(0.75f);
        cs.moveTo(x, y);
        cs.lineTo(x + ancho, y);
        cs.stroke();
    }

    private static String sanitizar(String texto) {
        if (texto == null) {
            return "";
        }
        // Helvetica estandar (WinAnsiEncoding) no cubre todo Unicode; se
        // reemplazan los caracteres "tipograficos" mas comunes que puedan
        // colarse en nombres u observaciones pegadas desde Word/Whatsapp.
        return texto
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-');
    }
}
