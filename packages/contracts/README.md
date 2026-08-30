# @mirai/contracts

`openapi.json` is generated, not hand-written. It is the OpenAPI 3.1 document produced by
springdoc from `services/inventory-service`'s actual controllers.

## Regenerating

```
cd services/inventory-service
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -Dtest=OpenApiContractExportTest test
```

This runs `OpenApiContractExportTest`, a `@SpringBootTest` that starts the app on a random port
with the `test` profile (which is the only profile where `springdoc.api-docs.enabled=true`),
fetches `/v3/api-docs`, and overwrites this file.

Run this after adding, removing, or changing the shape of any REST endpoint, then regenerate
`packages/api-client` (see its README) so the generated client stays in sync.
