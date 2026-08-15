# HMS — Lab Result Verification Model v0.2

**Supersedes:** `hms-lab-verification-model.md` v0.1
**Change:** results release to the clinician on entry. Verification is retained as a separate, later, optional act — performed when a verifying role exists.

---

## 1. The decision

```
Result entered  →  RELEASED IMMEDIATELY, clinician sees it
                →  verification record created, PENDING
                →  verified later by whoever holds the permission,
                   if and when that role exists
```

No result is ever held waiting for verification. Provisional release from v0.1 §4 is removed — it was solving a problem that no longer exists, since nothing is withheld.

---

## 2. Release and verification are independent axes

⚠ **The main structural change.** v0.1 modelled these as stages in one chain. They are two separate facts about a result, and treating them as one status forces awkward states.

```
LabResult(
  id, lab_order_id, specimen_id,

  -- release axis: always set, at entry
  entered_by            NOT NULL,
  entered_at            NOT NULL,
  released_at           NOT NULL,        -- = entered_at in practice

  -- verification axis: independent, may never be set
  verification_state    NOT NULL,        -- see below
  verified_by           NULL,
  verified_at           NULL,
  verification_comment  NULL,
  verification_level_applied  NOT NULL,  -- snapshot at entry
  result_version_hash   NULL,            -- what was approved

  amends_id, amendment_reason,
  version
)
```

```
verification_state ∈
  NOT_REQUIRED   test's level is NONE — no verification expected
  PENDING        awaiting verification, within the window
  VERIFIED       checked, values confirmed
  AMENDED        checked, an error was found and corrected
  LAPSED         window passed unverified — see §4
```

`released_provisionally` from v0.1 is **dropped**. Pending verification is `verification_state = PENDING`; there is no separate boolean.

The constraint from v0.1 §2.1 is unchanged:

```sql
CONSTRAINT verification_actor_valid CHECK (
  verified_by IS NULL
  OR verification_level_applied IN ('NONE', 'SELF')
  OR verified_by != entered_by
)
```

And `verification_level_applied` remains **snapshotted at entry**, not read live — raising a test's level next month must not retroactively invalidate results released under the old rule.

---

## 3. Two purposes, two mechanisms

⚠ A single "verify everything, eventually" queue cannot be emptied. With no verifying role for months, it accumulates tens of thousands of rows, becomes visibly hopeless, and gets ignored — at which point the mechanism is dead regardless of what the schema says.

Retrospective verification serves two distinct purposes, and they need different handling.

### 3.1 Clinical verification — short window, exhaustive

**Purpose:** catch an error while a correction can still change treatment.

```
Window:      configurable, 7–14 days from release
Scope:       every result whose test level is SELF, SECOND_PERSON, or SPECIALIST
Queue:       clearable, sorted critical-first then oldest-first
Past window: verification_state → LAPSED, leaves the queue
```

This queue is emptiable. That is the point — a lab scientist starting on Monday can clear the last fortnight and stay current, rather than facing a year of backlog.

### 3.2 Quality audit — sampled, any age

**Purpose:** measure technician accuracy. Not about fixing individual cases.

```
Scope:     a configured percentage per technician per period
           (e.g. 5% monthly), selected randomly
Age:       any — a six-month-old result is still evidence of accuracy
Output:    per-technician discrepancy rate, to CLINICAL_LEAD
Result:    discrepancies still amend (§5), but the primary product
           is the metric, not the correction
```

Sampling is what makes historical results useful. Verifying all 40,000 is impossible; verifying 300 chosen at random tells you whether the other 39,700 can be trusted.

`?Q1` — sample rate and period at launch.

### 3.3 `LAPSED` is not a failure state

A lapsed result is one nobody checked within the window where checking mattered. That is the expected outcome for most results in a facility with no verifying role, and it should be recorded honestly rather than dressed up.

`LAPSED` results remain eligible for §3.2 sampling. Lapsing removes them from the clinical queue, not from existence.

Report monthly: lapse rate by test and by technician. A rising lapse rate once a verifier exists means the window or the staffing is wrong.

---

## 4. Retention — verification depends on it

⚠ **You cannot verify a result retrospectively without the analyser's original output.** There is nothing to compare the typed values against, so the verification is a signature on an unchecked number.

This makes machine output capture load-bearing rather than optional:

```
LabTest.produces_machine_output = true
    → machine output capture is REQUIRED at result entry
    → stored in the object store (MinIO), key in the database
    → retained for at least the audit sampling horizon
```

Where a test is genuinely manual (microscopy, macroscopic description), there is no machine output and verification rests on the parameters, specimen quality, and delta check instead. Mark those tests explicitly so the absence is intentional rather than a gap.

Everything the verifier needs must survive to verification time:

| Retained | Why |
|---|---|
| Machine output PDF | The primary comparison |
| Entered values, unmodified | What is being checked |
| Specimen quality and notes | Borderline quality changes interpretation |
| Reference ranges as applied | Ranges change; the flag must be re-derivable |
| Entering technician and timestamp | Accountability, and the §3.2 metric |
| Reagent batch links | A faulty lot invalidates the result regardless of the typing |

---

## 5. When verification finds an error

The data correction is the amendment path from v0.1 §5, unchanged: new row, `amends_id`, reason required, original readable, ordering doctor notified, printed reports marked as superseding.

⚠ **But a retrospective error on a result the clinician already acted on is a clinical incident, not just a data correction.**

```
Verification finds a discrepancy
   │
   ├─ result was NOT acted on (no linked prescription, no diagnosis
   │  change, no admission after release)
   │     → amendment + doctor notification. Ends there.
   │
   └─ result WAS acted on
         → amendment + doctor notification
         → ⚠ automatic clinical incident raised to CLINICAL_LEAD
         → incident records: original value, corrected value,
           what was prescribed or decided on it, elapsed time
```

Notifying the ordering doctor handles the data. It does not handle the fact that someone was treated on wrong information, which needs review by someone whose job that is.

"Acted on" is detectable: a prescription, a diagnosis, an admission, or a disposition recorded on the encounter after `released_at`.

`?Q2` — should a discrepancy above a severity threshold (critical-range error, blood group mismatch) escalate immediately rather than through the monthly report?

---

## 6. What the clinician sees

⚠ **No unverified marker in v1.**

If verification is inactive, every result carries the marker, it means nothing, and clinicians learn to ignore it. They are then still ignoring it on the day it starts carrying information.

```
Facility has no active verifying role
    → results display normally, no verification badge

Verification becomes active (permission granted to some role)
    → PENDING and LAPSED results show a badge
    → VERIFIED results show a verified badge with the verifier's name
    → the distinction now means something
```

Drive this from a facility-level setting rather than inferring it, so the transition is deliberate:

```
ProtocolSetting(key='lab.verification_active', value=false)
```

⚠ An **amended** result always shows its amendment notice regardless of this setting. That is not a verification badge — it is a statement that the value changed.

---

## 7. Verification screen

Unchanged from v0.1 §7, and more important now that verification may happen weeks after entry:

| Shown | Why |
|---|---|
| Entered values, computed flags, reference ranges as applied | The primary check |
| Machine output inline | Confirms typed values match the instrument |
| Specimen quality and rejection notes | Context for a borderline result |
| **Patient's previous result for the same parameter** | Delta check — the strongest single signal of sample mix-up |
| Clinical indication from the order | Interpretation context |
| **What was done after release** | ⚠ New — prescriptions, diagnoses, admission. Tells the verifier immediately whether a discrepancy is an incident (§5) |
| Entering technician and timestamp | Accountability |

Recorded on verification: `verified_by`, `verified_at`, `verification_comment`, `result_version_hash`, and delta-check acknowledgement where a warning fired.

---

## 8. Adding the verifying role later

Unchanged from v0.1 §8 — the mechanism is permission-based, so:

```
1. Seed Role(code='LAB_SCIENTIST', is_system=true)
2. Grant LAB_RESULT_VERIFY (+ LAB_RESULT_VERIFY_SPECIALIST if applicable)
3. RoleAssignment(user, LAB_SCIENTIST, scope: DEPARTMENT:LABORATORY)
4. Optionally revoke LAB_RESULT_VERIFY from LAB_TECHNICIAN
5. Set verification_level per test in the catalogue
6. ⚠ Set lab.verification_active = true  (§6)
7. ⚠ Configure the clinical window (§3.1) and audit sample rate (§3.2)
```

No migration, no code change, no backfill. Steps 4 to 7 are policy; 1 to 3 are administration.

A lab scientist doing bench work also holds `LAB_RESULT_ENTER`; the §2 constraint correctly prevents them verifying their own entries while permitting them to verify a colleague's.

---

## 9. Migration now

```sql
ALTER TABLE lab_test
  ADD verification_level    TEXT NOT NULL DEFAULT 'NONE',
  ADD produces_machine_output BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE lab_result
  ADD entered_by            BIGINT NOT NULL,
  ADD entered_at            TIMESTAMPTZ NOT NULL,
  ADD released_at           TIMESTAMPTZ NOT NULL,
  ADD verification_state    TEXT NOT NULL DEFAULT 'NOT_REQUIRED',
  ADD verified_by           BIGINT NULL,
  ADD verified_at           TIMESTAMPTZ NULL,
  ADD verification_comment  TEXT NULL,
  ADD verification_level_applied TEXT NOT NULL DEFAULT 'NONE',
  ADD result_version_hash   TEXT NULL,
  ADD CONSTRAINT verification_actor_valid CHECK (...);
```

With every test at `NONE` and `lab.verification_active = false`, behaviour is identical to the current flow: enter, release, done. No extra clicks, no badges, no queue.

The columns and the retention rule (§4) are the parts that must exist now. The policy can wait indefinitely.

---

## 10. Open

| | Question |
|---|---|
| `?Q1` | Quality audit sample rate and period — 5% monthly per technician? |
| `?Q2` | Should severe discrepancies escalate immediately rather than via the monthly report? |
| `?Q3` | Clinical verification window — 7 days or 14? |
| `?Q4` | Which tests are `produces_machine_output = false` (genuinely manual)? Microscopy and macroscopic description at minimum |
| `?Q5` | Does the ordering doctor get any verification role, or is it strictly a laboratory function? |
| `?Q6` | Are `LAPSED` results eligible for clinical amendment if someone notices later, or only for audit sampling? |
