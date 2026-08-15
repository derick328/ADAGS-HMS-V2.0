# HMS — Work Shifts & Roster

**Version:** 0.1
**Owner:** `ADMIN` (definitions) · department heads or `CLINICAL_LEAD` (rostering)
**Consumed by:** doctor incomplete-consultation prompt · ward nurse handover · on-call emergency authorisation · triage staffing · lab turnaround

---

## 1. Two different things called "shift"

⚠ Name these distinctly or they will be confused in code and conversation.

| | `WorkShift` | `CashierShift` |
|---|---|---|
| Purpose | Who is on duty, when | Cash reconciliation session |
| Created by | Admin, as a recurring definition | The cashier, by opening it |
| Ends | At the defined time | When counted and reconciled |
| Belongs to | Roster / HR | Billing |
| Existing spec | This document | Receptionist §7.1 |

A receptionist can have a `CashierShift` that does not align with any `WorkShift`. They are unrelated objects.

---

## 2. Shift definitions

Admin defines the patterns once; they recur.

```
WorkShift(
  id, code, name,
  department_id,          -- NULL = hospital-wide
  start_time,             -- time of day, no date
  end_time,
  crosses_midnight BOOLEAN,
  days_of_week,           -- bitmask or array; some shifts are weekday-only
  is_active,
  created_by, created_at
)
```

Typical set:

```
MORNING    07:00 – 15:00
EVENING    15:00 – 23:00
NIGHT      23:00 – 07:00     crosses_midnight = true
DAY_LONG   08:00 – 20:00     twelve-hour pattern
ON_CALL    17:00 – 08:00     crosses_midnight = true
```

⚠ **`crosses_midnight` is not optional.** A night shift starting 23:00 and ending 07:00 has `end_time < start_time`, and every date calculation — is this nurse on duty now, which shift does this handover belong to, when does the prompt fire — breaks silently without the flag.

⚠ **Times are stored as time-of-day in facility-local time**, and resolved against a date when needed. Storing UTC timestamps for a recurring pattern makes daylight-invariant reasoning harder, and East Africa Time has no DST, so local is unambiguous here.

---

## 3. Roster

```
ShiftAssignment(
  id, user_id, work_shift_id,
  shift_date,             -- the calendar date the shift starts
  ward_id,                -- NULL for non-ward roles
  department_id,
  role_context,           -- WARD_NURSE | DOCTOR | TRIAGE_NURSE | ON_CALL_LEAD
  status,                 -- SCHEDULED | ACTIVE | COMPLETED | ABSENT | SWAPPED
  assigned_by, assigned_at,
  swapped_with_user_id, swap_reason
)
```

`shift_date` anchors a midnight-crossing shift: the night shift of 12 July runs 12 July 23:00 to 13 July 07:00 and is filed under 12 July.

Rostering is delegated — admin defines the shift patterns, department heads or the clinical lead assign people to them. `ROSTER_MANAGE` scoped to a department.

`?Q1` — is rostering entered in this system, or does the hospital already keep it elsewhere? If elsewhere, a lighter option is recording only who is currently on duty rather than a full schedule.

---

## 4. Consumers

### 4.1 Doctor — end-of-shift consultation prompt

The reason this model is being built now.

```
30 minutes before shift end (configurable)
   → if the doctor has incomplete consultations, prompt appears
   → same content as the login prompt
   → dismissible; suppressed entirely if an IMMEDIATE patient is queued
```

⚠ Catching this at shift end is materially better than next login — the doctor is still in the building and still remembers the patient. Next-morning completion is reconstruction.

**Fallback when not rostered.** Many doctors will not be on a rigid shift, especially in outpatients. If a doctor has no `ShiftAssignment` for today, only the login prompt applies. The feature degrades rather than failing.

### 4.2 Ward nurse — shift handover

Ward nurse §7 needs shift boundaries to exist at all.

```
Shift end approaching
   → outgoing nurse prompted to record handover per assigned patient
   → pre-populated with alerting vitals, overdue MAR slots,
     pending results, orders added this shift
   → incoming nurse (from the roster) acknowledges
   → unacknowledged handovers flagged
```

The roster is what identifies the incoming nurse. Without it, handover has no recipient.

### 4.3 On-call emergency authorisation

Resolves `CLINICAL_LEAD ?Q3`. `EMERGENCY_DECLARE_AUTHORISE` for `URGENT` and below needs a holder at 02:00.

```
ShiftAssignment(role_context = ON_CALL_LEAD)
   → the triage nurse's screen names who to call
   → the authorisation is recorded against that user
```

⚠ If nobody is rostered on call, the system says so plainly rather than showing an empty authoriser field. A named gap is actionable; a blank field is not.

### 4.4 Other

| Consumer | Use |
|---|---|
| Triage | Which nurses are on duty, for queue staffing |
| Lab | Whether a verifier is on shift (verification model §3.1) |
| Referral picker | Doctor availability — replaces the placeholder duty roster in triage §6.2 |
| Governance reports | Normalising per-nurse and per-doctor metrics by shifts worked, so a nurse on the busiest rota is not flagged as an outlier |

The referral picker is worth noting: triage v0.3 §5.1 shows doctors as "on leave until 12 Aug" and greys off-duty providers. That was written against a placeholder. This is the real table behind it.

---

## 5. Admin screens

```
Shift Definitions
  ├─ patterns, times, days, department scope
  └─ crosses-midnight flag

Roster
  ├─ week view by department or ward
  ├─ assign, swap, mark absent
  └─ gaps highlighted — unstaffed shifts, no on-call lead

Settings
  ├─ end-of-shift prompt lead time      default 30 min
  ├─ handover reminder lead time        default 30 min
  └─ unacknowledged handover escalation default 60 min
```

⚠ **Gap highlighting is the most useful part of the roster screen.** A night with no rostered on-call lead means the emergency authorisation path has no holder, and that should be visible before the night rather than discovered during it.

---

## 6. Data model additions

```
WorkShift(...)             -- §2
ShiftAssignment(...)       -- §3

ProtocolSetting keys:
  shift.end_prompt_lead_minutes           default 30
  shift.handover_reminder_lead_minutes    default 30
  shift.handover_escalation_minutes       default 60
```

Existing tables gain nothing — the roster is referenced, not denormalised into admissions or encounters. Care responsibility is *derived* from the roster rather than stored on the record, which is what ward nurse §8.2 requires.

---

## 7. Open

| | Question |
|---|---|
| `?Q1` | Full rostering in this system, or only "who is on duty now"? |
| `?Q2` | Who rosters — admin centrally, or department heads and the clinical lead per department? |
| `?Q3` | Are doctors rostered at all, or only nurses and on-call? Determines how often the §4.1 fallback applies |
| `?Q4` | Should shift swaps require approval, or is self-service with a record sufficient? |
| `?Q5` | Does absence need a reason, and does it connect to anything else (leave balance, payroll)? Probably out of scope — but if leave is tracked elsewhere, the referral picker's "on leave" status needs a source |
