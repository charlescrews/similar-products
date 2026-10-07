# Similar Products API

Spring Boot service that exposes `GET /product/{productId}/similar` on port **5000**. It returns the detail of the products similar to a given one, ordered by similarity, by aggregating the two existing product APIs.

The original statement of the exercise is in [STATEMENT.md](./STATEMENT.md).

## Stack

- Java 21 (virtual threads, records, sealed types, pattern matching) and Spring Boot 3.5
- OpenAPI Generator, to generate the REST interface from the agreed contract
- Caffeine for asynchronous caching and Resilience4j for circuit breakers
- JUnit 5, Mockito, AssertJ, WireMock, ArchUnit and JaCoCo
- Maven Wrapper, Docker and GitHub Actions

## Running the application

Start the mocks of the existing APIs and the metrics infrastructure:

```bash
docker-compose up -d simulado influxdb grafana
```

Start the application. Maven does not need to be installed, because the wrapper downloads it:

```bash
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

Try it:

```bash
curl http://localhost:5000/product/1/similar
```

```json
[
  { "id": "2", "name": "Dress",  "price": 19.99, "availability": true },
  { "id": "3", "name": "Blazer", "price": 29.99, "availability": false },
  { "id": "4", "name": "Boots",  "price": 39.99, "availability": true }
]
```

### Running the performance test

With the mocks and the application running:

```bash
docker-compose run --rm k6 run scripts/test.js
```

Results are available at http://localhost:3000/d/Le2Ku9NMk/k6-performance-test.

The same test can also be run on GitHub, without any local setup, from **Actions → Performance test → Run workflow**.

### With Docker

```bash
docker build -t similar-products .
docker run -p 5000:5000 -e PRODUCT_API_BASE_URL=http://host.docker.internal:3001 similar-products
```

## Tests

```bash
./mvnw verify
```

This runs every test and writes the coverage report to `target/site/jacoco/index.html`. The same command runs in GitHub Actions on every push.

| Test                                | Type         | What it covers                                                                                  |
|-------------------------------------|--------------|-------------------------------------------------------------------------------------------------|
| `SimilarProductsApiIntegrationTest` | Integration  | The scenarios of the performance test (normal, not found, error, slow) against a stubbed API    |
| `ProductCatalogAdapterTest`         | Adapter      | Timeouts, error mapping, caching, request coalescing and circuit breaker opening (WireMock)      |
| `GetSimilarProductsServiceTest`     | Unit         | Ordering, partial results, de-duplication and parallel lookups                                  |
| `HexagonalArchitectureTest`         | Architecture | Dependencies point towards the domain, and domain and application are framework-free           |

## Architecture

The service follows a hexagonal (ports and adapters) architecture:

```
com.carlosdiaz.similarproducts
├── domain                        # No framework dependencies
│   ├── model                     # Product, ProductLookup (Found | NotFound | Unavailable)
│   ├── exception                 # ProductNotFoundException, ProductCatalogUnavailableException
│   └── port
│       ├── in                    # GetSimilarProductsUseCase
│       └── out                   # ProductCatalogPort
├── application
│   └── service                   # GetSimilarProductsService: parallel fan-out, partial results
└── infrastructure
    ├── config                    # Wiring, typed configuration properties
    └── adapter
        ├── in/rest               # Controller implementing the generated contract interface
        └── out/productapi        # HTTP client + caching and circuit breakers
```

`HexagonalArchitectureTest` enforces these rules on every build, so the architecture is protected and not only drawn.

### Request flow

1. The controller receives `GET /product/{id}/similar` and calls the use case.
2. The use case asks the catalog port for the similar ids. If the product does not exist, the API returns `404`.
3. The detail of every similar product is looked up **in parallel**, on virtual threads.
4. Products that are found are returned in similarity order. Products that do not exist or cannot be retrieved are **left out**.

## Design decisions

### Contract-first

The REST interface (`ProductApi`) and its model are generated at build time from [`similarProducts.yaml`](./similarProducts.yaml), the contract agreed with the front-end, and the controller implements that interface. Any divergence from the contract becomes a compilation error instead of a bug found by the consumer.

### Performance

- **Parallel lookups:** the response time is bounded by the slowest product lookup, not by the sum of all of them.
- **Virtual threads (Java 21):** both Tomcat and the lookups run on virtual threads. Blocking I/O is cheap, so the code stays simple and imperative and still handles a high number of concurrent requests, without a reactive stack or pool tuning.
- **Asynchronous caching with request coalescing:** product details and similar ids are cached with Caffeine. Because the cache stores *futures*, concurrent requests for the same product share a single in-flight call. Under the 200 virtual users of the performance test, a slow product is requested once, not 200 times.

  An asynchronous cache is also a deliberate choice for Java 21: a synchronous `cache.get(key, loader)` blocks inside a lock, which pins the virtual thread to its carrier thread while the HTTP call is in progress.
- **HTTP/1.1 with connection reuse,** through the JDK `HttpClient`.

### Resilience

- **Timeouts on every call:** connect 500 ms, read 2 s. A slow dependency, such as product `10000` with its 50 s delay, never holds a request for longer than we are willing to wait.
- **Partial responses (graceful degradation):** one failing similar product does not break the whole response. The customer still sees the products that could be retrieved.
- **Per-outcome cache expiration:** each lookup result has its own TTL:

  | Outcome       | TTL   | Reason                                                                     |
  |---------------|-------|----------------------------------------------------------------------------|
  | Found         | 1 min | Product data changes, but not on every request                             |
  | Not found     | 1 min | Avoids repeatedly asking for products that do not exist                    |
  | Unavailable   | 10 s  | Avoids hammering a failing product, while still recovering quickly         |

- **Circuit breakers (Resilience4j):** one per downstream operation. If the product API starts failing, calls fail fast instead of waiting for timeouts, which protects both this service and the dependency. A `404` is a valid answer, so it does not count as a failure.
- **Explicit outcomes in the domain:** `ProductLookup` is a sealed type (`Found`, `NotFound`, `Unavailable`). The application decides with an exhaustive `switch` what to do with each outcome, and the infrastructure decides how long to cache each of them. No `null`s or exceptions are used for control flow.

### Error semantics

| Situation                                          | Response                                        |
|----------------------------------------------------|-------------------------------------------------|
| Similar products found (even if some are omitted)  | `200` with the available products               |
| The product does not exist                         | `404` (as defined in the contract)              |
| The similar ids cannot be retrieved                | `503 Service Unavailable`                       |

Errors follow RFC 7807 (`application/problem+json`).

## Configuration

Every setting can be overridden with environment variables (for example `PRODUCT_API_BASE_URL` or `PRODUCT_API_READ_TIMEOUT`).

| Property                                  | Default                 |
|-------------------------------------------|-------------------------|
| `product-api.base-url`                    | `http://localhost:3001` |
| `product-api.connect-timeout`             | `500ms`                 |
| `product-api.read-timeout`                | `2s`                    |
| `product-api.cache.product-ttl`           | `1m`                    |
| `product-api.cache.not-found-ttl`         | `1m`                    |
| `product-api.cache.unavailable-ttl`       | `10s`                   |
| `product-api.cache.similar-ids-ttl`       | `1m`                    |
| `product-api.circuit-breaker.*`           | See `application.yml`   |

Health and metrics are exposed at `/actuator/health` and `/actuator/metrics`.

## Trade-offs and possible improvements

- **Freshness vs. performance:** caching means a price or availability change can take up to one TTL to show. For stricter freshness, the TTL can be lowered, or the cache can be invalidated through events published by the product service.
- **Completeness vs. latency:** the read timeout decides which slow products make it into the response. Two seconds is a compromise for this exercise. In production it should come from the latency SLOs agreed with the front-end.
- **Stale-while-revalidate:** Caffeine's `refreshAfterWrite` could serve the cached value while it is refreshed in the background, removing even the occasional cache-miss latency.
- **Batch endpoint:** if the product API offered a batch endpoint (`GET /products?ids=...`), one call would replace N calls.
- **Distributed cache:** with several instances, a shared cache such as Redis would improve the hit ratio.
- **Observability:** export the circuit breaker and cache metrics through Micrometer, and add distributed tracing across the calls.
