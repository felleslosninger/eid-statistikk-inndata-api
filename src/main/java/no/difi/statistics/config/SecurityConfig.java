package no.difi.statistics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                // No authentication required for documentation paths used by Swagger
                .antMatchers(GET, "/", "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                // No authentication required for health check path or env
                .antMatchers(GET, "/health", "/env/**").permitAll()
                // Authentication required for ingest methods. Username must be equal to owner of series.
                // test without authentication .antMatchers(POST, "/{owner}/{seriesName}/**").authenticated()
                .antMatchers(POST, "/{owner}/{seriesName}/**").permitAll()
                // No authentication required for getting last point on a series
                .antMatchers(GET, "/{owner}/{seriesName}/{distance}/last").permitAll()
                .anyRequest().authenticated()
                .and()
                .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);
    }

}
