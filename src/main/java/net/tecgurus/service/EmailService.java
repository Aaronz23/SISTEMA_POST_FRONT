package net.tecgurus.service;

import jakarta.mail.internet.MimeMessage;
import net.tecgurus.model.Quote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class EmailService {

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Autowired
    private JavaMailSender mailSender;

    public void sendOrderConfirmation(String to, byte[] pdf, Quote quote) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to != null && to.contains(",")
                    ? Arrays.stream(to.split(",")).map(String::trim).toArray(String[]::new)
                    : new String[]{to});

            String folio = (quote.getNumber() != null)
                    ? "Q-" + String.format("%02d", quote.getNumber())
                    : quote.getId();

            helper.setSubject("Confirmación de Pedido - Cotización " + folio);
            helper.setText("Estimado cliente,\n\nAdjunto encontrarás el PDF de tu pedido confirmado.\n\nSaludos,\nEquipo Contacto Eléctrico");

            helper.addAttachment("Pedido_" + folio + ".pdf", new ByteArrayResource(pdf));

            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al enviar correo: " + e.getMessage(), e);
        }
    }

    public void sendQuoteConfirmation(String to, byte[] pdf, Quote quote) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to != null && to.contains(",")
                    ? Arrays.stream(to.split(",")).map(String::trim).toArray(String[]::new)
                    : new String[]{to});
            String folio = (quote.getNumber() != null) ? "Q-" + String.format("%02d", quote.getNumber()) : quote.getId();
            helper.setSubject("Cotización " + folio + " - Contacto Eléctrico");

            // HTML email content
            String htmlContent = """
                <!DOCTYPE html>
                <html>
                    <head>
                        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                    </head>
                    <body>
                        <div style="font-family: Arial, sans-serif; line-height: 1.6; max-width: 600px; margin: 0 auto;">
                            <div style="text-align: center; margin-bottom: 20px;">
                                <h2 style="color: #2c3e50;">¡Gracias por su preferencia!</h2>
                                <p>Estimado cliente,</p>
                            </div>
                            

                            <div style="background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin-bottom: 20px;">
                                <p>Le enviamos adjunta la cotización solicitada con folio: 
                                <strong>%s</strong></p>
                                

                                <p>Esta cotización tiene una vigencia de 15 días naturales a partir de la fecha de emisión.</p>
                                

                                <p>Si desea realizar algún cambio o tiene alguna duda, no dude en contactarnos.</p>
                            </div>
                            

                            <div style="margin-top: 30px; color: #7f8c8d; font-size: 0.9em;">
                                <p>Atentamente,</p>
                                <p><strong>Equipo de Ventas</strong><br>
                                Contacto Eléctrico<br>
                                Teléfono: [Número de Teléfono]<br>
                                Email: [Correo de Contacto]</p>
                            </div>
                        </div>
                    </body>
                </html>
                """.formatted(folio);

            helper.setText(htmlContent, true);
            helper.addAttachment("Cotizacion_" + folio + ".pdf", new ByteArrayResource(pdf));

            mailSender.send(message);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al enviar correo de cotización: " + e.getMessage(), e);
        }
    }

    public void sendEmailWithAttachment(
            String to,
            String subject,
            String text,
            String attachmentFilename,
            byte[] attachment,
            String attachmentContentType) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to != null && to.contains(",") ? to.split(",") : new String[]{to});
            helper.setSubject(subject);

            // Si el texto parece ser HTML, lo enviamos como tal, de lo contrario como texto plano
            boolean isHtml = text != null && (text.contains("<") && text.contains(">"));
            helper.setText(text, isHtml);

            // Agregar el archivo adjunto
            if (attachment != null && attachment.length > 0) {
                helper.addAttachment(attachmentFilename,
                    new ByteArrayResource(attachment),
                    attachmentContentType);
            }

            mailSender.send(message);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al enviar correo con adjunto: " + e.getMessage(), e);
        }
    }

    public static class Attachment {
        private final String filename;
        private final byte[] content;
        private final String contentType;

        public Attachment(String filename, byte[] content, String contentType) {
            this.filename = filename;
            this.content = content;
            this.contentType = contentType;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public byte[] getContent() {
            return content;
        }
        
        public String getContentType() {
            return contentType;
        }
    }

    public void sendEmailWithAttachments(
            String to,
            String subject,
            String text,
            Attachment... attachments) {
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to != null && to.contains(",") ? to.split(",") : new String[]{to});
            helper.setSubject(subject);
            
            // Si el texto parece ser HTML, lo enviamos como tal, de lo contrario como texto plano
            boolean isHtml = text != null && (text.contains("<") && text.contains(">"));
            helper.setText(text, isHtml);
            
            // Agregar todos los archivos adjuntos
            if (attachments != null) {
                for (Attachment attachment : attachments) {
                    if (attachment != null && attachment.getContent() != null && attachment.getContent().length > 0) {
                        helper.addAttachment(
                            attachment.getFilename(), 
                            new ByteArrayResource(attachment.getContent()),
                            attachment.getContentType()
                        );
                    }
                }
            }

            mailSender.send(message);
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al enviar correo con adjuntos: " + e.getMessage(), e);
        }
    }
}
