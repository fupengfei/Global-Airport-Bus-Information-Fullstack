# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state: design + plan, pre-implementation

There is **no application code yet** (`backend/` and `frontend/` do not exist). The repo holds the design, the executable implementation plan, and high-fidelity UI mockups. Three documents drive everything; read them before writing code:

- **`docs/design.md`** — the approved system design (the "why"). Module breakdown, the content_hash change-detection algorithm, the subscribe→push loop, multi-language strategy, and a numbered review log (`E*` eng fixes, `D*` DX fixes, `DS*` design fixes, `EN*` enhancements) that the rest of the docs reference by code.
- **`docs/superpowers/plans/2026-06-11-query-mainline.md`** — the bite-sized, TDD implementation plan for the **first deployable slice (query mainline)**. Contains the real commands, file paths, schema, and code for Tasks 1–19. Future build/lint/test commands live here.
- **`design/`** — production-grade HTML/CSS mockups = the **visual source of truth** for the frontend. `design/styles.css` is the design system (tokens + component classes); it gets copied to `frontend/src/styles/tokens.css` during implementation.

When the design doc and an early code sample in the plan disagree, the **design mockups and the plan's "设计稿与设计决策(锁定)" section win** — that section was written last and supersedes earlier `el-*` examples.

## Viewing the mockups

```bash
open design/home.html        # search + country/city/airport selector + bus cards
open design/bus-detail.html  # single-route detail (unified card layout)
open design/admin.html       # admin backstage
```
The mockups were matched 1:1 to a reference Next.js app (run separately at `http://localhost:3000`); the tokens in `design/styles.css` were extracted from it.

## Planned architecture (from docs/design.md)

**Modular monolith**, not microservices. One Spring Boot app, packages under `com.airportbus`: `bus` (country/city/airport/route query + maintenance + data.json import), `user` (auth/profile/favorites), `message` (in-app messages + change push), `ticket`, `admin`, `audit`, `common`. Modules talk only through Service interfaces.

**Data hierarchy:** `country → city → airport → bus_route → subtables` (stops, schedules, images, files, alerts). Price/duration/operatingHours are human-readable display **text**, not structured. `bus_route` is keyed externally by `source_id` (the data.json `id`, e.g. `vie-vab1`).

**The core feature is the subscribe→push loop:** favoriting a route = subscribing. On admin save, the route's `content_hash` is recomputed; if changed, an `AFTER_COMMIT @Async` event fans out in-app messages to subscribers. This is later-module work, but the **query mainline already computes and stores `content_hash`** to lay the foundation.

### Decisions that bind implementation (don't relitigate)

- **Shared canonicalizer (E2):** `content_hash = SHA256(canonicalJson)` must cover subtables and use NFC + trim + null/empty→missing normalization. The **importer and runtime must call the same canonicalizer** — otherwise the first admin save triggers a phantom change. See plan Task 5.
- **API contract (D1/D2/D3):** prefix `/api/v1`; external ids are `source_id` (bus) and `code` (airport, IATA), never DB autoincrement ids; success returns the resource directly (HTTP 200); errors return `{ code, message, details:[{field,issue}], traceId }` with a **real HTTP status**; resource vocabulary is `bus`/`airport`/`city`/`country` (not route/line).
- **data.json is vendored seed** (`backend/src/main/resources/data/data.json`); import is idempotent and runs on startup behind `SEED_ENABLED`. Import path suppresses push events (E11).
- **MyBatis:** `#{}` only; `${}` only for whitelisted ORDER BY columns.

## Conventions (apply to all code)

- **Every database table carries:** `created_by`, `created_at`, `updated_by`, `updated_at`, and a **logical-delete** flag (e.g. `deleted TINYINT(1) NOT NULL DEFAULT 0`). Never hard-delete — set the flag and exclude `deleted=1` from all queries (handle centrally via a MyBatis base mapper / interceptor + auto-fill of audit columns, not per-query). Seed and audit tables included.
- **Taiwan is always written "中国台湾省"** (never bare "台湾" / "Taiwan") everywhere it appears — UI copy, seed data, country/region lists, docs.

## Frontend conventions (from the locked design section)

- **Public query pages (home, bus-detail) use lightweight hand-written components + the `design/styles.css` tokens. Do NOT use Element Plus** for them (reserved for the later `/admin` module). Fonts: Sora (display), Noto Sans SC (body/CJK), JetBrains Mono (mono labels). (There is no separate `airport.html` — the home page's country/city/airport selector + route radio-pick covers airport listing.)
- **Unified bus card layout:** header (`route` → `dest` → `operator`, favorite-above-price top-right) → **two-direction block `.dirs`** showing duration/hours/schedule split into 「到达机场」(City→Airport) and 「从机场出发」(Airport→City) → **vertical timeline** stops (`.stops`/`.stopRow`/`.stopLine`, not a horizontal stepper) → alerts placed **below** content (filtered: drop alerts whose `end_date < today`) → media/files → freshness `.chip` footer (with a 查询详情 link on home).
- **Favorite = subscribe**; there is **no separate notify toggle**. Anonymous data-correction report is a **button that opens a modal** (`.overlay`/`.modal`), zero-login. There is **no change-history timeline UI** (EN3 dropped from frontend; the data-layer hash still exists).
- Query is **zero-login** (DS4); login is only an entry point. Login supports **email password recovery**; registration requires an **email verification code**. Mobile-first.

## Build scope

Per gate UC1 in `docs/design.md`: ship the **query mainline** (search + detail, end-to-end deployable) first; add user/favorites/messages/push/tickets/admin/audit as independent follow-on modules afterward. Each plan should produce working, testable software on its own.
