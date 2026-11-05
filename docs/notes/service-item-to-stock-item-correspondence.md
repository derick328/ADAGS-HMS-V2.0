# `service_item.code` ↔ `item.code` — integrity audit

Written during the revenue-analytics hardening pass. The admin revenue
reports show a medicine's strength and form beside its billed name, and
that enrichment joins the **billing** catalogue to the **stock** catalogue
on `code`. This note records what that join rests on, what can break it,
and what should replace it.

**No migration has been applied for this.** The recommendation below needs
approval before anything is changed.

## What the two tables are

| | `service_item` | `item` |
|---|---|---|
| Owns | what can be **charged for** | what is **held in stock** |
| Key columns | `code` (UNIQUE), `name`, `category`, `type`, `billing_category` | `code` (UNIQUE), `name`, `form`, `strength`, `unit`, `regulatory_schedule` |
| Referenced by | `invoice_line.service_item_id`, `price_list_entry` | `stock_movement`, `stock_batch`, `stock_balance` |
| Relationship to the other | **none declared** | **none declared** |

There is no foreign key in either direction, and no unique constraint
spanning the two. The only thing connecting them is that both carry a
`code` column and, by convention, the same string.

## Measured state (audit run against the live database)

| Check | Result |
|---|---|
| Pharmacy billing items (`category='PHARMACY'`) | 1,933 |
| Stock items (`item`) | 1,933 |
| Matched on `code` | **1,933** |
| Pharmacy billing items with no stock match | **0** |
| Stock items with no billing match | **0** |
| Non-pharmacy `service_item` rows colliding with a stock code | **0** |
| Matched pairs whose names differ (case-insensitive) | **0** |

Today the correspondence is a perfect bijection, and because both `code`
columns are `UNIQUE` the join can never multiply a row. The reporting join
is therefore safe *right now*; the exposure is to future drift.

## What maintains it

`SetPharmacyItemPriceService.setPrice()`:

```java
ServiceItem serviceItem = serviceItemRepository.findByCode(command.itemCode())
        .orElseGet(() -> serviceItemRepository.insert(/* ... */));
```

`ReviewPharmacyItemProposalService` passes `item.code()` into that call, so
approving a pharmacy item proposal creates a `service_item` carrying the
stock item's own code. The invariant holds because one service happens to
pass the right string — it is an application convention, enforced nowhere
else.

## Integrity risks

1. **Two independent creation paths.** The proposal flow above mints codes
   as `ITEM-<uuid-prefix>`; the bulk catalogue importer
   (`JdbcCatalogImportBatch` → `JdbcCatalogItemLookup.insert`) writes
   `service_item` rows with supplier codes (`DRG0593`) and never touches
   `item`. An import that adds a pharmacy service item without a matching
   stock row produces a billable medicine with no stock record, and the
   report shows a blank strength.
2. **No cascade on rename.** `item.name`, `item.strength` and `item.form`
   can be edited with nothing propagating to `service_item`. The report
   would then print the billed name beside a strength from the edited stock
   record — two descriptions of what may no longer be the same product.
3. **Nothing prevents a collision.** A future non-pharmacy service item
   could be given a code that an unrelated stock item already uses, and the
   join would silently attach that item's strength to it. Currently zero
   such rows exist, and the reporting query only enriches rows it has
   already filtered to the pharmacy category, which contains the blast
   radius but does not remove it.
4. **Soft-deletes diverge.** `item.active` and `service_item.is_active` are
   independent booleans. An item deactivated in stock stays billable in the
   catalogue unless someone deactivates it there too.

None of these corrupt money: every financial figure on the revenue reports
comes from `invoice_line` and `payment_allocation` and never from `item`.
The exposure is **descriptive** — a medicine could be labelled with the
wrong strength — which on a pharmacy report is still worth fixing.

## Recommendation (needs approval — not implemented)

**Add an explicit nullable reference, then enforce it in the one service
that creates the pair.**

```sql
ALTER TABLE service_item ADD COLUMN stock_item_id UUID REFERENCES item(id);
UPDATE service_item si SET stock_item_id = it.id
  FROM item it WHERE it.code = si.code AND si.category = 'PHARMACY';
CREATE UNIQUE INDEX idx_service_item_stock_item ON service_item (stock_item_id)
  WHERE stock_item_id IS NOT NULL;
```

Why this shape:

- **Additive and reversible.** A nullable column plus a partial unique
  index. No row is deleted, no existing column changes type, and the
  backfill is unambiguous because today's match is exactly 1:1.
- **Nullable on purpose.** Non-pharmacy service items have no stock
  counterpart and must stay null; so must any pharmacy item imported
  without a stock row, which then becomes *visible* as a null rather than
  being silently inferred by a string join.
- **The index stops the collision case** — two service items cannot claim
  the same stock item.
- `SetPharmacyItemPriceService` sets it at creation; the reporting query
  joins `ON si.stock_item_id = it.id` and drops the `code` join entirely.

**Interim, if the column is not wanted yet:** keep the `code` join but add
a reconciliation check to the admin catalogue screen that counts pharmacy
service items with no stock match and stock items with no billing match.
Both numbers are 0 today, so anything above 0 is drift that appeared after
this audit — detection without a schema change.

## Until then

`TopServiceItemLine.strength` / `.form` are **null when no stock item
matches the billing code**, never "this medicine has no strength". The UI
renders that as `—`. Pharmacy names in this catalogue already embed the
strength (`Fluconazole 200mg`, `Chlorpromazine 25mg inj`), so the billed
name — which comes from `service_item` and is the figure money is attached
to — remains authoritative on its own.
