-- 12-pharmacist.md §7.2: "Supplier | Managed list, not free text" — the
-- restock form's picker needs at least a starting list; supplier has no
-- admin management screen yet (Supplier.java's own comment: "a minimal
-- referenceable list with no propose/approve workflow"), so a handful of
-- representative Tanzanian pharmaceutical suppliers are seeded directly,
-- same as insurance_scheme's seed rows.
INSERT INTO supplier (code, name) VALUES
    ('MSD', 'Medical Stores Department'),
    ('SHELYS', 'Shelys Pharmaceuticals Ltd'),
    ('MEDIVET', 'Medivet Pharmaceuticals'),
    ('KARIBUPHARM', 'Karibu Pharmacy Ltd'),
    ('SURGIPHARM', 'Surgipharm Tanzania Ltd');
