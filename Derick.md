Phases
 0  Foundation      ✅
1  Front office    ✅
2  Clinical core   ✅
3  Billing gate    ✅
4  Diagnostics     🔄 just started
5  Pharmacy
6  Inpatient
7  Back office
8  Surgery         theatre, consent, checklist, intraop, reconciliation
9  Accountant      claims, receivables, refunds, shift reconciliation


Commands
-- BACKEND 
cd ~/ADAGS-HMS-V2.0

# infrastructure
docker compose up -d
docker compose ps          # postgres, redis, minio should be healthy

# backend — migrations run on startup
cd backend
./gradlew bootRun


IN ANOTHER TERMINAL
-- FRONTEND
cd ~/ADAGS-HMS-V2.0/frontend
pnpm run generate-api-types
pnpm run dev