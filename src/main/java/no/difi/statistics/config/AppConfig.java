package no.difi.statistics.config;

import no.difi.statistics.api.IngestRestController;
import no.difi.statistics.poc.RandomIngesterRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("application.properties")
public class AppConfig  {


    @Autowired
    private BackendConfig backendConfig;

    @Bean
    public IngestRestController api() {
        return new IngestRestController(backendConfig.ingestService());
    }

    @Bean
    public RandomIngesterRestController randomApi() {
        return new RandomIngesterRestController(backendConfig.ingestService());
    }

}
