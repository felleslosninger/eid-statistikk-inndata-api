// https://stackoverflow.com/a/61131221/319826

package no.difi.statistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.StreamSupport;

@Component
public class PropertyLogger {
    private static final Logger logger = LoggerFactory.getLogger(PropertyLogger.class);

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        final Environment environment = event.getApplicationContext().getEnvironment();
        logger.info("====== Environment and configuration ======");
        logger.info("Active profiles: {}", Arrays.toString((environment.getActiveProfiles())));
        final MutablePropertySources sources = ((AbstractEnvironment) environment).getPropertySources();
        StreamSupport.stream(sources.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .distinct()
                .filter(prop -> !(prop.contains("credentials") || prop.contains("password")))
                .forEach(prop -> logger.info("{}: {}", prop, environment.getProperty(prop)));
        logger.info("===========================================");
    }
}
