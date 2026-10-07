package com.carlosdiaz.similarproducts.domain.model;

/**
 * Outcome of looking up a single product detail.
 * <p>
 * Making the three outcomes explicit lets the application decide how to treat each of them
 * (and lets infrastructure cache each of them differently) instead of hiding them behind
 * {@code null} or exceptions.
 */
public sealed interface ProductLookup {

    String productId();

    record Found(Product product) implements ProductLookup {
        @Override
        public String productId() {
            return product.id();
        }
    }

    record NotFound(String productId) implements ProductLookup {
    }

    record Unavailable(String productId) implements ProductLookup {
    }
}
