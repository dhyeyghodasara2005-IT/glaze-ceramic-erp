# Glaze · Ceramic ERP

**Glaze** is an ERP for ceramic tile manufacturing, built first for a glazed vitrified tile (GVT) plant in Morbi,
Gujarat, that makes 600×1200 mm tiles. The plant ran on Tally, Excel and paper registers; Glaze brings the store,
production, finished goods, dispatch, maintenance, energy and the lab into one system that works on office PCs and on
supervisors' phones, in English and Gujarati.

**Status:** ongoing. Steps 1 to 8 are done (see the roadmap below); costing and analytics come next.

**Stack:** Java 17 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA / Hibernate · MySQL · Flyway ·
React 19 · TypeScript · Vite · Ant Design 6 · TanStack Query · i18next

---

## What it does

| Area | What it covers |
|---|---|
| **Organisation & access** | Companies, plants, users and roles; 39 permissions; every user sees only their plants. Starter roles for each job (store keeper, press supervisor, lab chemist …) |
| **Masters** | Sizes, finishes, categories, grades, designs, materials, machines and lines, dealers, suppliers, transporters, jobwork principals |
| **Raw-material store** | Godowns, GRNs, issues and adjustments; stock and ledger at weighted average cost |
| **Production** | Shifts, body recipes, ball mill charges, slip tests, spray dryer runs, silos (powder traced oldest first back to its batches), design lots, press, glaze, kiln, quality checks, polishing, sorting and packing by grade, shade and caliber |
| **Finished goods** | Godowns and pillars, stock by pillar and batch, opening stock, breakage, regrading, smart put-away (which pillar a new pallet should go to) |
| **Sales & dispatch** | Sales orders from loading slips, product names matched to designs and remembered, a loading plan that picks pillars by rules, a phone checklist for loading, packing list and gate pass |
| **Owner dashboard** | m² packed, Premium share, yield, gas and power per m², dispatches, godown fill, and a "needs a look" list |
| **Maintenance** | Breakdown log (downtime, MTTR, MTBF, availability), preventive jobs with a due list, spares issued from the store |
| **Energy** | Gas and power meters (main and per section), coal, LPG and DG diesel; reading rounds on the phone; use per m² fired and packed |
| **Lab** | Raw-material tests at the GRN: tests and ranges per material, the GRN waits for the lab, rejected material never enters stock, supplier rejection % |
| **Search** | Ctrl+K across every module: document numbers, designs, parties, materials |

---

## How it is built

- **Package by feature.** Each module (`store`, `production`, `sales`, `maintenance`, `energy`, `lab` …) has the same
  layers: **Controller** (HTTP) → **Service** (business rules) → **Repository** (database) → **Entity** (table).
- **The database is versioned** with Flyway: 19 migrations, from the first tables to the lab tests. Hibernate only
  validates the schema.
- **Rules live in the database too** where two requests could race: a unique key on a generated column allows one open
  breakdown per machine; row locks keep two people from posting stock or planning a truck from the same pillar at once.
- **Stock is never edited, only moved.** Posting a document writes ledger rows and updates balances in one transaction;
  cancelling writes the opposite rows.
- **Modules plug into each other through small interfaces.** The store asks every `GrnCheck` before posting a GRN, and
  the lab is one of them; global search asks every `SearchProvider`. Neither side depends on the other's code.
- **Tests run against MySQL**, not an in-memory stand-in: 182 integration tests (JUnit 5, MockMvc) on a separate test
  database that is rebuilt on every run.

---

## Run it on your computer

**Needs:** Java 17, Node 20+, MySQL running locally.

1. **One-time setup:** create `config/application-local.yml` from the template and fill in the database password,
   a JWT secret (`openssl rand -base64 48`) and the first admin's password:
   ```bash
   cp config/application-local.example.yml config/application-local.yml
   ```
   This file is git-ignored, so no password ever reaches the repository.

2. **Backend:**
   ```bash
   ./mvnw spring-boot:run
   ```
   It is ready when the log says `Started CeramicErpApplication`. The API is documented at
   http://localhost:8080/swagger-ui.html (log in with `POST /api/auth/login`, then **Authorize** with the token).

3. **Frontend** (first time: `npm install` inside `frontend/`):
   ```bash
   cd frontend && npm run dev
   ```
   Open http://localhost:5173. On a phone on the same Wi-Fi, use the "Network" address it prints.

4. **Tests** (they use a separate `ceramic_erp_test` database):
   ```bash
   ./mvnw test
   cd frontend && npm run typecheck
   ```

---

## Project layout

```
src/main/java/com/ceramicerp/
├── common/          base entity (audit columns), errors, paging
├── security/        JWT, login filter, plant access
├── auth/  user/  organization/   login, users, roles, permissions, companies, plants
├── master/  party/  numbering/   masters, parties, document numbers per plant and financial year
├── store/           godowns, GRN / issue / adjustment, stock engine and ledger
├── production/      body preparation, press, glaze, kiln, quality, finishing, packing
├── fg/              finished-goods godowns, pillars, stock, warehouse documents
├── sales/           sales orders, loading plan, dispatch
├── dashboard/       the owner's dashboard
├── maintenance/     breakdowns, downtime, preventive jobs
├── energy/          meters, reading rounds, energy use
├── lab/             raw-material lab tests
└── search/          global search

src/main/resources/db/migration/   V1 … V19 (Flyway)

frontend/src/
├── api/  auth/  layout/  i18n/   HTTP client and types, login, sidebar and phone bar, English + Gujarati
└── features/        one folder per module
```

---

## Roadmap

| Step | Module | Status |
|---|---|---|
| 1 | Foundation: companies, plants, users, roles, JWT login; React frontend, PC and phone layouts, English / Gujarati | ✅ Done |
| 2 | Masters: product masters, materials, machines, parties; global search | ✅ Done |
| 3 | Raw-material store: GRN, issues, adjustments, stock and ledger | ✅ Done |
| 4 | Production: body preparation to packing, with lots traced end to end | ✅ Done |
| 5 | Finished goods: godowns, pillars, warehouse documents | ✅ Done |
| 6 | Smart put-away, sales orders from loading slips, loading and dispatch | ✅ Done (invoicing and Tally sync next) |
| 7 | Owner dashboard | ✅ Done |
| 8 | Maintenance, energy, raw-material lab tests | ✅ Done |
| 9 | Costing and labour | Next |
| 10 | Analytics and predictions | Planned |
