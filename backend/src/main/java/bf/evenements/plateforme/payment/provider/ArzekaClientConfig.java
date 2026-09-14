package bf.evenements.plateforme.payment.provider;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** HTTP client used to call the FasoArzeka gateway — short timeouts, dedicated bean. */
@Configuration
public class ArzekaClientConfig {

    @Bean
    public RestTemplate arzekaRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }
}
