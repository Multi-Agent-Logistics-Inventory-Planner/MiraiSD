# Review

## Scope reviewed

Catalog facade extraction, product-state compatibility, module boundaries, and caller migration.

## Findings

- [Spec] `products.is_active` had conflicting descriptions — **act on**: durable data/API §2 is now the sole definition; this record defers to it.

## Residual risk

- Global legacy activity remains deliberately stock-derived until a separately reviewed product-lifecycle change.
