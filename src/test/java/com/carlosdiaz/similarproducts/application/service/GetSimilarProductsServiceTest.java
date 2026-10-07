package com.carlosdiaz.similarproducts.application.service;

import com.carlosdiaz.similarproducts.domain.exception.ProductNotFoundException;
import com.carlosdiaz.similarproducts.domain.model.Product;
import com.carlosdiaz.similarproducts.domain.model.ProductLookup;
import com.carlosdiaz.similarproducts.domain.port.out.ProductCatalogPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetSimilarProductsServiceTest {

    private final ProductCatalogPort productCatalog = mock(ProductCatalogPort.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final GetSimilarProductsService service = new GetSimilarProductsService(productCatalog, executor);

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void shouldReturnSimilarProductsOrderedBySimilarityWhenAllAreFound() {
        when(productCatalog.findSimilarProductIds("1")).thenReturn(List.of("3", "2"));
        when(productCatalog.findProduct("3")).thenReturn(found("3"));
        when(productCatalog.findProduct("2")).thenReturn(found("2"));

        var similarProducts = service.getSimilarProducts("1");

        assertThat(similarProducts).extracting(Product::id).containsExactly("3", "2");
    }

    @Test
    void shouldOmitSimilarProductsWhenTheyAreNotFoundOrUnavailable() {
        when(productCatalog.findSimilarProductIds("1")).thenReturn(List.of("2", "5", "6"));
        when(productCatalog.findProduct("2")).thenReturn(found("2"));
        when(productCatalog.findProduct("5")).thenReturn(new ProductLookup.NotFound("5"));
        when(productCatalog.findProduct("6")).thenReturn(new ProductLookup.Unavailable("6"));

        var similarProducts = service.getSimilarProducts("1");

        assertThat(similarProducts).extracting(Product::id).containsExactly("2");
    }

    @Test
    void shouldLookUpEachProductOnceWhenSimilarIdsAreDuplicated() {
        when(productCatalog.findSimilarProductIds("1")).thenReturn(List.of("2", "2"));
        when(productCatalog.findProduct("2")).thenReturn(found("2"));

        var similarProducts = service.getSimilarProducts("1");

        assertThat(similarProducts).extracting(Product::id).containsExactly("2");
        verify(productCatalog, times(1)).findProduct("2");
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoSimilarProducts() {
        when(productCatalog.findSimilarProductIds("1")).thenReturn(List.of());

        assertThat(service.getSimilarProducts("1")).isEmpty();
    }

    @Test
    void shouldPropagateProductNotFoundExceptionWhenProductDoesNotExist() {
        when(productCatalog.findSimilarProductIds("99")).thenThrow(new ProductNotFoundException("99"));

        assertThatThrownBy(() -> service.getSimilarProducts("99"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldLookUpSimilarProductsInParallelWhenThereAreSeveral() {
        var lookupDelay = Duration.ofMillis(400);
        when(productCatalog.findSimilarProductIds("1")).thenReturn(List.of("2", "3", "4"));
        for (var id : List.of("2", "3", "4")) {
            when(productCatalog.findProduct(id)).thenAnswer(invocation -> {
                Thread.sleep(lookupDelay);
                return found(id);
            });
        }

        var start = System.nanoTime();
        var similarProducts = service.getSimilarProducts("1");
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(similarProducts).hasSize(3);
        // Sequential lookups would take at least 3 x 400 ms
        assertThat(elapsed).isLessThan(lookupDelay.multipliedBy(2));
    }

    private static ProductLookup found(String id) {
        return new ProductLookup.Found(new Product(id, "Product " + id, new BigDecimal("9.99"), true));
    }
}
