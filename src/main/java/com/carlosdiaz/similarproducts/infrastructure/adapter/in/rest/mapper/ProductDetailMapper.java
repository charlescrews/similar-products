package com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest.mapper;

import com.carlosdiaz.similarproducts.domain.model.Product;
import com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest.api.model.ProductDetail;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProductDetailMapper {

    /**
     * Maps to the contract model. A {@link LinkedHashSet} keeps the similarity order.
     */
    public Set<ProductDetail> toResponse(List<Product> products) {
        return products.stream()
                .map(ProductDetailMapper::toResponse)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static ProductDetail toResponse(Product product) {
        return new ProductDetail(product.id(), product.name(), product.price(), product.available());
    }
}
