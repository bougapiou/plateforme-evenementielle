package bf.evenements.plateforme.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailSenderTest {

    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final EmailSender sender = new EmailSender(mail);

    @Test
    void never_mails_the_placeholder_address_of_a_phone_only_guest() {
        assertThat(sender.send("tel-22670123456@guest.plateforme.local", "s", "b")).isFalse();
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void still_mails_real_addresses() {
        assertThat(sender.send("awa@example.bf", "s", "b")).isTrue();
        verify(mail).send(any(SimpleMailMessage.class));
    }
}
