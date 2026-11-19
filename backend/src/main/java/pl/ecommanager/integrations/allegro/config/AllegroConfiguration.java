package pl.ecommanager.integrations.allegro.config;

import pl.ecommanager.integrations.allegro.client.AllegroClient;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AllegroProperties.class)
class AllegroConfiguration {
    @Bean
    AllegroClient allegroClient(RestClient.Builder builder, AllegroProperties properties) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new AllegroClient(builder.requestFactory(factory).build(), properties);
    }
}
