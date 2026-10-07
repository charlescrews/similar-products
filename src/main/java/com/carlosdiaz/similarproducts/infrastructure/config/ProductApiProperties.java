package com.carlosdiaz.similarproducts.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the integration with the existing product API.
 */
@ConfigurationProperties(prefix = "product-api")
public record ProductApiProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Cache cache,
        CircuitBreakerSettings circuitBreaker) {

    public record Cache(
            long maximumSize,
            Duration similarIdsTtl,
            Duration productTtl,
            Duration notFoundTtl,
            Duration unavailableTtl) {
    }

    public record CircuitBreakerSettings(
            float failureRateThreshold,
            int slidingWindowSize,
            int minimumNumberOfCalls,
            Duration waitDurationInOpenState,
            int permittedNumberOfCallsInHalfOpenState) {
    }
}
