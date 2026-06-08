package org.sunyaxing.transflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.ResourceResolver;
import org.springframework.web.reactive.resource.ResourceResolverChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFlux
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /**
     * SPA 路由回退：非 /api 且非静态文件的请求返回 index.html
     */
    static class SpaResourceResolver implements ResourceResolver {

        private static final Resource INDEX = new ClassPathResource("static/index.html");

        @Override
        public Mono<Resource> resolveResource(ServerWebExchange exchange, String requestPath,
                                              List<? extends Resource> locations, ResourceResolverChain chain) {
            return chain.resolveResource(exchange, requestPath, locations)
                    .switchIfEmpty(Mono.defer(() -> {
                        if (requestPath.startsWith("api/") || requestPath.contains(".")) {
                            return Mono.empty();
                        }
                        return Mono.just(INDEX);
                    }));
        }

        @Override
        public Mono<String> resolveUrlPath(String resourcePath, List<? extends Resource> locations,
                                           ResourceResolverChain chain) {
            return chain.resolveUrlPath(resourcePath, locations);
        }
    }
}
