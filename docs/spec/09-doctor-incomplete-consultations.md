# HMS — Doctor Module: Incomplete Consultation Recovery

**Version:** 0.5 delta on `hms-doctor-module-v0.3.md` and `hms-doctor-navigation-ui.md`
**Feature:** on login, a doctor with consultations left incomplete beyond 12 hours is shown the list and required to finish them

---

## 1. Why it matters

An incomplete consultation is two problems at once:

- **Clinical** — the patient was seen, examined, and treated, and none of it is recorded. The next clinician has nothing to read
- **Financial** — the patient paid the consultation fee for a service with no record. If anyone audits, that charge is unsupported

Both worsen with time. At 12 hours the doctor still remembers the patient; at five days they do not, and whatever gets written then is reconstruction.

---

## 2. What counts as incomplete

⚠ **The critical distinction.** Two states look similar and mean opposite things:

| State | Meaning | On the list? |
|---|---|---|
| `ConsultationRecord.status = OPEN` | Started, never finalized | **Yes** |
| Encounter `AWAITING_RESULTS`, note `FINALIZED` | Note complete, waiting on labs | **No** |
| Encounter `IN_CONSULT`, no record created | Opened and abandoned | **Yes** |
| `FINALIZED` with `final_diagnosis` null pending labs | Correct and expected | **No** |

A blood culture takes five days. If `AWAITING_RESULTS` encounters appear as incomplete, the doctor gets a list they cannot clear — and within a week they will dismiss the popup unread, which kills the feature.

```sql
incomplete = consultation_record.status = 'OPEN'
         AND consultation_record.author_id = :doctor
         AND consultation_record.created_at < now() - interval '12 hours'
         AND encounter.state NOT IN ('CLOSED', 'CANCELLED_IN_ERROR',
                                     'LEFT_WITHOUT_BEING_SEEN')
```

The 12-hour threshold is configuration (`ProtocolSetting`), not a constant.

---

## 3. The prompt

Shown **once per session on login**, not on every page navigation.

```
┌──────────────────────────────────────────────────────────────┐
│  3 consultations are incomplete                              │
│                                                              │
│  These patients were seen but their records were never       │
│  finalized. Please complete them.                            │
│                                                              │
│  Amina Hassan · HN-2026-04412 · seen 12 Jul 14:22 · 19h ago  │
│  Missing: examination, treatment plan          [ Complete ]  │
│                                                              │
│  Joseph Mwita · HN-2026-03871 · seen 11 Jul 09:15 · 2d ago   │
│  Missing: provisional diagnosis                [ Complete ]  │
│                                                              │
│  Grace Kimaro · HN-2025-11204 · seen 09 Jul 16:40 · 4d ago   │
│  Missing: all fields                           [ Complete ]  │
│                                                              │
│                              [ Later ]   [ Go to first ]     │
└──────────────────────────────────────────────────────────────┘
```

Each row names **what is missing**, not just that something is. A doctor who can see "examination, treatment plan" knows it is two minutes of work; "incomplete" tells them nothing and invites deferral.

`[ Complete ]` opens that consultation directly.

---

## 4. It must not hard-block

⚠ **The single most important constraint.**

If the modal prevents the doctor from working until the backlog is cleared, then the first morning an `IMMEDIATE` patient is waiting, the doctor is doing paperwork while someone deteriorates.

```
Dismissible with [ Later ]              always
Emergency queue access                  never gated
IMMEDIATE patient in queue              modal is suppressed entirely,
                                        badge only
```

What persists instead of a block:

- A badge on **My Queue** and in the header, showing the count, never clearing until the list is empty
- The modal reappears next session
- Escalation over time (§6)

⚠ If an `IMMEDIATE` patient is in this doctor's queue at login, the modal does not appear at all. The badge is enough; a critical patient outranks completing yesterday's notes.

---

## 5. Completion requirements

### 5.1 "All fields" means all *required* fields

⚠ Reconciling with v0.3 §4.4: `final_diagnosis` is legitimately nullable when investigations are pending. Requiring literally every field would mean a doctor with an outstanding culture cannot finalize — creating the backlog this feature exists to clear.

**Required to finalize:**

```
history_of_presenting_illness      *
physical_examination               *
provisional_diagnosis              *   (ICD-10 coded)
treatment_plan                     *
disposition (nextStep)             *
```

**Not required:**

```
past_medical_history               optional
past_surgical_history              optional
family_social_history              optional
clinical_notes                     optional
final_diagnosis                    nullable when results are pending
```

### 5.2 Stricter validation on recovered consultations

⚠ For a consultation being completed from this list — hours or days after the patient was seen — one addition:

```
retrospective_completion = true
retrospective_note       required, min 20 chars
```

The note states why it is being completed late and what it is based on (memory, paper notes, the nurse's record). A consultation written from memory four days later is a different evidential object from one written at the bedside, and the record should say so.

This is not punitive. It protects the doctor as much as the patient — an honestly-marked retrospective entry is defensible; an undated one that appears contemporaneous is not.

---

## 6. Escalation timeline

⚠ This feature overlaps the draft sweep in v0.3 §10. Two competing timelines would mean the doctor is chased to complete something the system closes by itself. One timeline:

| Elapsed | Action |
|---|---|
| 12 hours | Appears on the login prompt and the badge |
| 48 hours | `CLINICAL_LEAD` sees it on the governance queue |
| 7 days | Auto-closed `CLOSED_ADMINISTRATIVELY`, reason recorded, consultation charge flagged for review |

All three thresholds are configuration.

⚠ **Auto-closure is not absolution.** A `CLOSED_ADMINISTRATIVELY` encounter with a paid consultation and no clinical record appears on a monthly report to the clinical lead and the accountant. It is a recorded failure, not a tidy-up.

---

## 7. The abandon path

⚠ **Without an exit, the list only grows.** A doctor cannot complete a consultation on a patient who went home three days ago — there is nobody to examine and nothing to add.

```
[ Cannot complete ] → reason required:

  PATIENT_DID_NOT_RETURN     patient left before the consultation finished
  LEFT_BEFORE_COMPLETION     walked out mid-consultation
  RECORDED_IN_ERROR          opened on the wrong patient
  DUPLICATE                  a second record for the same encounter
  TRANSFERRED_TO_COLLEAGUE   another doctor completed it
```

Consequences:

```
→ ConsultationRecord.status = ABANDONED, reason recorded
→ Encounter closes to the matching terminal state
→ Consultation charge:
     RECORDED_IN_ERROR / DUPLICATE  → voided automatically
     PATIENT_DID_NOT_RETURN         → ?Q1 — refund, retain, or credit
→ Removed from the list
→ Abandonments appear on the CLINICAL_LEAD governance report
```

⚠ Abandoning is a recorded clinical decision with a reason, not a delete. The encounter, the triage record, and the charge history all remain.

---

## 8. Governance

An incomplete-consultation rate is a real supervision signal. Monthly to `CLINICAL_LEAD`:

```
Incomplete consultations — Internal Medicine, July

  Dr. J. Mushi      4 incomplete / 218 consultations    1.8%
  Dr. A. Kessy      2 incomplete / 187 consultations    1.1%
  Dr. S. Mally     31 incomplete / 203 consultations   15.3%  ⚠
  Abandoned (all reasons)                                  12
  Auto-closed at 7 days                                     3
```

An outlier is a conversation — a doctor covering two clinics and a doctor avoiding documentation produce the same number, and only a person can tell which.

⚠ Per the privacy constraint on `CLINICAL_LEAD`, this report is **aggregate**. Opening an individual record requires a care relationship or break-glass.

---

## 9. Data model

```
ConsultationRecord(
  ...,
  status,                        -- OPEN | FINALIZED | ABANDONED
  retrospective_completion  BOOLEAN NOT NULL DEFAULT false,
  retrospective_note        TEXT NULL,
  completed_at              TIMESTAMPTZ NULL,
  abandoned_reason          TEXT NULL,
  abandoned_at              TIMESTAMPTZ NULL,
  abandoned_by              BIGINT NULL
)

ProtocolSetting  keys:
  consultation.incomplete_prompt_hours     default 12
  consultation.escalate_to_lead_hours      default 48
  consultation.auto_close_days             default 7
```

`completed_at` distinct from `created_at` is what makes §5.2 and §8 measurable — the gap between them *is* the metric.

---

## 10. Interaction with existing behaviour

| Existing | Interaction |
|---|---|
| Autosave every 20s (v0.3 §10) | Unchanged. A recovered consultation opens with whatever was autosaved |
| Draft sweep (v0.3 §10) | **Replaced** by the §6 timeline — one mechanism, not two |
| Doctor queue ordering | Unchanged. Incomplete consultations do not enter the live queue |
| `My Patients → Active` | Incomplete consultations appear here too, flagged |
| Encounter state | Stays as it was. This feature completes the *record*, not the encounter |

---

## 11. Open

| | Question |
|---|---|
| `?Q1` | `PATIENT_DID_NOT_RETURN` — is the consultation charge refunded, retained, or converted to a credit? The doctor did the work; the record is incomplete |
| `?Q2` | Should a doctor be able to complete a *colleague's* incomplete consultation, or only mark it `TRANSFERRED_TO_COLLEAGUE`? |
| `?Q3` | Do the 12/48/7 thresholds differ by encounter type? An emergency consultation left incomplete is arguably more urgent than a routine one |
| `?Q4` | Should the prompt appear at shift end as well as at login? Catching it before the doctor leaves is more effective than catching it the next morning |
