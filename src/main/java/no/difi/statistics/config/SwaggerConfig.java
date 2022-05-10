package no.difi.statistics.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;


@Configuration
public class SwaggerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String maskinportenUri;

    @Bean
    public OpenAPI apiInfo() {
        final String apiVersion = System.getProperty("difi.version", "N/A");
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Statistikk for offentlige tjenester")
                                .description("Beskrivelse av API for innlegging av data (versjon %s).")
                                .version(apiVersion)
                )
                .components(getComponents());
    }

    private Components getComponents() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl(maskinportenUri + "authorize")
                                .tokenUrl(maskinportenUri + "token")
                                .scopes(new Scopes().addString("digdir:statistikk.skriv", "Skrivetilgong til Statistikk-api for innlegging av tidsserier"))));

        return new Components().securitySchemes(Collections.singletonMap("authorization-code-flow", securityScheme));
    }

}
