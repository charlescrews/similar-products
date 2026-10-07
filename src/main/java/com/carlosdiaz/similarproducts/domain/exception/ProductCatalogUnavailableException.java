package com.carlosdiaz.similarproducts.domain.exception;

public class ProductCatalogUnavailableException extends RuntimeException {

    public ProductCatalogUnavailableException(String productId, Throwable cause) {
        super("Similar products of product %s could not be retrieved".formatted(productId), cause);
    }
}
