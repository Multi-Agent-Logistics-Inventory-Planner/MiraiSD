# Review

## External review findings and disposition

An independent review of the working tree (before push) found four issues, all confirmed real
and fixed here. None were caught by the 560 tests green at the time of that review, which is
itself the reason this record needed a review pass before commit.

### 1. Active custom Kuji boxes appeared Closed (regression, severity: high)

`web-product-list-cache` W1 consolidated every `useProducts` caller onto
`ProductService.getAllProductsAsListItems()`. That method never called `withHasActiveBox` --
only `getRootProductsAsListItems()`/`getActiveRootProductsAsListItems()`/
`getRootKujiProductsAsListItems()` did. Before W1, a `rootOnly` web caller routed through one of
those three; after W1, every caller routes through `getAllProductsAsListItems()` instead, so
`hasActiveBox` silently went from a real value to always-null. `CustomKujiTabs`
(`apps/web/src/components/kuji/custom-kuji-tabs.tsx:39,43`) partitions products into Active/Closed
tabs purely on that field, so every product would render as Closed regardless of its real box
status.

**Fix:** `getAllProductsAsListItems()` now calls `withHasActiveBox` too
(`ProductService.java`). `openKujiBoxPort.findProductIdsWithOpenBox()` is one bounded query
(`KujiBoxRepository.findProductIdsWithStatus`), matching the cost profile of the `hasChildren`
fold this whole record is about -- not a reintroduction of the egress problem.

**Regression coverage:** `ProductServiceListItemsTest` (new, unit, Mockito) proves
`getAllProductsAsListItems()` marks a product with an open box as `hasActiveBox=true` and one
without as `false`. `use-products.test.ts` gained a test proving `hasActiveBox` survives the
client-side `rootOnly` filter unchanged.

**Root cause of the miss:** the original plan and B2/B3 focused on `hasChildren`'s fold, which
happens inside the JPQL query itself. `hasActiveBox` is a separate, service-layer enrichment step
applied selectively by only 3 of 8 list methods -- a distinction the original egress fix did not
track when W1 consolidated every `useProducts` caller onto one method.

### 2. Catalog detail omitted the assigned supplier's name (bug, severity: medium)

`CatalogProductMapper` had no `@Mapping` for `preferredSupplierName`. `preferredSupplierId`
auto-mapped because `Product.preferredSupplierId` is a real, same-named shadow column;
`preferredSupplierName` has no matching property on `Product` at all (it only exists on the
associated `Supplier`), so MapStruct silently left it unmapped. No `unmappedTargetPolicy` is
configured in `pom.xml`, so this compiled clean with no warning.

**Fix:** added `@Mapping(target = "preferredSupplierName", source = "product", qualifiedByName =
"safePreferredSupplierName")`, reusing the same lazy-load-safe accessor pattern already
established in `ProductMapper.safePreferredSupplierName`.

**Regression coverage:** `ProductListEgressIT.catalogProductDetail_endToEnd_...` asserts
`$.preferredSupplierName` on a real HTTP response against a real Postgres-backed supplier.

### 3. Catalog detail could recursively load the category subtree (bug, severity: medium)

`CatalogProductResponse.category` had no explicit `@Mapping`. Because both `Product.category`
(type `Category`) and `CatalogProductResponse.category` (type `CategoryResponseDTO`) share the
same property name, and `CategoryResponseDTO`'s fields structurally match `Category`'s
(`id`/`parentId`/`name`/`slug`/`displayOrder`/`isActive`/`usesPacks`/`children`), MapStruct 1.5
auto-generates a nested `Category -> CategoryResponseDTO` sub-mapper for same-named,
structurally-compatible properties. That auto-generated mapper would recursively map `children`
(a `LAZY @OneToMany`), triggering a lazy-load chain at mapping time and returning a subtree shape
inconsistent with the list endpoint's flat, always-childless category
(`CATALOG_LIST_SELECT`'s inline `CategoryResponseDTO.builder()...children(emptyList())`).

**Fix:** added an explicit `@Mapping(target = "category", source = "product.category",
qualifiedByName = "flatCategory")` with a hand-written `flatCategory` method matching the list
endpoint's exact flat shape. Verified against the generated `CatalogProductMapperImpl.java`:
before the fix would have shown a nested sub-mapper method; after, `toResponse` calls
`flatCategory(product.getCategory())` directly, with no recursive method generated at all.

**Regression coverage:** `ProductListEgressIT.catalogProductDetail_endToEnd_...` seeds a
category with a parent and a child (so a recursive mapper would have something to traverse),
asserts the response's `category.children` is empty, and asserts exactly one statement in the
whole request references the `categories` table at all (the single-row detail fetch's own JOIN,
which legitimately touches `categories` twice via two aliases for the product's own category and
that category's parent -- not a second, separate query for children).

### 4. The egress IT tested the repository, not the regression boundary (test-design finding)

`ProductListEgressIT`'s three original tests called `productRepository.find*()` methods
directly. Each proved its own query is slim -- true and still valuable -- but none would have
caught finding 1: `ProductService.getAllProductsAsListItems()` calling an *additional* method
around a perfectly slim repository call is invisible to a test that never goes through
`ProductService` at all. The site-products test's "exactly one statement" framing was also
misleading: `SiteProductController.getSiteProducts` always issues a second statement
(`SiteAssortment.rowsForSite`) against a different table, which the original test never observed
by calling `findAllSettingsDefaults()` in isolation.

**Fix:** added three new tests firing real HTTP requests through `mockMvc` and inspecting the
actual captured SQL for the whole request:
- `legacyProductList_throughController_...` -- proves `GET /api/products` issues the slim list
  query plus exactly the `kuji_boxes` lookup (2 statements past the JWT-auth preamble), not the 1
  the repository-level test alone would suggest, and not a JOIN FETCH regression either.
- `siteProductsList_throughController_...` -- proves `GET /api/v1/sites/{siteId}/products`
  issues exactly the slim `products` query and the separate `site_products` query (2 statements
  past the auth/site/membership preamble), neither containing a JOIN. Explicitly documents that 2
  is correct, not a regression from an implied "1".
- `catalogProductDetail_endToEnd_...` -- covers findings 2 and 3 together against a real Postgres
  Testcontainers instance and a real HTTP request.

A helper, `withoutAuthPreamble`, filters the JWT-auth user lookup and (for site-scoped routes) the
site/membership lookups `SiteAccessAuthorizationFilter` issues ahead of any endpoint's own work,
so each assertion describes only the endpoint's own query shape.

The original three repository-level tests were kept: they are still correct, cheap, and useful as
the first line of defense on the query itself, they just aren't sufficient alone, which the class
javadoc now says explicitly.

## Verification after fixes

- `./mvnw -B clean test`: 483/483 (482 -> 483, the new `ProductServiceListItemsTest`).
- `./mvnw -B clean test -Dtest='*IT'`: 563/563 (560 -> 563, the three new HTTP-level
  `ProductListEgressIT` tests).
- `apps/web`: `npx tsc --noEmit` clean; `npx vitest run --pool=threads`: 461/461 (460 -> 461, the
  new `hasActiveBox` survival test).

No source changes beyond the four fixes and their regression tests. No migrations, no production
action, no push.
