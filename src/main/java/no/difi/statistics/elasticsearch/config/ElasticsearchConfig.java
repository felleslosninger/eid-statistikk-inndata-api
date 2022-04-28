package no.difi.statistics.elasticsearch.config;

import no.difi.statistics.IngestService;
import no.difi.statistics.config.BackendConfig;
import no.difi.statistics.elasticsearch.Client;
import no.difi.statistics.elasticsearch.ElasticsearchIngestService;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Profile({"!unittest"})
public class ElasticsearchConfig implements BackendConfig {

    private final Environment environment;

    private final String elasticSearchHost;
    private final int elasticSearchPort;

    @Autowired
    public ElasticsearchConfig(Environment environment, @Value("${no.difi.statistics.elasticsearch.host}") String elasticSearchHost, @Value("${no.difi.statistics.elasticsearch.port}") int elasticSearchPort) {
        this.environment = environment;
        this.elasticSearchHost = elasticSearchHost;
        this.elasticSearchPort = elasticSearchPort;
    }

    @Bean
    public IngestService ingestService() {
        return new ElasticsearchIngestService(elasticsearchHighLevelClient());
    }

    @Bean
    public Client elasticsearchClient() {
        return new Client(
                elasticsearchHighLevelClient(),
                getHttpScheme()+"://" + elasticSearchHost + ":" + elasticSearchPort
        );
    }

    @Bean(destroyMethod = "close")
    public RestHighLevelClient elasticsearchHighLevelClient() {
        return new RestHighLevelClient(elasticsearchLowLevelClient());
    }

    private RestClientBuilder elasticsearchLowLevelClient() {
        Header[] headers = new Header[]{new BasicHeader("Authorization","ApiKey " + loadApiKey())};
        RestClientBuilder builder = RestClient.builder(new HttpHost(elasticSearchHost, elasticSearchPort, getHttpScheme()));
        builder.setDefaultHeaders(headers);
        return builder;
    }

    private String loadApiKey(){
        final String fileName = environment.getRequiredProperty("file.base.difi-statistikk");
        try {
            final Path pathToPasswordFile = ResourceUtils.getFile(fileName).toPath();
            return new String(Files.readAllBytes(pathToPasswordFile));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load file defined in environment property 'file.base.difi-statistikk': " + fileName, e);
        }
    }

    private String getHttpScheme(){
        String scheme = "http";
        // TODO put this into config?
        if (elasticSearchHost.endsWith("elastic-cloud.com")) {
            scheme = "https";
        }
        return scheme;
    }

}
