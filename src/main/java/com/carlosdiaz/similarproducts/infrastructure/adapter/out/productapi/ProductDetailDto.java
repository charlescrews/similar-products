package com.carlosdiaz.similarproducts.infrastructure.adapter.out.productapi;

import java.math.BigDecimal;

/**
 * Product detail as returned by the existing product API.
 */
public record ProductDetailDto(String id, String name, BigDecimal price, Boolean availability) {
}
