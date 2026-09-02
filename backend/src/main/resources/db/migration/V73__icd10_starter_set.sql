-- V13 deliberately left icd10_code empty ("seeding is an operational task
-- for the clinical lead"), but that leaves the doctor's diagnosis picker
-- returning nothing until an admin runs an ICD-10 import — and there is
-- no shipping WHO release to run through Icd10ImportService yet. This
-- seeds a small, hand-picked starter set covering the everyday
-- presentations at a Tanzanian district/regional hospital, so the picker
-- is usable out of the box.
--
-- Real deployments still call POST /api/reference/icd10/import with the
-- licensed release; that upsert will just overwrite these titles with the
-- official ones, keyed on `code`. `icd10_release = 'starter-2019'` marks
-- these apart so an admin can see what came from the bootstrap versus a
-- real import.
--
-- ON CONFLICT DO NOTHING keeps this migration safe against environments
-- where the same codes were already imported by another route.

INSERT INTO icd10_code (code, title, chapter, block, icd10_release, is_billable) VALUES
    -- Infectious diseases (I) — the top of every outpatient list here
    ('A00.9', 'Cholera, unspecified',                              'I', 'A00-A09', 'starter-2019', TRUE),
    ('A01.0', 'Typhoid fever',                                     'I', 'A00-A09', 'starter-2019', TRUE),
    ('A09',   'Diarrhoea and gastroenteritis of presumed infectious origin', 'I', 'A00-A09', 'starter-2019', TRUE),
    ('A15.0', 'Tuberculosis of lung, confirmed by sputum microscopy', 'I', 'A15-A19', 'starter-2019', TRUE),
    ('A16.2', 'Tuberculosis of lung, without mention of bacteriological or histological confirmation', 'I', 'A15-A19', 'starter-2019', TRUE),
    ('A41.9', 'Sepsis, unspecified organism',                      'I', 'A30-A49', 'starter-2019', TRUE),
    ('A90',   'Dengue fever',                                      'I', 'A90-A99', 'starter-2019', TRUE),
    ('B20',   'HIV disease resulting in infectious and parasitic diseases', 'I', 'B20-B24', 'starter-2019', TRUE),
    ('B24',   'Unspecified human immunodeficiency virus [HIV] disease', 'I', 'B20-B24', 'starter-2019', TRUE),
    ('B50.9', 'Plasmodium falciparum malaria, unspecified',        'I', 'B50-B64', 'starter-2019', TRUE),
    ('B51.9', 'Plasmodium vivax malaria without complication',     'I', 'B50-B64', 'starter-2019', TRUE),
    ('B54',   'Unspecified malaria',                               'I', 'B50-B64', 'starter-2019', TRUE),
    ('B15.9', 'Hepatitis A without hepatic coma',                  'I', 'B15-B19', 'starter-2019', TRUE),
    ('B16.9', 'Acute hepatitis B without delta-agent and without hepatic coma', 'I', 'B15-B19', 'starter-2019', TRUE),
    ('B18.1', 'Chronic viral hepatitis B without delta-agent',     'I', 'B15-B19', 'starter-2019', TRUE),
    ('B34.9', 'Viral infection, unspecified',                      'I', 'B25-B34', 'starter-2019', TRUE),
    ('B77.9', 'Ascariasis, unspecified',                           'I', 'B65-B83', 'starter-2019', TRUE),

    -- Neoplasms (II)
    ('C50.9', 'Malignant neoplasm of breast, unspecified',         'II','C50-C50', 'starter-2019', TRUE),
    ('C53.9', 'Malignant neoplasm of cervix uteri, unspecified',   'II','C51-C58', 'starter-2019', TRUE),

    -- Blood (III)
    ('D50.9', 'Iron deficiency anaemia, unspecified',              'III','D50-D53','starter-2019', TRUE),
    ('D57.1', 'Sickle-cell anaemia without crisis',                'III','D55-D59','starter-2019', TRUE),
    ('D64.9', 'Anaemia, unspecified',                              'III','D60-D64','starter-2019', TRUE),

    -- Endocrine (IV)
    ('E10.9', 'Type 1 diabetes mellitus, without complications',   'IV','E10-E14', 'starter-2019', TRUE),
    ('E11.9', 'Type 2 diabetes mellitus, without complications',   'IV','E10-E14', 'starter-2019', TRUE),
    ('E11.5', 'Type 2 diabetes mellitus with peripheral circulatory complications', 'IV','E10-E14', 'starter-2019', TRUE),
    ('E43',   'Unspecified severe protein-energy malnutrition',    'IV','E40-E46', 'starter-2019', TRUE),
    ('E44.0', 'Moderate protein-energy malnutrition',              'IV','E40-E46', 'starter-2019', TRUE),
    ('E66.9', 'Obesity, unspecified',                              'IV','E65-E68', 'starter-2019', TRUE),

    -- Mental (V)
    ('F32.9', 'Depressive episode, unspecified',                   'V','F30-F39',  'starter-2019', TRUE),
    ('F41.1', 'Generalized anxiety disorder',                      'V','F40-F48',  'starter-2019', TRUE),
    ('F20.9', 'Schizophrenia, unspecified',                        'V','F20-F29',  'starter-2019', TRUE),

    -- Nervous system (VI)
    ('G40.9', 'Epilepsy, unspecified',                             'VI','G40-G47', 'starter-2019', TRUE),
    ('G43.9', 'Migraine, unspecified',                             'VI','G40-G47', 'starter-2019', TRUE),
    ('G44.2', 'Tension-type headache',                             'VI','G40-G47', 'starter-2019', TRUE),

    -- Eye and ear (VII/VIII)
    ('H10.9', 'Conjunctivitis, unspecified',                       'VII','H10-H13','starter-2019', TRUE),
    ('H66.9', 'Otitis media, unspecified',                         'VIII','H65-H75','starter-2019', TRUE),

    -- Circulatory (IX)
    ('I10',   'Essential (primary) hypertension',                  'IX','I10-I15', 'starter-2019', TRUE),
    ('I11.9', 'Hypertensive heart disease without heart failure',  'IX','I10-I15', 'starter-2019', TRUE),
    ('I50.0', 'Congestive heart failure',                          'IX','I30-I52', 'starter-2019', TRUE),
    ('I63.9', 'Cerebral infarction, unspecified',                  'IX','I60-I69', 'starter-2019', TRUE),
    ('I64',   'Stroke, not specified as haemorrhage or infarction','IX','I60-I69', 'starter-2019', TRUE),
    ('I21.9', 'Acute myocardial infarction, unspecified',          'IX','I20-I25', 'starter-2019', TRUE),

    -- Respiratory (X)
    ('J00',   'Acute nasopharyngitis (common cold)',               'X','J00-J06',  'starter-2019', TRUE),
    ('J06.9', 'Acute upper respiratory infection, unspecified',    'X','J00-J06',  'starter-2019', TRUE),
    ('J15.9', 'Bacterial pneumonia, unspecified',                  'X','J09-J18',  'starter-2019', TRUE),
    ('J18.9', 'Pneumonia, unspecified organism',                   'X','J09-J18',  'starter-2019', TRUE),
    ('J20.9', 'Acute bronchitis, unspecified',                     'X','J20-J22',  'starter-2019', TRUE),
    ('J45.9', 'Asthma, unspecified',                               'X','J40-J47',  'starter-2019', TRUE),
    ('J44.9', 'Chronic obstructive pulmonary disease, unspecified','X','J40-J47',  'starter-2019', TRUE),

    -- Digestive (XI)
    ('K02.9', 'Dental caries, unspecified',                        'XI','K00-K14', 'starter-2019', TRUE),
    ('K25.9', 'Gastric ulcer, unspecified',                        'XI','K20-K31', 'starter-2019', TRUE),
    ('K29.7', 'Gastritis, unspecified',                            'XI','K20-K31', 'starter-2019', TRUE),
    ('K30',   'Functional dyspepsia',                              'XI','K20-K31', 'starter-2019', TRUE),
    ('K35.8', 'Acute appendicitis, other and unspecified',         'XI','K35-K38', 'starter-2019', TRUE),
    ('K40.9', 'Unilateral or unspecified inguinal hernia, without obstruction or gangrene', 'XI','K40-K46','starter-2019', TRUE),
    ('K52.9', 'Non-infective gastroenteritis and colitis, unspecified', 'XI','K50-K52', 'starter-2019', TRUE),
    ('K80.2', 'Calculus of gallbladder without cholecystitis',     'XI','K80-K87', 'starter-2019', TRUE),

    -- Skin (XII)
    ('L03.9', 'Cellulitis, unspecified',                           'XII','L00-L08','starter-2019', TRUE),
    ('L23.9', 'Allergic contact dermatitis, unspecified cause',    'XII','L20-L30','starter-2019', TRUE),
    ('L50.9', 'Urticaria, unspecified',                            'XII','L50-L54','starter-2019', TRUE),
    ('B35.9', 'Dermatophytosis, unspecified (ringworm)',           'I','B35-B49',  'starter-2019', TRUE),

    -- Musculoskeletal (XIII)
    ('M25.5', 'Pain in joint',                                     'XIII','M20-M25','starter-2019', TRUE),
    ('M54.5', 'Low back pain',                                     'XIII','M50-M54','starter-2019', TRUE),
    ('M79.1', 'Myalgia',                                           'XIII','M70-M79','starter-2019', TRUE),
    ('M06.9', 'Rheumatoid arthritis, unspecified',                 'XIII','M05-M14','starter-2019', TRUE),

    -- Genitourinary (XIV)
    ('N39.0', 'Urinary tract infection, site not specified',       'XIV','N30-N39','starter-2019', TRUE),
    ('N18.9', 'Chronic kidney disease, unspecified',               'XIV','N17-N19','starter-2019', TRUE),
    ('N20.0', 'Calculus of kidney',                                'XIV','N20-N23','starter-2019', TRUE),
    ('N70.9', 'Salpingitis and oophoritis, unspecified',           'XIV','N70-N77','starter-2019', TRUE),
    ('N76.0', 'Acute vaginitis',                                   'XIV','N70-N77','starter-2019', TRUE),
    ('N92.6', 'Irregular menstruation, unspecified',               'XIV','N80-N98','starter-2019', TRUE),

    -- Pregnancy / childbirth (XV)
    ('O03.9', 'Complete or unspecified spontaneous abortion without complication', 'XV','O00-O08','starter-2019', TRUE),
    ('O23.4', 'Unspecified infection of urinary tract in pregnancy','XV','O20-O29', 'starter-2019', TRUE),
    ('O80',   'Single spontaneous delivery',                       'XV','O80-O84', 'starter-2019', TRUE),
    ('O99.0', 'Anaemia complicating pregnancy, childbirth and the puerperium', 'XV','O95-O99','starter-2019', TRUE),

    -- Perinatal (XVI)
    ('P07.3', 'Preterm newborn (28-36 weeks)',                     'XVI','P05-P08','starter-2019', TRUE),
    ('P39.9', 'Infection specific to the perinatal period, unspecified', 'XVI','P35-P39','starter-2019', TRUE),

    -- Symptoms / signs (XVIII) — the "not sure yet" bucket
    ('R05',   'Cough',                                             'XVIII','R00-R09','starter-2019', TRUE),
    ('R10.4', 'Other and unspecified abdominal pain',              'XVIII','R10-R19','starter-2019', TRUE),
    ('R11',   'Nausea and vomiting',                               'XVIII','R10-R19','starter-2019', TRUE),
    ('R19.7', 'Diarrhoea, unspecified',                            'XVIII','R10-R19','starter-2019', TRUE),
    ('R50.9', 'Fever, unspecified',                                'XVIII','R50-R69','starter-2019', TRUE),
    ('R51',   'Headache',                                          'XVIII','R50-R69','starter-2019', TRUE),
    ('R53',   'Malaise and fatigue',                               'XVIII','R50-R69','starter-2019', TRUE),
    ('R56.8', 'Other and unspecified convulsions',                 'XVIII','R50-R69','starter-2019', TRUE),

    -- Injuries (XIX)
    ('S06.0', 'Concussion',                                        'XIX','S00-S09','starter-2019', TRUE),
    ('S52.5', 'Fracture of lower end of radius',                   'XIX','S50-S59','starter-2019', TRUE),
    ('S72.0', 'Fracture of neck of femur',                         'XIX','S70-S79','starter-2019', TRUE),
    ('T14.0', 'Superficial injury of unspecified body region',     'XIX','T08-T14','starter-2019', TRUE),
    ('T14.1', 'Open wound of unspecified body region',             'XIX','T08-T14','starter-2019', TRUE),
    ('T30.0', 'Burn of unspecified body region, unspecified degree','XIX','T20-T32','starter-2019', TRUE),
    ('T63.0', 'Toxic effect of snake venom',                       'XIX','T51-T65','starter-2019', TRUE)
ON CONFLICT (code) DO NOTHING;
