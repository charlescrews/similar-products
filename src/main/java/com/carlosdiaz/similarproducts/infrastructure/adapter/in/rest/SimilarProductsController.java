package com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest;

import com.carlosdiaz.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest.api.ProductApi;
import com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest.api.model.ProductDetail;
import com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest.mapper.ProductDetailMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Implements the interface generated from the agreed contract ({@code similarProducts.yaml}),
 * so any divergence from the contract is a compilation error.
 */
@RestController
public class SimilarProductsController implements ProductApi {

    private final GetSimilarProductsUseCase getSimilarProductsUseCase;
    private final ProductDetailMapper mapper;

    public SimilarProductsController(GetSimilarProductsUseCase getSimilarProductsUseCase, ProductDetailMapper mapper) {
        this.getSimilarProductsUseCase = getSimilarProductsUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Set<ProductDetail>> getProductSimilar(String productId) {
        var similarProducts = getSimilarProductsUseCase.getSimilarProducts(productId);
        return ResponseEntity.ok(mapper.toResponse(similarProducts));
    }
}
