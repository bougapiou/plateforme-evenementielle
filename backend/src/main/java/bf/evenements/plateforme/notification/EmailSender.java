package bf.evenements.plateforme.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends plain-text e-mails via SMTP (Mailpit in dev). Failures are swallowed so a
 * mail outage never breaks a business transaction — the notification is marked
 * {@code ECHEC} instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;

    public boolean send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return false;
        }
        // Phone-only guests get a non-routable placeholder address: never try to mail it.
        if (to.toLowerCase().endsWith(bf.evenements.plateforme.auth.AuthService.PLACEHOLDER_EMAIL_DOMAIN)) {
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom("no-reply@plateforme.bf");
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("Envoi e-mail à {} échoué : {}", to, e.getMessage());
            return false;
        }
    }
}
