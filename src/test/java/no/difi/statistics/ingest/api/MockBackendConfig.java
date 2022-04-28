package no.difi.statistics.ingest.api;

import no.difi.statistics.IngestService;
import no.difi.statistics.config.BackendConfig;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

//@Configuration
public class MockBackendConfig implements BackendConfig {

    @Override
    @Bean
    public IngestService ingestService() {
        return mock(IngestService.class);
    }

}
