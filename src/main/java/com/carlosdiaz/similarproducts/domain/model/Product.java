package com.carlosdiaz.similarproducts.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Product detail as understood by the domain.
 */
public record Product(String id, String name, BigDecimal price, boolean available) {

    public Product {
        requireNotBlank(id, "id");
        requireNotBlank(name, "name");
        Objects.requireNonNull(price, "price must not be null");
    }

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
