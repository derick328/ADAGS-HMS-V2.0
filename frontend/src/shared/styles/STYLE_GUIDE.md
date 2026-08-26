# ADAGS style guide

This is the constitution for how ADAGS looks and reads. `tokens.css` is the
single source of truth for values; this document is the single source of
truth for *when* to reach for which one. If a component needs a value that
isn't here, the answer is to extend `tokens.css` and this guide together —
never to write a one-off.

Reference implementation: `frontend/src/shared/components/QueueShell.tsx`.
When in doubt, match its conventions.

---

## 1. Aesthetic principles

ADAGS is read by a triage nurse at 3am and a cashier mid-queue. The interface
has to disappear so the clinical or financial decision in front of the user
is the only thing competing for attention. Flat, calm, clinical — never
consumer-flashy, never "delightful" for its own sake.

**We do:**

- Flat fills and hairline 1px borders (`--border`, `--border-strong`) to
  separate surfaces.
- Generous whitespace — the spacing scale, used deliberately, not
  approximated.
- Exactly two shadow levels, reserved for content that is genuinely floating
  above the page (§8).
- Status communicated by colour **and** icon **and** label together, never
  colour alone.
- Sentence case, plain language, everywhere.
- Sharp, purposeful corners — `--radius` / `--radius-lg` on containers,
  `--radius-full` only for pills (badges, avatars).

**We don't:**

- Gradients on UI surfaces. (The one sanctioned exception is a full-bleed
  branded hero panel — e.g. the login screen backdrop — never a card, button,
  or table row.)
- Glassmorphism, blur, or translucency effects.
- Drop shadows on static, in-flow surfaces (cards, table containers, panels).
  Use a border. A shadow implies elevation above the page; most of this UI
  sits flush on it.
- Neon or saturated gradients, especially purple-to-pink. Not our palette,
  not our category.
- Emojis anywhere in the product UI.
- ALL CAPS or Title Case in interface copy.
- Decorative icons that don't pair with a text label (§5).
- `--green-signal` as a background, or under white text (1.3:1 — fails every
  contrast threshold). It is a signal colour, not a surface.

---

## 2. Colour system

Every colour in a component must come from a token below. No raw hex, ever
— `tokens.css` says so at the top of the file, and it's load-bearing: this
is what keeps a clinical product's palette auditable.

### Brand green ramp

| Token | Hex | Use for | Never for |
|---|---|---|---|
| `--green-signal` | `#7FFF00` | Active nav state, badge fills (`.badge-signal`), focus rings, the logo mark, low-opacity decorative accents | A surface of any size; anything under white text |
| `--green-100` | `#EAF3DE` | Tinted/selected row backgrounds (`.row-selected`), success badge background | Body text foreground |
| `--green-200` | `#C0DD97` | Muted text **on dark surfaces only** (sidebar secondary labels) | Text on light surfaces — fails contrast there |
| `--green-400` | `#8FC23C` | Unused. Reserved mid-ramp step — don't assign it meaning without updating this file | — |
| `--green-500` | `#639922` | Unused. Reserved mid-ramp step — don't assign it meaning without updating this file | — |
| `--green-600` | `#3B6D11` | Primary buttons, links, primary interactive accents. The one green that's safe under white text (5.6:1) | Large background fills behind body copy |
| `--green-800` | `#24460A` | Hover/pressed state for `--green-600` primaries; success status text (`--success`) | — |
| `--green-900` | `#173404` | Sidebar, header chrome, the login hero backdrop | Body text colour, button fills that need frequent scanning (too heavy at high area) |

The rule of thumb: **`--green-900` is structure** (chrome you see once and
tune out), **`--green-600` is action** (something you click), **`--green-signal`
is a signal** (something demanding a glance — active, focused, or a status
pill), never a fill of any real size.

### Neutrals

| Token | Use for |
|---|---|
| `--gray-25` / `--gray-50` | `--bg-raised`, `--bg-page`, table row hover |
| `--gray-100` | `--neutral-bg` (neutral badges) |
| `--gray-200` / `--gray-300` | `--border` / `--border-strong` |
| `--gray-500` | `--text-muted` |
| `--gray-700` | `--text-secondary` |
| `--gray-900` | `--text-primary` |

Components should reach for the **semantic** text/surface/border tokens
(`--text-primary`, `--bg-page`, `--border`, …), not the raw `--gray-*` scale
directly — the semantic layer is what lets the neutral ramp move without a
find-and-replace across every workspace.

### Semantic status

Deliberately far from brand green in hue, so clinical severity never
competes visually with "active" / "success," which are green in this system.

| Token | Hex | Meaning |
|---|---|---|
| `--critical` / `--critical-bg` | `#C4183C` / `#FCEBEE` | IMMEDIATE, critical lab results, overdue doses. This is the system's **danger/error** token — there is no separate `--danger`, don't add one |
| `--warning` / `--warning-bg` | `#B8690A` / `#FDF4E7` | URGENT, expiring stock, unpaid balance |
| `--info` / `--info-bg` | `#1B6E9E` / `#EBF4F9` | Neutral informational callouts |
| `--success` / `--success-bg` | `--green-800` / `--green-100` | Confirmations, positive status text. Aliases onto the brand ramp deliberately — success *is* green here, it's the one status that's allowed to agree with brand |

Every status pairing follows the same shape: a saturated `-fg` colour for
text/icon, a pale `-bg` tint for the badge or banner fill. Never invert it
(pale text on saturated fill) except `.badge-signal`, which is the one
codified exception because dark-on-signal is the only readable combination.

---

## 3. Typography

14px base, not 16 — these are dense clinical screens; staff scan tables all
shift. Tabular numerals on anything numeric so vitals and money columns
align (`.tabular`, `.money`).

| Token | Size | Typical weight | Use for |
|---|---|---|---|
| `--text-xs` | 11px | `--weight-medium` | Badge labels, table captions |
| `--text-sm` | 12px | `--weight-normal`/`--weight-medium` | Table headers, secondary/meta text, field hints and errors |
| `--text-base` | 14px | `--weight-normal` | Body copy, inputs, buttons — the default |
| `--text-lg` | 16px | `--weight-normal`/`--weight-medium` | Input text (touch-safe), section labels |
| `--text-xl` | 20px | `--weight-medium` | Screen/section titles (`<h1>` inside a workspace page) |
| `--text-2xl` | 26px | `--weight-bold` | The one or two "arrival" headings per app — login, empty states, a workspace splash. Not for routine screen titles |

| Weight | Value | Use for |
|---|---|---|
| `--weight-normal` | 400 | Body copy, table cell content |
| `--weight-medium` | 500 | Labels, active tab, table primary column, buttons |
| `--weight-bold` | 600 | Page/card headings only (`--text-xl` and up) |

Line-height isn't tokenised — use `1.4` for body/paragraph text and `1.2`
for headings (`--text-xl` and above), set locally. If a third line-height
value becomes necessary, add it to `tokens.css` rather than eyeballing a
fourth.

---

## 4. Spacing & vertical rhythm

4px base scale: `--space-1` (4px) through `--space-10` (40px). Never a raw
pixel margin/padding in a component — pick the nearest step.

- **Inside a control** (button padding, badge padding): `--space-1`–`--space-3`.
- **Between a label and its field, or an icon and its text**: `--space-1`–`--space-2`.
- **Between fields in a form, or rows in a tight list**: `--space-4`.
- **Between distinct sections of a page** (header to toolbar, toolbar to
  table): `--space-4`–`--space-6`.
- **Around a card's outer edge**: `--space-6`–`--space-8` for a standalone
  card (login, an empty state), `--space-4` for an in-workspace panel.
- **Above/below a page's outermost content**: `--space-8`–`--space-10`.

Rhythm rule: spacing should increase with the semantic distance between two
things, never decrease. A gap between a field and its own error message is
always smaller than the gap between that field and the next one.

---

## 5. Iconography

We use [`lucide-react`](https://lucide.dev), stroke width **2** (the
library default — don't override it).

| Context | Size |
|---|---|
| Inline with body/label text (form field icons, button icons, table cells) | 16px |
| Section/page headings | 20px |
| Empty states, large standalone marks (e.g. a hero brand mark) | 24px |

An icon is always paired with a text label **unless it is a repeated
affordance the user learns once** — a password show/hide toggle, a table row
action that's identical across every row and already has a tooltip/aria-label.
Anywhere the icon's meaning could be ambiguous or is the *only* signal for
something the user hasn't seen before, add the label. This is the same rule
that governs status colour (§1): never make the icon carry meaning alone
when a word would remove all doubt.

Existing exception: `QueueShell.tsx`'s row-severity markers predate this
convention and use raw glyphs (`⬤`, `▲`) rather than `lucide-react`. Don't
replicate that pattern in new code — new severity/status icons should use the
matching Lucide icon (e.g. `AlertTriangle` for warning/critical rows,
`Siren` or similar for emergency) at the sizes above. Migrating `QueueShell`
itself is a separate, deliberate change, not something to do incidentally.

---

## 6. Interactive states

| State | Treatment |
|---|---|
| **Default** | `--border-strong` outline on inputs; `--border` on containers; text at `--text-primary` or `--text-secondary` |
| **Hover** (buttons, rows, clickable cards) | Background steps one tint darker/deeper (`--green-600` → `--green-800` for primary buttons; `--bg-surface` → `--gray-25`/`--gray-50` for rows and secondary buttons). Transition `background-color` over `--duration-fast` with `--ease-standard` |
| **Focus** | Never a browser default outline. Always `--focus-ring` via the global `:focus-visible` rule — a 3px `--green-signal` ring at 45% opacity, applied automatically to any focusable element. Don't hand-roll a competing focus style; if a container needs its own focus cue (e.g. an input wrapper highlighting alongside its child), pair a `--green-600` border on `:focus-within` with the input's own ring, don't replace the ring |
| **Active/pressed** | Same tone as hover, no additional motion — this isn't a tactile product |
| **Disabled** | `opacity: 0.5` (buttons/controls; `.btn:disabled` is the canonical value) and `cursor: not-allowed`. Never grey-out via a separate colour token — opacity keeps it consistent across every surface it lands on |
| **Loading** | An inline spinner (a Lucide icon such as `Loader2`, rotated) replaces the leading icon or sits where content will land — never a layout shift, never a full-screen blocking spinner for an in-page action. Rotation runs at `--duration-slow` (800ms), `linear`, looping, and must be skipped/stilled under `prefers-reduced-motion: reduce` |

Every transition and animation in a component must be wrapped in its own
`@media (prefers-reduced-motion: reduce)` guard at the point it's defined —
there is no global override in `tokens.css` doing this for you (see §8).

---

## 7. Voice

- Sentence case everywhere. "Sign in", not "Sign In" or "SIGN IN".
- "Sign in" / "Sign out", never "Login" / "Logout" as UI copy (the noun
  "login page" as a route/internal name is fine; the verb the user reads is
  always "sign in").
- No exclamation marks. Nothing in a clinical workflow is that exciting, and
  urgency is communicated by the status system (§2), not punctuation.
- Validation messages name the action to take, not the failure: "Enter a
  phone number", not "Phone is required".
- Security-sensitive errors never narrow the failure: "Invalid username or
  password", never "Username not found" / "Incorrect password".
- Empty states say what the empty state *means* in context: "No patients
  waiting for triage", not "No results" or "No data".

---

## 8. Elevation & motion

### Elevation — exactly two levels

Most of this UI has **no shadow at all**. A card, a table container, a form
panel: flat fill, `--border`, done. Shadow is reserved for content that is
actually floating above the page, and there are only two tiers of that:

| Token | Value | Use for |
|---|---|---|
| `--shadow` | `0 2px 8px rgb(35 38 31 / 0.08)` | Raised — dropdown menus, popovers, tooltips |
| `--shadow-lg` | `0 8px 24px rgb(35 38 31 / 0.12)` | Overlay — modals, dialogs, and the one standalone "floating card" pattern (the auth screen) |

If a design calls for a shadow that isn't one of these two, the design is
wrong, not the token scale.

### Motion

| Token | Value | Use for |
|---|---|---|
| `--duration-fast` | 120ms | Colour/border transitions — hover, `:focus-within` |
| `--duration-base` | 200ms | Slightly larger transitions — tab underline movement, a panel opening |
| `--duration-slow` | 800ms | Looping motion — spinner rotation |
| `--ease-standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | Default easing for the above |

Motion here is a hover/focus courtesy, never a personality. There is no
page-transition animation, no stagger, no bounce. Every use of an animation
or transition must be re-declared as `none`/instant inside that component's
own `@media (prefers-reduced-motion: reduce)` block — ward and lab staff use
these screens for a 12-hour shift, and motion sensitivity is a real,
common accommodation, not an edge case.

---

## 9. Token quick reference

See `tokens.css` for exact values and inline comments — this table is a map,
not a duplicate source of truth.

```
Brand:      --green-signal --green-100 --green-200 --green-400* --green-500*
            --green-600 --green-800 --green-900          (*reserved, unused)
Neutrals:   --white --gray-25 --gray-50 --gray-100 --gray-200 --gray-300
            --gray-500 --gray-700 --gray-900
Status:     --critical(-bg) --warning(-bg) --info(-bg) --success(-bg) --neutral-bg
Surfaces:   --bg-page --bg-surface --bg-raised --bg-sidebar --border --border-strong
Text:       --text-primary --text-secondary --text-muted
            --text-on-dark --text-on-dark-muted --text-link
Type:       --font-sans --font-mono
            --text-xs --text-sm --text-base --text-lg --text-xl --text-2xl
            --weight-normal --weight-medium --weight-bold
Spacing:    --space-1 --space-2 --space-3 --space-4 --space-5 --space-6
            --space-8 --space-10
Radius:     --radius-sm --radius --radius-lg --radius-full
Elevation:  --shadow --shadow-lg
Motion:     --duration-fast --duration-base --duration-slow --ease-standard
Layout:     --sidebar-width --header-height --row-height --focus-ring
```
