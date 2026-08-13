package com.equinox.EquinoxGym;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import java.io.IOException;

@Service
public class NotificacionEmailService {

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final LogoService logoService;
    private final ComprobantePdfStorageService pdfStorageService;
    private final boolean habilitado;
    private final String gymName;
    private final String appUrl;

    public NotificacionEmailService(JavaMailSender mailSender,
                                    ITemplateEngine templateEngine,
                                    LogoService logoService,
                                    ComprobantePdfStorageService pdfStorageService,
                                    @Value("${equinox.notificaciones.email.habilitado:false}") boolean habilitado,
                                    @Value("${equinox.branding.gym-name:Keep Fit Gym}") String gymName,
                                    @Value("${equinox.app-url:http://localhost:8085}") String appUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.logoService = logoService;
        this.pdfStorageService = pdfStorageService;
        this.habilitado = habilitado;
        this.gymName = gymName;
        this.appUrl = appUrl;
    }

    public void enviarComprobantePago(Pago pago) {
        if (!habilitado || pago == null || pago.getCuota() == null) {
            return;
        }
        Socio socio = pago.getCuota().getSocio();
        if (socio == null || socio.getEmail() == null || socio.getEmail().isBlank()) {
            return;
        }

        Context contexto = new Context();
        contexto.setVariable("gymName", gymName);
        contexto.setVariable("socio", socio);
        contexto.setVariable("pago", pago);

        String asunto = "Comprobante de pago " + pago.getNumeroComprobante() + " - " + gymName;
        byte[] pdf = generarPdfComprobante(pago, socio);

        // Guardar PDF en servidor y generar link
        try {
            String nombreArchivo = pdfStorageService.guardarComprobante(pdf, pago.getId());
            pago.setNombreArchivoComprobante(nombreArchivo);
            String linkDescarga = appUrl + "/pagos/" + pago.getId() + "/descargar-pdf";
            contexto.setVariable("linkDescargaComprobante", linkDescarga);
            enviar(socio.getEmail(), asunto, "email/comprobante-email", contexto);
        } catch (IOException e) {
            System.err.println(">>> No se pudo guardar el PDF del comprobante: " + e.getMessage());
        }
    }

    public void enviarRecordatorioVencimiento(Cuota cuota) {
        if (!habilitado || cuota == null) {
            return;
        }
        Socio socio = cuota.getSocio();
        if (socio == null || socio.getEmail() == null || socio.getEmail().isBlank()) {
            return;
        }

        Context contexto = new Context();
        contexto.setVariable("gymName", gymName);
        contexto.setVariable("socio", socio);
        contexto.setVariable("cuota", cuota);

        String asunto = "Tu cuota vence pronto - " + gymName;
        enviar(socio.getEmail(), asunto, "email/recordatorio-email", contexto);
    }

    private void enviar(String destinatario, String asunto, String plantilla, Context contexto) {
        enviar(destinatario, asunto, plantilla, contexto, null, null);
    }

    private void enviar(String destinatario, String asunto, String plantilla, Context contexto,
                        String nombreAdjunto, byte[] adjuntoPdf) {
        try {
            String html = templateEngine.process(plantilla, contexto);
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            if (adjuntoPdf != null && adjuntoPdf.length > 0) {
                helper.addAttachment(nombreAdjunto, new ByteArrayResource(adjuntoPdf));
            }
            mailSender.send(mensaje);
        } catch (Exception e) {
            System.err.println(">>> No se pudo enviar el email a " + destinatario + ": " + e.getMessage());
        }
    }

    private byte[] generarPdfComprobante(Pago pago, Socio socio) {
        try {
            byte[] logo = logoService.obtenerLogoBytes();
            return ComprobantePdfGenerator.generar(pago, socio, gymName, logo);
        } catch (Exception e) {
            System.err.println(">>> No se pudo generar el PDF del comprobante: " + e.getMessage());
            return null;
        }
    }
}
