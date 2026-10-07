package com.carlosdiaz.similarproducts.infrastructure.adapter.in.rest;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the API against a stubbed product API that reproduces the scenarios of the
 * provided performance test ({@code shared/simulado/mocks.json}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SimilarProductsApiIntegrationTest {

    @RegisterExtension
    static WireMockExtension productApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void productApiProperties(DynamicPropertyRegistry registry) {
        registry.add("product-api.base-url", productApi::baseUrl);
        registry.add("product-api.read-timeout", () -> "500ms");
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubExistingApis() {
        stubSimilarIds("1", "[2,3,4]");
        stubSimilarIds("2", "[3,100,1000]");
        stubSimilarIds("4", "[1,2,5]");
        stubSimilarIds("5", "[1,2,6]");
        productApi.stubFor(WireMock.get("/product/99/similarids").willReturn(notFound()));
        productApi.stubFor(WireMock.get("/product/7/similarids").willReturn(serverError()));

        stubProduct("1", "Shirt", "9.99", true, 0);
        stubProduct("2", "Dress", "19.99", true, 0);
        stubProduct("3", "Blazer", "29.99", false, 100);
        stubProduct("4", "Boots", "39.99", true, 0);
        productApi.stubFor(WireMock.get("/product/5").willReturn(notFound()));
        productApi.stubFor(WireMock.get("/product/6").willReturn(serverError()));
        stubProduct("100", "Trousers", "49.99", false, 200);
        stubProduct("1000", "Coat", "89.99", true, 2_000);
    }

    @Test
    void shouldReturnSimilarProductsOrderedBySimilarityWhenAllDetailsAreAvailable() throws Exception {
        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains("2", "3", "4")))
                .andExpect(jsonPath("$[0].name").value("Dress"))
                .andExpect(jsonPath("$[0].price").value(19.99))
                .andExpect(jsonPath("$[0].availability").value(true))
                .andExpect(jsonPath("$[1].availability").value(false));
    }

    @Test
    void shouldOmitSimilarProductWhenItDoesNotExist() throws Exception {
        mockMvc.perform(get("/product/4/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains("1", "2")));
    }

    @Test
    void shouldOmitSimilarProductWhenItsDetailFails() throws Exception {
        mockMvc.perform(get("/product/5/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains("1", "2")));
    }

    @Test
    void shouldOmitSimilarProductWhenItsDetailExceedsTimeout() throws Exception {
        mockMvc.perform(get("/product/2/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains("3", "100")));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() throws Exception {
        mockMvc.perform(get("/product/99/similar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenSimilarIdsCannotBeRetrieved() throws Exception {
        mockMvc.perform(get("/product/7/similar"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Product catalog unavailable"));
    }

    private static void stubSimilarIds(String productId, String similarIds) {
        productApi.stubFor(WireMock.get("/product/%s/similarids".formatted(productId)).willReturn(okJson(similarIds)));
    }

    private static void stubProduct(String id, String name, String price, boolean availability, int delayMillis) {
        var body = "{\"id\":\"%s\",\"name\":\"%s\",\"price\":%s,\"availability\":%s}"
                .formatted(id, name, price, availability);
        productApi.stubFor(WireMock.get("/product/" + id).willReturn(okJson(body).withFixedDelay(delayMillis)));
    }
}
