# HMS — Controlled Medicines: Tanzania Regulatory Reference

**Version:** 0.1
**Source:** Tanzania Medicines and Medical Devices (Scheduling of Medicines) Regulations, 2025, made under the Tanzania Medicines and Medical Devices Act (Cap 219), signed by the Minister for Health, Dodoma. Revokes GN 63 of 2015.
**Regulator:** Tanzania Medicines and Medical Devices Authority (TMDA)
**Supersedes assumptions in:** `hms-controlled-medicines-policy.md` §2.1

⚠ Read this alongside the actual regulations before implementation. This document summarises the structure for system design; it is not legal advice and the schedules are amendable by TMDA at any time.

---

## 1. Correction to earlier assumptions

Two things in the earlier policy document were wrong:

| Earlier assumption | Actual position |
|---|---|
| Diazepam, midazolam, phenobarbital would be blocked as controlled | **Not controlled.** Regulation 16(3) places psychotropics with moderate or high therapeutic value in Prescription Only Human Medicine |
| Controlled status is a property of the molecule | **It is a property of the product.** Strength and formulation determine the schedule — codeine appears in three different schedules |

The practical effect: the v1 block is much narrower than feared. Most essential sedatives and anticonvulsants remain fully dispensable.

---

## 2. The five human-medicine categories

Regulation 7(2) divides medicines into controlled, prescription only, pharmacy only, general sales, and ADDO prescription. This maps directly onto a schema field.

```
Item.regulatory_schedule ∈

  CONTROLLED              First Schedule    — narcotics, psychotropics (limited
                                              therapeutic value), precursors
  PRESCRIPTION_ONLY       Second Schedule   — POM; includes psychotropics with
                                              moderate/high therapeutic value
  PHARMACY_ONLY_BTC       Third Schedule A  — behind the counter
  PHARMACY_ONLY_OTC       Third Schedule B  — over the counter
  GENERAL_SALES           Fourth Schedule   — no prescription needed
  ADDO_PRESCRIPTION       Fifth Schedule    — accredited drug dispensing outlets
  RADIOPHARMACEUTICAL     Regulation 19     — authorised premises only
  UNCLASSIFIED            not yet reviewed  — blocked (see §5)
```

⚠ **Replace the earlier three-state `controlled_status` with this.** It carries strictly more information, and the additional categories drive behaviour beyond controlled handling — see §6.

Veterinary schedules (Sixth to Tenth) are out of scope unless the hospital has a veterinary arm.

---

## 3. What sits inside `CONTROLLED`

The First Schedule has four internal groupings. The distinction between them matters.

### 3.1 Narcotic drugs and substances — blocked

Includes: morphine and its derivatives, pethidine, fentanyl and its many analogues, methadone, oxycodone, hydrocodone, hydromorphone, codeine, dihydrocodeine, ethylmorphine, pholcodine, opium, coca leaf, cocaine, cannabis and cannabis resin, remifentanil, sufentanil, alfentanil, nalbuphine, tilidine, diphenoxylate, difenoxin.

⚠ **Ketamine and its salts are on the narcotic list.** This is the item most likely to cause operational difficulty, since ketamine is a common anaesthetic in the region. Confirm usage with the pharmacist-in-charge before finalising v1 scope.

Regulation 15(1) extends control to isomers, derivatives, esters, ethers, salts, and intermediate or raw materials.

### 3.2 Exempted low-strength narcotic preparations — NOT controlled

Regulation 15(3): preparations containing narcotic drugs in low strengths are not controlled drugs. The First Schedule sets the thresholds:

| Substance | Exemption threshold |
|---|---|
| Acetyldihydrocodeine, codeine, dihydrocodeine, ethylmorphine, nicodicodine, norcodeine, pholcodine | Not more than 2.5% in undivided preparations, compounded with one or more other ingredients |
| Cocaine | Not more than 0.1% calculated as cocaine base |
| Opium / morphine | Not more than 0.2% morphine calculated as anhydrous morphine base, compounded so the drug cannot readily be recovered |
| Dextropropoxyphene | Not more than 135mg base per dosage unit, or 2.5% in undivided preparations |
| Difenoxin | Not more than 0.5mg per dosage unit with atropine sulfate at ≥5% of the difenoxin dose |
| Diphenoxylate | Not more than 2.5mg per dosage unit with atropine sulfate at ≥1% of the diphenoxylate dose |
| Propiram | Not more than 100mg per dosage unit with at least the same amount of methylcellulose |

⚠ **This is why classification must be per product.** A cough linctus at 0.2% codeine is not controlled; a 30mg codeine phosphate tablet is. Same molecule, opposite handling.

### 3.3 Psychotropics with limited medical value — blocked

LSD, MDMA, methamphetamine, cathinone, mescaline, psilocybin, phencyclidine analogues, synthetic cannabinoids, etizolam, flualprazolam, clonazolam, diclazepam, tapentadol, oliceridine, lisdexamfetamine.

Regulation 16(1) characterises these as high abuse risk, serious threat to public health, and little or no therapeutic value. Regulation 16(2) limits them to scientific and medical use by duly authorised persons in medical or scientific establishments.

Most of these will never appear in a hospital formulary at all.

### 3.4 Psychotropics with moderate or high therapeutic value — NOT controlled

Regulation 16(3): these are **not** controlled drugs and belong to Prescription Only Human Medicine.

This list is where the earlier assumption failed. It includes diazepam, midazolam, lorazepam, clonazepam, alprazolam, chlordiazepoxide, nitrazepam, oxazepam, temazepam, triazolam, flunitrazepam, phenobarbital, methylphenobarbital, pentobarbital, secobarbital, amobarbital, buprenorphine, pentazocine, phentermine, zolpidem, dronabinol, meprobamate, methaqualone.

**Practical consequence for v1:** seizure management, procedural sedation, and anxiolysis are unaffected by the block. Only the true narcotic analgesics and ketamine are.

### 3.5 Precursors — blocked

Regulation 17. Includes acetic anhydride, acetone, ephedrine, norephedrine, ergometrine, ergotamine, potassium permanganate, safrole, isosafrole, phenylacetic acid, piperidine, hydrochloric acid, ethyl ether, methyl ethyl ketone, lysergic acid, anthranilic acid.

⚠ Several of these have legitimate non-medicinal hospital uses — acetone, potassium permanganate, hydrochloric acid. If the inventory module tracks laboratory chemicals as well as medicines, precursor status needs handling there too, not just in the pharmacy.

---

## 4. Statutory obligations the system touches

These are the requirements that turn into features whenever controlled handling is built.

| Reg | Requirement | System implication |
|---|---|---|
| 11(2) | Licence or permit required to acquire, possess, manufacture or supply controlled medicines for sale | Store the facility permit reference and expiry; block controlled operations when lapsed |
| 11(3) | Exempts prescribing and dispensing by an authorised prescriber, a pharmacist on such a prescription, a hospital pharmacist on the written requisition of a practitioner, dentist or ward nurse-in-charge, and a nurse or midwife on written direction | This is the legal basis for ward requisitions — model requisition as a first-class object, not as a prescription |
| 12 | Quarterly and annual consumption estimate reports to TMDA | Reporting requirement; scope to importers, but hospitals submit annual consumption returns per TMDA guidance |
| 13(1) | Hospitals must keep records of quantities of each controlled medicine acquired, used, or disposed | The register. Acquired / used / disposed are three distinct movement types |
| 13(2) | Records preserved not less than two years, available for inspection | Retention policy; make the register exportable for an inspector |
| 13(3) | Destruction of controlled drugs must be authorised and supervised by TMDA | Wastage and destruction are separate flows; destruction requires an external authorisation reference |
| 14(1) | Primary pack sealed so a broken seal is distinguishable | Receipt checking at restock |
| 14(2) | Storage in a locked receptacle, cabinet, or approved safe, opened only by the lawful possessor or an authorised person | Model the controlled cabinet as a distinct `Store` with a restricted key-holder list |
| 6(1) | No person shall sell, possess or manufacture any medicine other than those listed in the regulations | **Legal basis for `UNCLASSIFIED` = blocked** |
| 8 | TMDA may change, vary, add or delist medicines at any time | Schedules are versioned data with an import process, never hardcoded |

Separately, per TMDA guidance: the Medical Stores Department is the sole importer of narcotic drugs, and other facilities require a TMDA permit to purchase narcotics from MSD. Facilities are also expected to keep a register or ledger recording entry and issuance.

---

## 5. `UNCLASSIFIED` now has a legal footing

Regulation 6(1) means a medicine not listed in any schedule cannot lawfully be sold at all. So blocking an unclassified item is not merely conservative engineering — it reflects the legal position.

Note the interaction with regulation 7(3), which deems all medicines registered under the Act after commencement to form part of the schedules. That does not tell your system *which* schedule, so the classification step remains necessary.

```
Item.regulatory_schedule  NOT NULL  DEFAULT 'UNCLASSIFIED'
```

`UNCLASSIFIED` is blocked from pricing, prescribing, dispensing, and restock exactly as `CONTROLLED` is.

---

## 6. The schedule drives more than the controlled block

Carrying the full schedule rather than a controlled flag pays off elsewhere:

| Schedule | Prescription required by law? | Uses in the system |
|---|---|---|
| `CONTROLLED` | Yes, plus register and permit | Blocked in v1 |
| `PRESCRIPTION_ONLY` | Yes | Normal hospital dispensing |
| `PHARMACY_ONLY_BTC` | No, but pharmacist-supervised, not self-selection | Could support a counter-sales flow |
| `PHARMACY_ONLY_OTC` | No | Counter sales |
| `GENERAL_SALES` | No | Counter sales; includes paracetamol, ORS, Whitfield's ointment |
| `ADDO_PRESCRIPTION` | Yes, in ADDO context | Not applicable to a hospital pharmacy |
| `RADIOPHARMACEUTICAL` | Yes, authorised premises | Blocked unless the facility is authorised |

### 6.1 Your prescription-only rule is stricter than the law

The regulations define general sale medicines as those not needing a practitioner's direction, and pharmacy-only medicines as saleable without a prescription.

So "no medicine without a doctor's prescription" is an **internal policy choice**, not a legal requirement. It is a defensible one for a hospital — it keeps the audit trail complete and every dispense tied to an encounter. Just recognise the trade-off:

- Walk-in sales of paracetamol, ORS, gauze, and bandages are out of scope in v1
- That is real revenue a hospital pharmacy usually earns
- If it is later wanted, the schedule field already tells you exactly which items qualify, so the change is a new flow rather than a data migration

---

## 7. Revisions to `hms-controlled-medicines-policy.md`

| Section | Change |
|---|---|
| §2 | Replace three-state `controlled_status` with `regulatory_schedule` (§2 here). `UNCLASSIFIED` default retained, now with regulation 6(1) as its basis |
| §2.1 | Seed source identified: Scheduling of Medicines Regulations 2025, First to Fifth Schedules. Match by INN **and formulation strength**, not INN alone |
| §3 | Block condition becomes `regulatory_schedule NOT IN (PRESCRIPTION_ONLY, PHARMACY_ONLY_BTC, PHARMACY_ONLY_OTC, GENERAL_SALES)` |
| §6.1 | Misclassification watchlist should match against the First Schedule narcotic and limited-value psychotropic lists only — **not** the moderate/high therapeutic value psychotropics, which are legitimately Prescription Only. Matching against those would produce constant false positives on diazepam and midazolam |
| §7 | v2 requirements confirmed against regulations 11–14. Add: permit tracking (11(2)), TMDA-supervised destruction (13(3)), locked-cabinet store modelling (14(2)), ward requisition as a first-class object (11(3)) |
| §8 | Scope of the v1 gap is smaller than stated — narcotic analgesics and ketamine, not the benzodiazepine class |

---

## 8. Open

| | Question |
|---|---|
| `?Q1` | Does the hospital use ketamine? It is the one controlled item likely to force the issue in v1 |
| `?Q2` | Does the facility hold a TMDA permit for narcotics, and who tracks its expiry? |
| `?Q3` | Are low-strength codeine preparations (cough linctus, compound analgesics) in the formulary? They are exempt, but classification must be per product to get that right |
| `?Q4` | Does the inventory module track laboratory chemicals? Several First Schedule precursors have legitimate lab uses |
| `?Q5` | Is the prescription-only policy permanent, or should the schedule field be built now to support counter sales later? |
| `?Q6` | Who obtains and maintains the schedule import — TMDA reissues these regulations periodically, and regulation 8 permits amendment at any time |
