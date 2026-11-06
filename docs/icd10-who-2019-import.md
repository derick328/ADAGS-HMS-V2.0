# WHO ICD-10 2019 import

The repository already includes `backend/src/main/resources/data/icd10/WHO_ICD10_2019_Codes.csv`.
The backend image bundles this file into its JAR. Import it explicitly after
the app starts; no CSV upload or container file copy is needed. From the repo
root on the VPS, run this and enter credentials for a user with
`REFERENCE_IMPORT` (the bootstrap admin has this permission):

```sh
ADAGS_BASE_URL=https://adagshms.derutech.com python3 backend/scripts/import_who_icd10_2019.py
```

The command prompts for a password, logs in, sends the import request with the returned token, and prints either the import counts or the HTTP error. It does not print or save the password or token.

For local development, omit `ADAGS_BASE_URL` and run the script from `backend/`.

Alternatively, log in and import manually:

```sh
export ADAGS_BASE_URL=https://adagshms.derutech.com
read -r -p 'Username: ' LOGIN_USER
read -r -s -p 'Password: ' LOGIN_PASSWORD; echo
LOGIN_PAYLOAD=$(printf '%s\n%s\n' "$LOGIN_USER" "$LOGIN_PASSWORD" | python3 -c 'import json,sys; print(json.dumps({"username":sys.stdin.readline().rstrip("\n"),"password":sys.stdin.readline().rstrip("\n")}))')
unset LOGIN_PASSWORD
LOGIN_RESPONSE=$(curl -fsS -H 'Content-Type: application/json' -d "$LOGIN_PAYLOAD" "$ADAGS_BASE_URL/auth/login")
unset LOGIN_PAYLOAD
export ACCESS_TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
unset LOGIN_RESPONSE
```

Then run the import once per release or correction:

```sh
if [ -z "${ACCESS_TOKEN:-}" ]; then
  echo "ACCESS_TOKEN is empty; log in as a user with REFERENCE_IMPORT first"
else
  curl -sS -i -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$ADAGS_BASE_URL/api/reference/icd10/import/who-2019"
fi
```

The response reports `release`, `imported`, `updated`, `skipped`, and `rejected`. Repeat calls skip unchanged rows. The operation runs in one transaction. An invalid row or missing parent is counted as rejected; descendants of a rejected parent are also rejected. Database failures roll back the entire import. No startup job runs it.

The starter set does not contain the full classification. If a diagnosis such as syphilis or salmonella is absent, check that the import returned `rejected: 0` and that `icd10_code` contains `A53.9` and `A02.9` with `icd10_release = WHO-ICD-10-2019` and `is_selectable = true`.

## Selection policy requiring clinical sign-off

The import preserves the CSV's official preferred title verbatim after trimming surrounding whitespace. It stores the long title separately. Search and diagnosis entry offer terminal `category` rows only. This includes valid three-character terminal categories such as `B54` and `I10`. Chapter and block headings, parent categories with more specific children, and `aster` manifestation codes are excluded. Dagger codes remain selectable as aetiology codes. Supplementary subdivisions absent from this CSV cannot be inferred or generated.

New WHO rows start with `is_billable = false`. Billing eligibility needs a separate clinical and payer review. Reimport never changes existing `is_billable` or `is_active` decisions. The clinical lead should review the selection rule, especially dagger/asterisk pairs and locally required supplementary subdivisions, before clinical rollout. Historical diagnosis rows and their title snapshots are never rewritten.
