package com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest;

import com.carlosdiaz.similarproducts.domain.exception.ProductCatalogUnavailableException;
import com.carlosdiaz.similarproducts.domain.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into RFC 7807 responses (application/problem+json).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", ex.getMessage());
    }

    @ExceptionHandler(ProductCatalogUnavailableException.class)
    public ProblemDetail handleCatalogUnavailable(ProductCatalogUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
