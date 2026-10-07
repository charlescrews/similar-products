package com.carlosdiaz.similarproducts.infrastructure.adapter.out.productapi;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP client of the existing product API. It only deals with HTTP concerns: a missing product
 * is reported as an empty {@link Optional}; any other failure (5xx, timeout, connection error)
 * is propagated as an exception.
 */
public class ProductApiClient {

    private static final ParameterizedTypeReference<List<String>> PRODUCT_IDS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public ProductApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Creates a client with explicit timeouts: a slow dependency must never hold our threads
     * for longer than we are willing to wait.
     */
    public static ProductApiClient create(RestClient.Builder builder, String baseUrl,
                                          Duration connectTimeout, Duration readTimeout) {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return new ProductApiClient(builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build());
    }

    public Optional<List<String>> findSimilarProductIds(String productId) {
        try {
            var similarIds = restClient.get()
                    .uri("/product/{productId}/similarids", productId)
                    .retrieve()
                    .body(PRODUCT_IDS);
            return Optional.of(Objects.requireNonNullElse(similarIds, List.of()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<ProductDetailDto> findProduct(String productId) {
        try {
            var product = restClient.get()
                    .uri("/product/{productId}", productId)
                    .retrieve()
                    .body(ProductDetailDto.class);
            return Optional.of(Objects.requireNonNull(product, "Empty product detail response"));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
