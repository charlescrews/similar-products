package com.carlosdiaz.similarproducts.domain.port.in;

import com.carlosdiaz.similarproducts.domain.model.Product;

import java.util.List;

public interface GetSimilarProductsUseCase {

    /**
     * Returns the detail of the products similar to the given one, ordered by similarity.
     *
     * @throws com.carlosdiaz.similarproducts.domain.exception.ProductNotFoundException
     *         if the product does not exist
     * @throws com.carlosdiaz.similarproducts.domain.exception.ProductCatalogUnavailableException
     *         if the similar products cannot be retrieved
     */
    List<Product> getSimilarProducts(String productId);
}
