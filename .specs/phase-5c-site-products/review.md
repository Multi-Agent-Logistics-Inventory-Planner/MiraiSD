# Review

## Scope reviewed

`site_products` expand/backfill behavior, per-site assortment semantics, and cross-service forecasting compatibility.

## Findings

- [Spec] Activity terminology conflicted with other records — **act on**: durable data/API §2 now defines global `products.is_active` and per-site `site_products.is_stocked`.

## Residual risk

- Flyway is not yet canonical; migration parity is owned by Slice F2.
