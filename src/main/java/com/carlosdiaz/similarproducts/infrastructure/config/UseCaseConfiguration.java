package com.carlosdiaz.similarproducts.infrastructure.config;

import com.carlosdiaz.similarproducts.application.service.GetSimilarProductsService;
import com.carlosdiaz.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import com.carlosdiaz.similarproducts.domain.port.out.ProductCatalogPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

/**
 * Registers the use cases as beans, keeping the domain and application layers free of
 * framework annotations.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    public GetSimilarProductsUseCase getSimilarProductsUseCase(
            ProductCatalogPort productCatalogPort,
            @Qualifier("productLookupExecutor") Executor productLookupExecutor) {
        return new GetSimilarProductsService(productCatalogPort, productLookupExecutor);
    }
}
