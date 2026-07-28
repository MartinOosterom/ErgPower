package work.zing.ergpower.pm5.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Prefixes the REST + SSE API with {@code /api/v1} (the OpenAPI server path) — but only the API
 * {@code @RestController}s, not the whole application. That leaves {@code /} free to serve the bundled
 * browser dashboard (classpath {@code static/}), so a single {@code java -jar … serve} process hosts
 * both the UI and the API on one origin/port — no separate Node/npm server, no proxy, no CORS.
 *
 * <p>Replaces a global {@code spring.webflux.base-path}, which would have prefixed the static SPA too.
 */
@Configuration
public class ApiRoutingConfig implements WebFluxConfigurer {

    @Override
    public void configurePathMatching(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api/v1",
                clazz -> clazz.isAnnotationPresent(RestController.class)
                        && clazz.getPackageName().startsWith("work.zing.ergpower"));
    }
}
