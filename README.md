# Glaze · Ceramic ERP

**Glaze** is an ERP for ceramic tile manufacturing (built first for a GVT 600×1200 plant in Morbi).
It supports many companies, many plants, and users and roles that can be added or removed at any time.

**Stack:** Java 17 · Spring Boot 3.5 · MySQL · Flyway · JWT · React 19 + TypeScript + Vite + Ant Design 6 + motion

---

## Run it on your computer

**Needs:** Java 17, MySQL running locally (`brew services start mysql`).

1. **One-time setup:** create `config/application-local.yml` from the template:
   ```bash
   cp config/application-local.example.yml config/application-local.yml
   ```
   Then fill in the DB password, a JWT secret (`openssl rand -base64 48`) and the first admin password.
   *(On this computer the file already exists.)*

2. **Start the backend:**
   ```bash
   ./mvnw spring-boot:run
   ```
   It is ready when the log says `Started CeramicErpApplication`.

3. **Try the API** in the browser at http://localhost:8080/swagger-ui.html
   - Open `POST /api/auth/login` → *Try it out* → log in with the admin from `config/application-local.yml`
   - Copy the `token`, click **Authorize** (top right), paste it
   - Now every endpoint can be called from the page

4. **Start the frontend** (second terminal; first time run `npm install` inside `frontend/`):
   ```bash
   cd frontend && npm run dev
   ```
   Open http://localhost:5173. On your phone (same Wi-Fi), use the "Network" address it prints.

5. **Run the tests** (uses a separate `ceramic_erp_test` database that is wiped on each run):
   ```bash
   ./mvnw test
   ```
   Frontend type-check: `cd frontend && npm run typecheck`

---

## Project layout

```
src/main/java/com/ceramicerp/
├── common/          shared pieces: BaseEntity (audit columns), errors, paging
├── security/        JWT tokens, login filter, who-can-call-what
├── auth/            login, "who am I", change password
├── organization/    companies and plants
├── user/            users, roles, permissions, first-admin bootstrap
├── master/          sizes, designs, materials, machines …
├── party/           dealers, suppliers, transporters
├── search/          global search (each module plugs in a SearchProvider)
├── numbering/       document numbers per plant, type and financial year
├── store/           godowns, GRN / issue / adjustment, stock engine and ledger
└── production/      body batches, spray dryer runs, silos, design lots, press entries

src/main/resources/
├── application.yml          settings for every environment
├── application-dev.yml      settings for your laptop
└── db/migration/V1__*.sql   database tables (Flyway)

frontend/src/
├── api/             HTTP client, TypeScript copies of the DTOs, error handling
├── auth/            login state, login/change-password pages, route guards
├── layout/          sidebar, top bar, plant switcher, language switch
├── i18n/            English + Gujarati texts
└── features/        one folder per module (companies, plants, masters, parties, store …)

config/application-local.yml   YOUR secrets - git-ignored, never commit
docs/learning/                 explanations of how each step works
```

Every feature module has the same layers:
**Controller** (HTTP) → **Service** (business rules) → **Repository** (database) → **Entity** (table).

---

## Roadmap

| Step | Module | Status |
|---|---|---|
| 1 | Foundation: companies, plants, users, roles, login | ✅ Done |
| 1b | Frontend: login, layout (PC + phone), EN/ગુ, company/plant/role/user screens | ✅ Done |
| 1c | Design: Glaze name + logo, theme, animations, phone bottom bar | ✅ Done |
| 2a | Product masters: sizes, finishes, categories, grades, designs | ✅ Done |
| 2b | Masters: units, material groups, materials, machines/lines | ✅ Done |
| ★ | Global search / Go To (Ctrl+K, Alt+G, /) across all modules | ✅ Done |
| 2c | Parties: dealers, suppliers, transporters (GST-aware, auto codes) | ✅ Done |
| 3 | Raw material store: godowns, GRN, issues, adjustments, stock + ledger (weighted average) | ✅ Done |
| 4a | Production: shifts, body recipes, ball mill charges (auto store issue), slip tests, spray dryer runs | ✅ Done |
| 4b | Design lots, press & dryer shift entries, silo powder tracked oldest-first (lot → spray runs → body batches) | ✅ Done |
| 4c | Glaze & print and kiln shift entries; lot funnel (pressed → glazed → fired, yield); gas per m² | ✅ Done |
| 4d | Cooling-area quality checks (bend, size, expected Premium %), polishing, sorting & packing by grade / shade / caliber; funnel press → box | ✅ Done |
| 5a | Finished goods: godowns & pillars (A1, A2 …), packed boxes into stock by pillar × batch × grade × shade × caliber, godown map, ledger | ✅ Done |
| 5b | Warehouse documents: opening FG stock (batch on the box), breakage write-off by reason, regrading (grade / shade / caliber / pillar) | ✅ Done |
| 6a | Smart put-away: pillar zones, walk order from the loading point, remainder / staging pillars, best sellers, pillar suggestion + mixing warning in the pack entry | ✅ Done |
| 6b | Sales orders from loading slips: dealer or **jobwork principal** (new party type), product names matched to designs and remembered, stock / slip-total / weight / credit checks | ✅ Done (slip PDF reader waits for sample PDFs) |
| 6c | Loading plan (pillars picked by rules: in front, clears a pillar, oldest, most boxes, nearest) with kept boxes; phone loading checklist; confirm loading → stock out, dispatch no., packing list + gate pass; undo dispatch | ✅ Done |
| 6d–6f | Bill + e-way bill on loading, Tally Prime sync, busy-day staging ([plan](docs/plans/06-dispatch-and-smart-storage.md), [backlog](docs/plans/BACKLOG.md)) | 6d waits on answers |
| 7 | Owner dashboard: m² packed with the change on the period before, Premium share, yield, gas per m², dispatched, orders waiting; packed vs dispatched per day; grade split; stage funnel; godown fill; needs-a-look list (each block by permission) | ✅ Done |
| 8 | QC, maintenance, gas/power meters | |
| 9 | Costing, labour | |
| 10 | Analytics & predictions (Python service) | |
