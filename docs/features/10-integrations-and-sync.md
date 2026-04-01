# Feature: Integrations and Data Sync

## Purpose

Connect SarvashikshaAI with external educational content and teacher data sources.

## Integrations

## 1) OpenAI Integration

- Used for:
  - teaching explanations
  - quiz generation
  - reading passage generation
  - reading evaluation
- Core client/service path includes `OpenAIClient` and downstream domain services.

## 2) Google Sheets Integration

- `GoogleSheetsService` + `StudentSyncService`
- Syncs student master data from teacher sheet into local DB (`students`).
- Also syncs assembly links/thought-related config paths where applicable.

## 3) URL/PDF/Image Extraction

- `UrlContentService` extracts textual context from provided links.
- `FileExtractionService` handles PDF text and image payload preparation.
- Used mainly by quiz generation flow.

## Startup Seed/Bootstrap Behavior

- `GradeRefBootstrap` seeds canonical grades when missing.
- `AuthBootstrapSeeder` creates default auth users when enabled.
- `MenuItemBootstrapSeeder` seeds default menu rows when missing.

## Flyway + Schema Management Notes

- Flyway migration scripts exist for core auth/menu tables.
- Runtime includes compatibility handling for environments where DB engine version is newer than Flyway's direct support.

