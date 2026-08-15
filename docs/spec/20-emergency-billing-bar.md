# HMS — Emergency Billing Bar (Receptionist)

**Version:** 0.1
**Module:** `billing` · surfaced in the receptionist workspace
**Related:** triage `IMMEDIATE` priority, `encounter.type = EMERGENCY`, the billing gate

---

## 1. Purpose

When triage marks a patient `IMMEDIATE`, the payment gate is bypassed and care proceeds. Charges still post — they simply do not block. The emergency bar is the only place that debt is visible.

Without it, bypassed charges are indistinguishable from ordinary unpaid invoices in a long list, and the hospital loses the money quietly. The bar exists so that bypassed debt is impossible to overlook, not so that anyone is chased for it.

---

## 2. Membership rule

⚠ Membership is **not** "priority == IMMEDIATE". It is:

```
encounter has an active PaymentBypass
  AND sum(unsettled patient_portion on lines posted under that bypass) > 0
```

Today the only bypass source is `TRIAGE_IMMEDIATE`, so the two definitions produce the same list. They diverge the moment a second bypass route exists — a clinician authorising a scan before payment, an admin waiver — and a priority-label filter would leave that debt invisible. Filtering on the bypass itself is the same list today and correct forever.

Rows disappear when the balance reaches zero or ownership transfers (§7). They are never dismissed manually.

---

## 3. Placement and behaviour

**Not a sidebar item.** A persistent bar pinned to the top of the receptionist workspace, visible on every screen including Patient Intake and Patient Records. Emergency debt should never require navigation to find.

```
┌──────────────────────────────────────────────────────────────┐
│ ⚠ EMERGENCY — 2 patients · 87,000/= outstanding      [expand]│
└──────────────────────────────────────────────────────────────┘
```

- **Hidden entirely when empty.** A bar that is always present becomes wallpaper within a week, and the day it matters nobody sees it.
- Collapsed by default; expansion state persists per user per session.
- Amber, not red. Red is reserved for clinical alerts, and colliding the two trains staff to discount both.
- Updates live over WebSocket on `PaymentBypassGranted`, `ChargePosted`, `PaymentRecorded`, `EncounterClosed`.

---

## 4. Row anatomy

```
┌──────────────────────────────────────────────────────────────┐
│ Amina Hassan · 34F · HN-2026-04412                           │
│ Emergency declared 14:22 (38 min ago) by N. Kimaro           │
│ Currently: Resuscitation Room · Dr. Mushi                    │
│                                                              │
│ Accrued            47,000/=                                  │
│ Paid                    0/=                                  │
│ Outstanding        47,000/=            [breakdown ▾]         │
│                                                              │
│ NHIF · unverified                      [ Verify policy ]     │
│                                        [ Take payment ]      │
│                                        [ Print statement ]   │
└──────────────────────────────────────────────────────────────┘
```

| Element | Source |
|---|---|
| Patient identity | `Patient` |
| Declared at / by | `PaymentBypass.granted_at`, `granted_by` |
| Currently | `encounter.state` + assigned provider + location |
| Accrued | Sum of lines posted under the bypass, gross |
| Outstanding | Unsettled patient portion only — insurer portion is a claim, not a debt at the desk |

The **Currently** line is the most useful field on the row and has nothing to do with money. Relatives arrive at the desk asking where someone is, and this is the answer.

### 4.1 Breakdown

Expands to the itemised lines with timestamps:

```
14:24  Consultation — emergency          10,000/=
14:41  Full blood count                   8,000/=
14:41  Malaria RDT                        5,000/=
14:52  IV fluids, cannula set            12,000/=
15:03  Ceftriaxone 1g × 2                12,000/=
                                  ─────────────
                                         47,000/=
```

Itemisation matters because the family will ask, and "47,000" with no explanation is how disputes start.

---

## 5. Live accrual

The balance is **not** a fixed consultation fee. A critical patient accrues consultation, investigations, consumables, drugs, and possibly a bed charge over hours.

Every `ChargePosted` event on an encounter with an active bypass updates the row and the bar total in place. Do not model this as a one-off 10,000/= entry — it will understate real exposure by an order of magnitude on the cases that matter most.

---

## 6. Actions

### 6.1 Available to the receptionist

| Action | Notes |
|---|---|
| Take payment | Full or partial, any method, against the open cashier shift. Partial is normal and expected |
| Print statement | Itemised, for family or for an insurer |
| Verify insurance | Attaches or verifies a policy; recomputes the insurer/patient split. Insurer portion becomes a claim, patient portion stays on the row |
| Record family contact | Free-text note: who was informed, when |

### 6.2 Withheld

| Action | Why |
|---|---|
| Write off | Accountant, with admin approval. Two clicks to erase revenue is not a control |
| Dismiss / hide row | The row leaves on settlement or transfer only (§7) |
| Revoke the bypass | Clinical decision, not a billing one — see §8 |
| Block discharge | See §9 |

---

## 7. Lifecycle — how a row leaves

| Condition | Result |
|---|---|
| Outstanding reaches zero | Row leaves; invoice follows the ordinary settled path |
| Insurance covers the full balance | Row leaves; a claim exists in the insurance module |
| Written off by accountant + admin | Row leaves; write-off appears in the leakage report |
| **Encounter closes with a balance** | Row leaves the bar and becomes an **accountant receivable** — see below |

⚠ The last case is the one to get right. A patient discharged with 47,000/= outstanding must not simply vanish from every screen. On `EncounterClosed` with a non-zero bypassed balance:

- The invoice transitions to `RECEIVABLE_POST_DISCHARGE`
- It appears on the accountant's aging report, not the receptionist's bar
- The patient record carries a visible-to-staff note that a prior emergency balance exists

The bar is a live-visit tool. Once the visit ends, the debt is an accounting matter and belongs with the people whose job that is.

---

## 8. Manual bypass (clinician-authorised)

Triage `IMMEDIATE` is automatic. A second route is worth having and needs tighter control: a doctor decides a CT or a transfusion cannot wait for the cashier on a patient who was not triaged `IMMEDIATE`.

```
PaymentBypass.source ∈
    TRIAGE_IMMEDIATE       -- automatic, triage nurse
    CLINICIAN_AUTHORISED   -- requires a clinician's credential + reason
    ADMIN_WAIVER           -- charity, staff benefit, ministry directive
```

⚠ The receptionist must **not** be able to grant a bypass alone. That would be an unaudited hole through the only revenue gate in the system. They can initiate the request; a clinician's authorisation completes it, and the authorising user is recorded on the row.

**Scope of a bypass is the whole encounter, not one charge.** A one-off consultation waiver leaves the same critical patient hitting a payment wall at the lab and again at pharmacy.

**Ending a bypass:** once the patient is stabilised, the attending doctor can revoke it. Charges posted after revocation gate normally; charges already posted stay unblocked. The row remains in the bar until the existing balance clears.

---

## 9. Constraints that must not be violated

Two rules to write into the spec so nobody adds a "helpful" check later.

### 9.1 Never block discharge on an emergency balance

Holding a patient until a bypassed debt is settled is detention for debt — unlawful in most jurisdictions and indefensible everywhere. Post-discharge balances become receivables (§7). There must be no code path where an outstanding emergency balance prevents an `EncounterClosed` transition.

### 9.2 No payment pressure during active critical care

While the encounter is in a critical state — `IN_RESUSCITATION`, `IN_THEATRE`, or `IMMEDIATE` priority not yet stabilised — the row displays the balance but the `Take payment` action is **not prompted**. It remains available if a family member voluntarily presents to pay; it must not surface a nudge, a countdown, or a notification.

A growing amber number beside a patient in resuscitation must never become a prompt for anyone to find the family in a corridor. The bar exists to prevent the hospital losing money, not to accelerate collection from people whose relative is being resuscitated.

---

## 10. Data model

```
PaymentBypass(
  id, encounter_id,
  source,                     -- §8
  reason_code, reason_text,
  granted_by_user_id, granted_at,
  authorised_by_user_id,      -- null for TRIAGE_IMMEDIATE
  revoked_by_user_id, revoked_at, revoke_reason
)

InvoiceLine(
  ...,
  posted_under_bypass_id → PaymentBypass   -- nullable
)
```

`posted_under_bypass_id` is what makes the distinction precise. It separates "charged during the emergency and unblocked" from "charged afterwards and simply unpaid", which the leakage report in §12 depends on.

An encounter has an active bypass when a `PaymentBypass` row exists with `revoked_at IS NULL`.

---

## 11. Events

**Emitted:** `PaymentBypassGranted` · `PaymentBypassRevoked` · `EmergencyBalanceSettled` · `EmergencyBalanceTransferredToReceivable`

**Consumed:** `ChargePosted` (recompute accrual) · `PaymentRecorded` (recompute outstanding) · `EncounterStateChanged` (update the Currently line) · `EncounterClosed` (transfer per §7)

---

## 12. Governance

The bypass is the only route around the payment gate, which makes it the most attractive target for misuse in the system. Two monthly reports, both to admin and the accountant:

**Emergency bypass leakage**
Total bypassed, recovered, written off, still receivable — by month and by department. This is the number that tells management whether the mechanism is working or being drained.

**Declaration rates**
`IMMEDIATE` declarations per triage nurse, and bypass value per nurse, normalised against patients triaged. An outlier is a conversation, not an automated block — a nurse with a high rate may be working the busiest shift, or may be doing favours. Only a human can tell which.

Add `CLINICIAN_AUTHORISED` bypasses per doctor to the same report once §8 is built.

---

## 13. Open

| | Question |
|---|---|
| `?Q1` | Patient dies during an emergency encounter with a balance — written off automatically, or pursued with the estate? Needs a stated policy; the default should almost certainly be automatic write-off |
| `?Q2` | Does a prior unsettled emergency balance show as a warning to the receptionist on a future visit? Visible-to-staff is reasonable; using it to refuse care is a management decision that should be explicit |
| `?Q3` | Is `CLINICIAN_AUTHORISED` bypass (§8) in v1, or is `TRIAGE_IMMEDIATE` the only route initially? |
| `?Q4` | Who owns the receivable after discharge — is there a follow-up or collections process, or does it age until written off? |
