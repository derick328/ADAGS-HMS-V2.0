# ADAGS specification index

Read `/CLAUDE.md` first — it holds the invariants that override anything here.
When a module spec and `CLAUDE.md` disagree, `CLAUDE.md` wins.

---

## Read first

| File | What it answers |
|---|---|
| `00-problem-register.md` | Every design problem raised, with its resolution. **Start here for context on why a rule exists.** §14 lists three decisions that carry residual risk |
| `01-architecture.md` | Module boundaries, encounter state machine, the permission model, deployment |
| `02-billing-architecture.md` | The billing gate, charge origination, emergency bypass, invoice lifecycle. §12 lists what was still open |
| `03-billing-addendum.md` | Payment allocation, result delivery, procedure gating, ward nurse billing consequences |
| `21-multi-facility.md` | What carries `facility_id` and what does not. Needed from phase 0 |

---

## By role

| File | Role | Notes |
|---|---|---|
| `05-receptionist.md` | `RECEPTIONIST` | Field-level intake spec with 13 flagged changes |
| `05a-receptionist-decisions.md` | | Resolved decisions superseding §11 of the above |
| `06-triage.md` | `TRIAGE_NURSE` | v0.3. §7 lists raised-but-not-adopted items |
| `07-doctor.md` | `DOCTOR`, `SPECIALIST_DOCTOR` | v0.3. ICD-10 coding, allergy reconciliation, disposition |
| `08-doctor-navigation.md` | | Sidebar, patient views, catalogue, consultation tabs |
| `09-doctor-incomplete-consultations.md` | | 12-hour recovery prompt and abandon path |
| `12-pharmacist.md` | `PHARMACIST` | v1.0 consolidated. §1 is the decisions register |
| `15-lab-technician.md` | `LAB_TECHNICIAN` | Accession, generated result forms, critical alerting |
| `16-lab-verification-model.md` | | v0.2 — release on entry, verify retrospectively |
| `17-ward-nurse.md` | `WARD_NURSE`, `NURSE` | §2 holds three reversals of earlier decisions |
| `18-admin.md` | `ADMIN` | v0.3. §1 is the resolutions register |
| `11-clinical-lead-role.md` | `CLINICAL_LEAD` | Additive governance role |

---

## Cross-cutting

| File | Covers |
|---|---|
| `04-procedure-bedday-rules.md` | Procedure item reconciliation, the 10:00 bed-day rule, discharge billing stop |
| `10-clinical-reference-data.md` | ICD-10, synonyms, common lists, the shared picker component |
| `13-controlled-medicines-policy.md` | Fail-closed policy for controlled drugs |
| `14-controlled-medicines-tanzania.md` | TMDA regulatory reference. **Corrects assumptions in 13** — read both |
| `19-work-shifts-roster.md` | Shift definitions, roster, and its consumers |
| `20-emergency-billing-bar.md` | Receptionist's view of bypassed payment debt |

---

## Version notes

Where a module went through several drafts, **only the latest is here.**
Superseded drafts were deliberately excluded so no outdated rule can be read
as current.

| Module | Version present |
|---|---|
| Triage | v0.3 |
| Doctor | v0.3 |
| Pharmacist | v1.0 (consolidated) |
| Admin | v0.3 |
| Lab verification | v0.2 |
| Receptionist | v0.1 + the v0.2 decisions file |

Two files intentionally disagree in places: `13-controlled-medicines-policy.md`
was written before the TMDA regulations were consulted, and
`14-controlled-medicines-tanzania.md` corrects it. §7 of file 14 lists exactly
what changed. **File 14 wins.**

---

## Notation used in the specs

| Mark | Meaning |
|---|---|
| `⚠` | A change from what was originally described, with the reasoning |
| `✗` | Conflicts with a previously locked decision — resolved in that section |
| `?Qn` | Open question. Check `00-problem-register.md` §13 for current status |
| ✅ ⏳ | In the problem register: resolved / still open |

---

## Build order

Phases in `01-architecture.md` §12, refined in the kickoff document.

```
0  Foundation      permissions, scopes, audit, money, numbering
1  Front office    patient MPI, encounters, queues
2  Clinical core   triage, consultation, ICD-10, orders
3  Billing gate    gate, allocation, cashier shift, emergency bypass
4  Diagnostics     lab catalogue, accession, results, critical alerting
5  Pharmacy        formulary, FEFO dispensing, stock ledger
6  Inpatient       wards, beds, MAR, bed-day accrual, handover
7  Back office     expenses, payroll, receivables, claims, analytics
```

Phase 0 is not optional and not fast. Everything after it depends on the
permission model.
