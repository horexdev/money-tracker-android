# Transaction Templates Domain

`core:templates` owns local quick transaction templates. Templates are manual shortcuts and do not have a schedule, due processing, background work, or run markers.

## Behavior

- CRUD and apply operations are profile-scoped.
- Account currency is copied from the selected local account when a template is created or updated.
- Categories must be active and compatible with the template transaction type.
- Fixed templates apply the stored positive amount.
- Variable templates require a positive `variableAmountCents` value when applied.
- Applying a template delegates transaction creation to `core:transactions`, so transaction validation and snapshot date behavior stay shared.
- Reorder accepts the complete profile template id list, requires every id exactly once, and rewrites dense `sort_order` values.

## Ordering

Template lists use `sort_order ASC, created_at_epoch_millis ASC, id ASC`. The final `id` tie-breaker keeps order stable when imported or older data has duplicate sort values.
