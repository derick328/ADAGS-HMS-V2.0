# Frontend conventions

React 18, TypeScript, Vite, TanStack Query.
Root `CLAUDE.md` holds the domain invariants and design tokens — not repeated here.

---

## Structure

```
src/
  workspaces/
    admin/          reception/      triage/        doctor/
    pharmacy/       lab/            ward/          accounts/
  shared/
    components/     design system primitives
    api/            GENERATED from api/openapi.yaml — never hand-edit
    auth/           usePermission, useScope, session
    format/         money, dates, patient names
  App.tsx
```

**One workspace shell per role, lazy-loaded.** Not one dashboard with forty
conditional blocks. A receptionist should never download the ward nurse's code.

---

## Rules

### API types are generated, never written

```
backend build → api/openapi.yaml → openapi-typescript → src/shared/api/types.ts
```

Never hand-write a request or response type. CI fails if the generated file is
stale. This is the only thing stopping the two folders drifting apart.

### Permissions

```tsx
const canApprove = usePermission('INVOICE_CANCEL_APPROVE');
{canApprove && <button>Approve</button>}
```

- Never check a role name.
- **UI permission checks hide controls; they are not security.** Every one is
  duplicated server-side. Assume the user can call the endpoint directly.

### Server state

- Queue and worklist screens are **server state** — TanStack Query, not
  `useState`.
- Queues subscribe over WebSocket. A receptionist taking payment must appear on
  the doctor's queue without a refresh.
- Invalidate on the events the backend emits, not on a timer.

### Money and dates

- Money arrives as minor units. Format with `Intl.NumberFormat`, never divide
  by 100 by hand in a component.
- Display in facility-local time. The server sends UTC.
- Tanzanian shilling: `12,000/=` is the local convention. Follow it.

### Forms

- react-hook-form + zod, schemas derived from the generated API types.
- Validation messages name what to do, not what went wrong:
  "Enter a phone number", not "Phone is required".

---

## Design system

**`src/shared/styles/tokens.css` is the single source of tokens.** Import it
once globally. Never define a token elsewhere, never write a raw hex in a
component.

`src/shared/components/QueueShell.tsx` is the reference implementation — match
its conventions.

- `--green-signal` (#7FFF00) is a **signal**, not a surface: active nav, badges,
  focus rings, logo mark. Never a page background, never behind body text,
  never with white text on it (1.3:1).
- Buttons that need white text use `--green-600`.
- Alert colours (red, amber) must stay visually distinct from brand green.
- Sentence case everywhere. Never Title Case, never ALL CAPS.
- Every count badge is clickable and leads to the filtered list. A number a
  user cannot click is a dead end.

### Screens that carry clinical risk

These have specific requirements from the specs, not stylistic preferences:

| Screen | Requirement |
|---|---|
| Prescribing | Allergy records **inline, adjacent to the drug field**, warning-styled when non-empty. Never collapsed, never a tab |
| Dispensing | Same allergy display. Batch, expiry, and stock shown per item |
| Triage | `IMMEDIATE` confirmation requires a **reason selection**, not an OK — plain dialogs attract click-through blindness |
| Any queue | `IMMEDIATE` patients always first, visually distinct by icon and position, not colour alone |
| Lab results | Critical values are pushed and acknowledged, never only listed |
| Referral picker | The **fee** appears on each provider card — the nurse is choosing between 10,000/= and 40,000/= |
| Order tabs | Running total on the tab bar, so the doctor sees what they are committing the patient to |

### Accessibility

- 4.5:1 minimum on body text. The green palette makes this easy to get wrong.
- Never colour alone for status — pair with an icon or a label.
- Keyboard navigable throughout. Ward and lab staff use keyboards, not mice.
- Focus visible on every interactive element.

---

## Things that are always wrong here

```tsx
if (user.role === 'DOCTOR')                    // role name — use usePermission
interface Patient { id: number; ... }          // hand-written API type
<div style={{ color: '#7FFF00' }}>             // raw hex, and unreadable
<button style={{ background: '#7FFF00',
                 color: 'white' }}>            // 1.3:1
{(amount / 100).toFixed(2)}                    // format with Intl
<Allergies collapsed />                        // on a prescribing screen
```
