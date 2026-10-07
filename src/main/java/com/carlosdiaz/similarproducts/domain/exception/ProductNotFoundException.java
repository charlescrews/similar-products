package com.carlosdiaz.similarproducts.domain.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Product %s not found".formatted(productId));
    }
}
