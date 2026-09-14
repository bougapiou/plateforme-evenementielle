package bf.evenements.plateforme.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, ArzekaProperties.class})
public class AppConfig {
}
