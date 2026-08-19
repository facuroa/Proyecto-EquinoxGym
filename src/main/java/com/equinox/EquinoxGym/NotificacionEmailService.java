package com.equinox.EquinoxGym;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class NotificacionEmailService {

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final boolean habilitado;
    private final String gymName;

    public NotificacionEmailService(JavaMailSender mailSender,
                                    ITemplateEngine templateEngine,
                                    @Value("${equinox.notificaciones.email.habilitado:false}") boolean habilitado,
                                    @Value("${equinox.branding.gym-name:Gym System}") String gymName) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.habilitado = habilitado;
        this.gymName = gymName;
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
        String nombreAdjunto = "Comprobante-" + pago.getNumeroComprobante() + ".pdf";
        enviar(socio.getEmail(), asunto, "email/comprobante-email", contexto, nombreAdjunto, pdf);
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
            return ComprobantePdfGenerator.generar(pago, socio, gymName);
        } catch (Exception e) {
            System.err.println(">>> No se pudo generar el PDF del comprobante: " + e.getMessage());
            return null;
        }
    }
}
