package com.carlosdiaz.similarproducts.infrastructure.adapter.out.productapi;

import com.carlosdiaz.similarproducts.domain.exception.ProductCatalogUnavailableException;
import com.carlosdiaz.similarproducts.domain.exception.ProductNotFoundException;
import com.carlosdiaz.similarproducts.domain.model.Product;
import com.carlosdiaz.similarproducts.domain.model.ProductLookup;
import com.carlosdiaz.similarproducts.infrastructure.config.ProductApiProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductCatalogAdapterTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    private static final ProductApiProperties.Cache CACHE = new ProductApiProperties.Cache(
            100, Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(1));

    @RegisterExtension
    static WireMockExtension productApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ProductCatalogAdapter adapter = adapterWith(CircuitBreaker.ofDefaults("product"));

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void shouldReturnSimilarIdsAsTextWhenApiReturnsNumericIds() {
        productApi.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));

        assertThat(adapter.findSimilarProductIds("1")).containsExactly("2", "3", "4");
    }

    @Test
    void shouldThrowProductNotFoundExceptionWhenSimilarIdsReturnNotFound() {
        productApi.stubFor(get("/product/99/similarids").willReturn(notFound()));

        assertThatThrownBy(() -> adapter.findSimilarProductIds("99"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldThrowCatalogUnavailableExceptionWhenSimilarIdsFail() {
        productApi.stubFor(get("/product/1/similarids").willReturn(serverError()));

        assertThatThrownBy(() -> adapter.findSimilarProductIds("1"))
                .isInstanceOf(ProductCatalogUnavailableException.class);
    }

    @Test
    void shouldReturnFoundWhenProductExists() {
        stubProduct("1", 0);

        assertThat(adapter.findProduct("1")).isEqualTo(new ProductLookup.Found(
                new Product("1", "Shirt", new BigDecimal("9.99"), true)));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() {
        productApi.stubFor(get("/product/5").willReturn(notFound()));

        assertThat(adapter.findProduct("5")).isEqualTo(new ProductLookup.NotFound("5"));
    }

    @Test
    void shouldReturnUnavailableWhenProductApiFails() {
        productApi.stubFor(get("/product/6").willReturn(serverError()));

        assertThat(adapter.findProduct("6")).isEqualTo(new ProductLookup.Unavailable("6"));
    }

    @Test
    void shouldReturnUnavailableWhenProductApiExceedsReadTimeout() {
        stubProduct("1", 1_000);

        assertThat(adapter.findProduct("1")).isEqualTo(new ProductLookup.Unavailable("1"));
    }

    @Test
    void shouldServeProductFromCacheWhenRequestedAgain() {
        stubProduct("1", 0);

        adapter.findProduct("1");
        adapter.findProduct("1");

        productApi.verify(1, getRequestedFor(urlEqualTo("/product/1")));
    }

    @Test
    void shouldShareSingleCallWhenSameProductIsRequestedConcurrently() {
        stubProduct("1", 200);

        var lookups = IntStream.range(0, 20)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> adapter.findProduct("1"), executor))
                .toList();
        var results = lookups.stream().map(CompletableFuture::join).toList();

        assertThat(results).allMatch(ProductLookup.Found.class::isInstance);
        productApi.verify(1, getRequestedFor(urlEqualTo("/product/1")));
    }

    @Test
    void shouldFailFastWithoutCallingApiWhenCircuitBreakerIsOpen() {
        var circuitBreaker = CircuitBreaker.of("product", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        var adapterWithBreaker = adapterWith(circuitBreaker);
        List.of("10", "11", "12", "13").forEach(id -> {
            productApi.stubFor(get("/product/" + id).willReturn(serverError()));
            adapterWithBreaker.findProduct(id);
        });
        stubProduct("1", 0);

        var lookup = adapterWithBreaker.findProduct("1");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(lookup).isEqualTo(new ProductLookup.Unavailable("1"));
        productApi.verify(0, getRequestedFor(urlEqualTo("/product/1")));
    }

    private ProductCatalogAdapter adapterWith(CircuitBreaker productCircuitBreaker) {
        var client = ProductApiClient.create(RestClient.builder(), productApi.baseUrl(),
                Duration.ofMillis(500), READ_TIMEOUT);
        return new ProductCatalogAdapter(client, CircuitBreaker.ofDefaults("similarIds"),
                productCircuitBreaker, executor, CACHE);
    }

    private static void stubProduct(String id, int delayMillis) {
        productApi.stubFor(get("/product/" + id).willReturn(
                okJson("{\"id\":\"%s\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}".formatted(id))
                        .withFixedDelay(delayMillis)));
    }
}
