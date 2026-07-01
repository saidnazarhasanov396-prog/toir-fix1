# Counteragent Frontend Migration Report

Date: 2026-07-01
Scope: frontend follow-up only. Backend API and database contract were refactored; no frontend files were changed in this branch.

## Backend Contract Change

Counteragent payloads no longer expose or accept these fields:

- `taxNumber`
- `baseInn`
- `contactPerson`
- `phone`
- `email`
- `specialization`

Use these fields instead:

- `inn`: string, optional, exactly 9 digits when provided
- `contactName`: string, optional
- `contactPosition`: string, optional
- `contactPhone`: string, optional
- `contactEmail`: string, optional email

Unchanged fields remain:

- `id`
- `code`
- `name`
- `address`
- `directorName`
- `bankName`
- `bankAccount`
- `mfo`
- `status`

## API Behavior To Reflect In UI

`code` is returned by the backend for display/search, but it is backend-generated. Do not include `code` in `POST /api/v1/counteragents` or `PUT /api/v1/counteragents/{id}` payloads.

`POST /api/v1/counteragents` and `PUT /api/v1/counteragents/{id}` now reject duplicate non-empty `inn` values among non-deleted counteragents with HTTP 409.

`inn` is nullable, so the UI should allow creating/updating a counteragent without INN. If the user enters INN, validate exactly 9 digits before submit.

Search now matches `code`, `name`, `inn`, `contactName`, `contactPhone`, and `contactEmail`.

## Frontend Work Items

1. Update counteragent TypeScript models and form payload builders to replace legacy fields with `inn`, `contactName`, `contactPosition`, `contactPhone`, and `contactEmail`.
2. Update create/edit forms: rename Tax number to INN, remove Base INN and Specialization, split Contact person/Phone/Email into the structured contact fields.
3. Add client-side validation for optional `inn`: empty is valid, otherwise exactly 9 digits.
4. Update tables/detail views/search placeholders to show INN and structured contact information.
5. Update mocks, fixtures, and tests that still use `taxNumber`, `baseInn`, `contactPerson`, `phone`, `email`, or `specialization` on counteragents.
6. Check repair warranty UI surfaces that display counteragent contact snapshots; backend now sources these from `contactName`, `contactPhone`, and `contactEmail`.

## Suggested Payload Shape

```json
{
  "name": "Tashkent Service LLC",
  "inn": "123456789",
  "contactName": "Ali Valiyev",
  "contactPosition": "Supply manager",
  "contactPhone": "+998901234567",
  "contactEmail": "ali@example.com",
  "address": "Tashkent",
  "directorName": "Director Name",
  "bankName": "Bank Name",
  "bankAccount": "20208000123456789001",
  "mfo": "00444",
  "status": "ACTIVE"
}
```
