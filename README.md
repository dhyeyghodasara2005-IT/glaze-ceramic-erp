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
└── user/            users, roles, permissions, first-admin bootstrap

src/main/resources/
├── application.yml          settings for every environment
├── application-dev.yml      settings for your laptop
└── db/migration/V1__*.sql   database tables (Flyway)

frontend/src/
├── api/             HTTP client, TypeScript copies of the DTOs, error handling
├── auth/            login state, login/change-password pages, route guards
├── layout/          sidebar, top bar, plant switcher, language switch
├── i18n/            English + Gujarati texts
└── features/        one folder per module (companies, plants, roles, users)

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
| 2 | Masters: designs, sizes, grades, shades, materials, machines, dealers, suppliers | ⏭ Next |
| 3 | Raw material & store (GRN, stock, issue) | |
| 4 | Production stage entries (mobile) + batch tracking | |
| 5 | Finished goods stock by grade/shade/caliber | |
| 6 | Sales orders, dispatch, Tally Prime sync | |
| 7 | Owner dashboard | |
| 8 | QC, maintenance, gas/power meters | |
| 9 | Costing, labour | |
| 10 | Analytics & predictions (Python service) | |
