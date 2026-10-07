package com.carlosdiaz.similarproducts.application.service;

import com.carlosdiaz.similarproducts.domain.model.Product;
import com.carlosdiaz.similarproducts.domain.model.ProductLookup;
import com.carlosdiaz.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import com.carlosdiaz.similarproducts.domain.port.out.ProductCatalogPort;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Fetches the similar product ids and then all their details in parallel, so the response time
 * is bounded by the slowest lookup instead of the sum of all of them.
 * <p>
 * A similar product whose detail is not found or cannot be retrieved is left out of the
 * response: one failing product must not prevent the customer from seeing the others.
 */
public class GetSimilarProductsService implements GetSimilarProductsUseCase {

    private final ProductCatalogPort productCatalog;
    private final Executor executor;

    public GetSimilarProductsService(ProductCatalogPort productCatalog, Executor executor) {
        this.productCatalog = productCatalog;
        this.executor = executor;
    }

    @Override
    public List<Product> getSimilarProducts(String productId) {
        var lookups = productCatalog.findSimilarProductIds(productId).stream()
                .distinct()
                .map(similarId -> CompletableFuture.supplyAsync(() -> productCatalog.findProduct(similarId), executor))
                .toList();

        return lookups.stream()
                .map(CompletableFuture::join)
                .flatMap(GetSimilarProductsService::foundProduct)
                .toList();
    }

    private static Stream<Product> foundProduct(ProductLookup lookup) {
        return switch (lookup) {
            case ProductLookup.Found(Product product) -> Stream.of(product);
            case ProductLookup.NotFound notFound -> Stream.empty();
            case ProductLookup.Unavailable unavailable -> Stream.empty();
        };
    }
}
