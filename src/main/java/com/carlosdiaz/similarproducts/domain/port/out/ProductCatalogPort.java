package com.carlosdiaz.similarproducts.domain.port.out;

import com.carlosdiaz.similarproducts.domain.model.ProductLookup;

import java.util.List;

public interface ProductCatalogPort {

    /**
     * Returns the ids of the products similar to the given one, ordered by similarity.
     *
     * @throws com.carlosdiaz.similarproducts.domain.exception.ProductNotFoundException
     *         if the product does not exist
     * @throws com.carlosdiaz.similarproducts.domain.exception.ProductCatalogUnavailableException
     *         if the catalog cannot be reached
     */
    List<String> findSimilarProductIds(String productId);

    /**
     * Looks up a product detail. Never throws: failures are reported as
     * {@link ProductLookup.Unavailable}.
     */
    ProductLookup findProduct(String productId);
}
