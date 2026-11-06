# Pharmacy stock and packaging implementation audit

## Existing implementation

- `item.unit` is the catalogue product's stock unit. Prescription quantities,
  stock movements, balances, and dispense quantities are integers in that unit.
- One restock request currently creates one batch, receipt movement, and
  balance update. A batch stores supplier, delivery note, expiry, and integer
  cost per stock unit. There is no purchase order or receipt header.
- Dispensing already checks the live billing gate, controlled schedule, FEFO,
  batch expiry, partial quantity, idempotency, and nonnegative stock through
  `InventoryFacade`. The doctor enters an unqualified integer quantity.
- Suppliers, stores, item proposals, price lists, stock reports, and scoped
  role assignments exist. The stock controller currently checks unscoped
  permissions and current stock reports include nonusable batches.

## Invariants and compatibility

- Keep `item.unit` as the sole base inventory unit and preserve the meaning of
  all existing integer quantities. Historical prescriptions need an explicit
  base-unit snapshot when new requested-unit fields are introduced.
- Keep movement rows append-only and balances materialized in the same
  transaction. Keep the existing FEFO, expiry, billing, allergy, controlled
  medicine, and nonnegative-balance checks.
- Do not change approved selling prices when purchase costs change.
- Scope stock reads and writes to stores in facilities the actor may access.

## Database plan

1. Add per-item packaging definitions with immutable versions and exact
   positive integer conversion factors; approve new versions before use.
2. Add receipt headers and lines with idempotency key, package conversion and
   cost snapshots, and accepted/rejected/missing quantities. Link new batches
   to receipt lines without rewriting historical batches.
3. Add prescription requested quantity/unit/factor snapshots while retaining
   `quantity_prescribed` as the base quantity. Add adjustment records and
   compensating movements for corrections.
4. Add exact receipt value allocation data so valuation and cost of goods sold
   reconcile when a package price does not divide evenly by its base units.

## Implemented conversion and cost rule

Packaging is a flat, item-specific, approved integer factor. A new approved
version supersedes the prior factor; receipts, prescriptions and dispense lines
save their selected unit and factor. Existing integer quantities retain their
base-unit meaning. For a receipt with total accepted cost `V` and `Q` accepted
base units, `V / Q` is allocated to every unit, and `V % Q` units receive one
additional minor unit of cost. Separate cost tranches are linked to the same
receipt line. Thus the sum of batch values equals the exact package purchase
value, and dispensing from a tranche records its actual integer cost.

`quantity` in current stock remains physical base units for compatibility;
`availableQuantity` is zero for nonactive or expired batches. Low-stock totals
use only active, unexpired batches. FEFO suggestions and dispense selection are
limited to the prescription facility.

The original single-batch restock endpoint remains for existing clients. New
multi-line receiving uses the receipt endpoint and its idempotency key.

## Implementation sequence

Packaging and migration; atomic multi-line receiving and scoped APIs; receipt
history and stock reports; prescription and dispense unit presentation;
adjustment workflow; React forms and tests. Old restock and historical rows
remain readable until all callers use the new receipt flow.
