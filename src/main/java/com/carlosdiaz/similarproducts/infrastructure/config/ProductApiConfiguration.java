package com.carlosdiaz.similarproducts.infrastructure.config;

import com.carlosdiaz.similarproducts.domain.port.out.ProductCatalogPort;
import com.carlosdiaz.similarproducts.infrastructure.adapter.out.productapi.ProductApiClient;
import com.carlosdiaz.similarproducts.infrastructure.adapter.out.productapi.ProductCatalogAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ProductApiConfiguration {

    /**
     * Virtual threads: blocking I/O is cheap, so every product lookup can run on its own thread
     * without sizing or tuning a pool.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService productLookupExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public ProductApiClient productApiClient(RestClient.Builder restClientBuilder, ProductApiProperties properties) {
        return ProductApiClient.create(restClientBuilder, properties.baseUrl(),
                properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public ProductCatalogPort productCatalogPort(ProductApiClient productApiClient, ProductApiProperties properties) {
        return new ProductCatalogAdapter(
                productApiClient,
                circuitBreaker("similarIds", properties.circuitBreaker()),
                circuitBreaker("productDetail", properties.circuitBreaker()),
                productLookupExecutor(),
                properties.cache());
    }

    private static CircuitBreaker circuitBreaker(String name, ProductApiProperties.CircuitBreakerSettings settings) {
        var config = CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedNumberOfCallsInHalfOpenState())
                .build();
        return CircuitBreaker.of(name, config);
    }
}
