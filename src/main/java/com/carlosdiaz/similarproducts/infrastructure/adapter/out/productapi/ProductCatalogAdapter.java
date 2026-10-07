package com.carlosdiaz.similarproducts.infrastructure.adapter.out.productapi;

import com.carlosdiaz.similarproducts.domain.exception.ProductCatalogUnavailableException;
import com.carlosdiaz.similarproducts.domain.exception.ProductNotFoundException;
import com.carlosdiaz.similarproducts.domain.model.Product;
import com.carlosdiaz.similarproducts.domain.model.ProductLookup;
import com.carlosdiaz.similarproducts.domain.port.out.ProductCatalogPort;
import com.carlosdiaz.similarproducts.infrastructure.config.ProductApiProperties;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Adapts the existing product API to the domain port, adding the resilience and performance
 * concerns that the HTTP client should not know about:
 * <ul>
 *     <li><b>Asynchronous caching</b> (Caffeine): concurrent requests for the same product share a
 *     single in-flight call instead of hammering the dependency.</li>
 *     <li><b>Per-outcome expiration</b>: a found product, a missing product and an unavailable
 *     product are cached for different times, so a failing product is not retried on every
 *     request but recovers quickly.</li>
 *     <li><b>Circuit breakers</b> (Resilience4j): if the dependency is failing, calls fail fast
 *     instead of waiting for a timeout.</li>
 * </ul>
 */
public class ProductCatalogAdapter implements ProductCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogAdapter.class);

    private final ProductApiClient client;
    private final CircuitBreaker similarIdsCircuitBreaker;
    private final CircuitBreaker productCircuitBreaker;
    private final Executor executor;
    private final AsyncCache<String, List<String>> similarIdsCache;
    private final AsyncCache<String, ProductLookup> productCache;

    public ProductCatalogAdapter(ProductApiClient client,
                                 CircuitBreaker similarIdsCircuitBreaker,
                                 CircuitBreaker productCircuitBreaker,
                                 Executor executor,
                                 ProductApiProperties.Cache cacheProperties) {
        this.client = client;
        this.similarIdsCircuitBreaker = similarIdsCircuitBreaker;
        this.productCircuitBreaker = productCircuitBreaker;
        this.executor = executor;
        this.similarIdsCache = Caffeine.newBuilder()
                .maximumSize(cacheProperties.maximumSize())
                .expireAfterWrite(cacheProperties.similarIdsTtl())
                .buildAsync();
        this.productCache = Caffeine.newBuilder()
                .maximumSize(cacheProperties.maximumSize())
                .expireAfter(Expiry.creating((String productId, ProductLookup lookup) -> ttlOf(lookup, cacheProperties)))
                .buildAsync();
    }

    @Override
    public List<String> findSimilarProductIds(String productId) {
        try {
            return similarIdsCache
                    .get(productId, (id, cacheExecutor) -> CompletableFuture.supplyAsync(() -> fetchSimilarProductIds(id), executor))
                    .join();
        } catch (CompletionException e) {
            // Failed loads are not cached: the next request will try again
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    @Override
    public ProductLookup findProduct(String productId) {
        return productCache
                .get(productId, (id, cacheExecutor) -> CompletableFuture.supplyAsync(() -> fetchProduct(id), executor))
                .join();
    }

    private List<String> fetchSimilarProductIds(String productId) {
        Optional<List<String>> similarIds;
        try {
            similarIds = similarIdsCircuitBreaker.executeSupplier(() -> client.findSimilarProductIds(productId));
        } catch (RuntimeException e) {
            log.warn("Similar ids of product {} could not be retrieved: {}", productId, e.toString());
            throw new ProductCatalogUnavailableException(productId, e);
        }
        return similarIds.orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private ProductLookup fetchProduct(String productId) {
        try {
            return productCircuitBreaker.executeSupplier(() -> client.findProduct(productId))
                    .<ProductLookup>map(dto -> new ProductLookup.Found(toDomain(dto)))
                    .orElseGet(() -> new ProductLookup.NotFound(productId));
        } catch (RuntimeException e) {
            log.warn("Product {} could not be retrieved: {}", productId, e.toString());
            return new ProductLookup.Unavailable(productId);
        }
    }

    private static Product toDomain(ProductDetailDto dto) {
        return new Product(dto.id(), dto.name(), dto.price(), Boolean.TRUE.equals(dto.availability()));
    }

    private static Duration ttlOf(ProductLookup lookup, ProductApiProperties.Cache cacheProperties) {
        return switch (lookup) {
            case ProductLookup.Found found -> cacheProperties.productTtl();
            case ProductLookup.NotFound notFound -> cacheProperties.notFoundTtl();
            case ProductLookup.Unavailable unavailable -> cacheProperties.unavailableTtl();
        };
    }
}
