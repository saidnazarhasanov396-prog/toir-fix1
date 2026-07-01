# Counteragent Module Field Report

Generated on 2026-07-01 from the current backend code and migrations.

## Short Summary

The `counteragent` module is a unified vendor/third-party module. It exists because the system previously had two separate concepts:

- `suppliers`: companies that provide spare parts, equipment, warranty support, and purchase order deliveries.
- `contractors`: companies that perform outsourced maintenance/repair work and have service contracts.

`counteragents` combines the shared identity/legal/contact fields from both modules so the rest of the system can point to one table instead of choosing between supplier and contractor tables. In other words, a counteragent can be a supplier, a contractor, or both.

The migration `V20260630_1__counteragents_foundation.sql` creates `counteragents`, copies existing contractors and suppliers into it, and then adds `counteragent_id` references to purchase orders, procurement, equipment, spare parts, work orders, contractor contracts, and contractor works.

## Main Counteragent Fields

| Field | Meaning | Origin | Why we need it |
| --- | --- | --- | --- |
| `id` | Unique counteragent record id. | Base entity/shared | Used as the one vendor id across procurement, equipment, warranty, work orders, contracts, and actual costs. |
| `code` | Human-friendly unique code, e.g. `CA-2026-001`. | Supplier + contractor | Needed for lists, search, integrations, and stable references that are easier to read than UUIDs. If not provided, backend generates a yearly sequence. |
| `name` | Legal/company/display name. | Supplier + contractor | Main display value everywhere: purchase orders, work orders, reports, financial review. |
| `taxNumber` | Tax identifier/INN-style value. | Supplier + contractor | Needed for legal identity, duplicate matching, financial documents, and migration matching between old supplier/contractor records. |
| `baseInn` | Normalized/base tax id. If `taxNumber` has a suffix like `123_1`, base becomes `123`. | Mainly supplier, then generalized | Needed to group branches or duplicate supplier records under one legal base identity. The service derives it automatically when not provided. |
| `contactPerson` | Main person to contact at the company. | Supplier + contractor | Used by procurement/warranty/contractor coordination so users know who to call or email. |
| `phone` | Company/contact phone. | Supplier + contractor | Operational contact for delivery, warranty, contractor dispatch, acceptance issues. |
| `email` | Company/contact email. | Supplier + contractor | Formal communication, purchase order sending, warranty and contract communication. Validated as email in requests. |
| `address` | Physical/legal address. | Supplier | Supplier table had this first; it is useful for deliveries, documents, and legal/vendor master data. Contractors did not have this in the baseline, but the unified model can now store it for all vendors. |
| `specialization` | What the company does, e.g. pump repair, electrical works, spare parts. | Contractor | Contractor table had this first. It helps select vendors for outsourced maintenance and is searchable in the counteragent list. |
| `directorName` | Director/general manager name. | Supplier + contractor legal details | Added to both supplier and contractor before unification. Needed for contracts, invoices, official documents. |
| `bankName` | Bank name. | Supplier + contractor legal details | Needed for payment and contract/payment document details. |
| `bankAccount` | Settlement/account number. | Supplier + contractor legal details | Needed for payment details and finance workflows. |
| `mfo` | Bank MFO/code. | Supplier + contractor legal details | Needed for local banking/payment details. |
| `status` | `ACTIVE`, `INACTIVE`, or `BLOCKED`. | Contractor status + supplier `active` converted | Controls whether a counteragent can be selected. `CounteragentWorkService` requires an active counteragent for work lifecycle actions. |

## Counteragent Contract Fields

API: `/api/v1/counteragent-contracts`  
DTO: `CounteragentContractDto`  
Storage entity/table: still `ContractorContract` / `contractor_contracts`, now linked by `counteragent_id`.

| Field | Meaning | Origin | Why we need it |
| --- | --- | --- | --- |
| `id` | Unique contract id. | Contractor module | Identifies a specific service/vendor contract. |
| `counteragentId` | Vendor/company that owns the contract. | Counteragent unification | Replaces old `contractor_id` for new flows. Allows the same vendor master record to hold service contracts. |
| `number` | Contract number. | Contractor module | Required, unique business identifier for legal and finance tracking. |
| `subject` | What the contract is about. | Contractor module | Describes the services/goods covered. Required for contract meaning. |
| `startDate` | Date contract becomes valid. | Contractor module | Used to check whether counteragent work is allowed. |
| `endDate` | Date contract expires. | Contractor module | Used with `startDate` for active contract validation. |
| `amount` | Contract amount/value. | Contractor module | Needed for budget and finance reference. |
| `status` | `DRAFT`, `ACTIVE`, `EXPIRED`, `TERMINATED`. | Contractor module | `CounteragentWorkService` requires at least one `ACTIVE` contract valid today before creating/starting/accepting counteragent work. |

## Counteragent Work Fields

API: `/api/v1/counteragent-works`  
DTO: `CounteragentWorkDto`  
Storage entity/table: still `ContractorWork` / `contractor_works`, now linked by `counteragent_id`.

| Field | Meaning | Origin | Why we need it |
| --- | --- | --- | --- |
| `id` | Unique outsourced work id. | Contractor module | Identifies the external work record. |
| `counteragentId` | Vendor doing the work. | Counteragent unification | Replaces old contractor-only reference in new flows. |
| `workOrderId` | Linked maintenance work order. | Contractor module / maintenance | Required by lifecycle logic. Counteragent work must belong to an approved/in-progress work order. |
| `description` | Description of outsourced work. | Contractor module | Required so the work scope is clear. |
| `status` | `DRAFT`, `IN_PROGRESS`, `COMPLETED`, `ACCEPTED`, `CANCELLED`. | Contractor module | Drives lifecycle: create draft, start, complete, accept. |
| `startedAt` | Timestamp when external work starts. | Contractor module | Set when `/start` is called. |
| `completedAt` | Timestamp when external work is marked complete. | Contractor module | Set when `/complete` is called. |
| `cost` | Expected/final work cost. | Contractor module / finance | Used to create an `ActualCost` after acceptance when positive. |
| `result` | Completion result text. | Contractor module | Required when completing the work. Records what was done. |
| `acceptanceComment` | Acceptance note from responsible person. | Contractor module | Required on acceptance; explains acceptance decision/context. |
| `createdById` | User who created the work. | Actor-stamped entity | Audit/ownership metadata inherited from `ActorStampedEntity`. |
| `acceptedById` | User/person accepting the work. | Contractor module | Required on acceptance; identifies who accepted the work. |
| `acceptedAt` | Timestamp of acceptance. | Contractor module | Needed for audit, finance timing, and accepted work history. |

## Performance Fields

API: `/api/v1/counteragents/{id}/performance`  
DTO: `CounteragentPerformanceDto`

These fields are calculated from purchase orders using `counteragent_id`, so today they mostly reflect supplier/procurement performance.

| Field | Meaning | Why we need it |
| --- | --- | --- |
| `counteragentId` | Vendor being measured. | Links the metrics back to the counteragent. |
| `totalOrders` | Count of purchase orders for this counteragent. | Shows procurement volume. |
| `deliveredOrders` | Count of purchase orders with `RECEIVED` status and `receivedDate`. | Shows completed delivery volume. |
| `onTimeDeliveries` | Delivered orders received on or before expected date. | Measures supplier reliability. |
| `lateDeliveries` | Delivered orders received after expected date. | Highlights delivery risk. |
| `averageDeliveryDays` | Average days from order date to received date. | Helps compare supplier speed. |
| `totalSpend` | Sum of purchase order total amounts. | Shows spend concentration by counteragent. |

## Why Some Old Names Still Exist

Some Java classes and database tables still use contractor naming:

- `ContractorContract`
- `ContractorWork`
- `contractor_contracts`
- `contractor_works`

But the new API exposes them as:

- `CounteragentContractDto`
- `CounteragentWorkDto`
- `/api/v1/counteragent-contracts`
- `/api/v1/counteragent-works`

This means the business language has moved to `counteragent`, while the storage/model names have not been fully renamed yet. The new field `counteragent_id` is the important part. It lets old contractor work and contracts belong to the unified vendor record.

## Where Counteragent Is Used

`counteragent_id` was added to these areas:

- `purchase_orders`: supplier purchase orders now point to counteragents.
- `procurement_requests`: procurement can select a counteragent.
- `equipment.counteragent_id`: equipment supplier/source vendor.
- `equipment.warranty_counteragent_id`: warranty provider.
- `repair_requests.warranty_counteragent_id`: warranty vendor captured for repair/warranty flow.
- `procurement_request_lines.warranty_counteragent_id`: warranty vendor per procurement line.
- `spare_parts.preferred_counteragent_id`: preferred vendor for replenishment.
- `contractor_contracts.counteragent_id`: service contract vendor.
- `contractor_works.counteragent_id`: outsourced work vendor.
- `work_orders.counteragent_id`: work order assigned to an external vendor.

## Field Origin Matrix

| Field group | Supplier reason | Contractor reason | Keep in counteragent? |
| --- | --- | --- | --- |
| Code/name/contact/phone/email/tax number | Yes | Yes | Yes, shared vendor identity. |
| Address | Yes | Not originally | Yes, useful for all legal/vendor records. |
| Specialization | Not originally | Yes | Yes, useful for contractor selection and possibly supplier categories. |
| Director/bank/account/MFO | Yes | Yes | Yes, needed for legal and payment details. |
| Status | Supplier had `active`; contractor had status enum | Yes | Yes, unified selection control. |
| Contracts/work lifecycle | No, not purchase-order supplier logic | Yes | Yes, because a counteragent can be a contractor. |
| Purchase order performance | Yes | No direct contractor use today | Yes, because a counteragent can be a supplier. |

## Practical Recommendation

Going forward, new code should use `counteragentId` instead of `supplierId` or `contractorId` when the business meaning is "external company/vendor." Old supplier/contractor fields can remain temporarily for migration compatibility, but user-facing APIs and new relationships should prefer counteragent.

The one thing still worth cleaning later is naming consistency: `ContractorWork` and `ContractorContract` are now counteragent-backed records, but their class/table names still say contractor. That is not wrong functionally, but it can confuse developers reading the code.
