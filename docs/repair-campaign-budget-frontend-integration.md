# Repair Campaign Budget Frontend Integration

Backend scope implemented: Repair Campaign now links to Budget without becoming a separate finance ledger.

The financial source of truth remains:

`MaintenanceBudget -> BudgetLine -> ActualCost`

Repair Campaign is the operational/project view:

`RepairCampaign -> Stage -> WorkOrder / ContractorWork / Material / Labor -> ActualCost -> BudgetLine`

## New request fields

### RepairCampaignRequest

Used by:

- `POST /api/v1/repair-campaigns`
- `PUT /api/v1/repair-campaigns/{id}`

New optional field:

```json
{
  "maintenanceBudgetId": "uuid"
}
```

Rules:

- Budget must exist and not be deleted.
- Budget year must equal campaign `year`.
- For non-`CROSS_DEPARTMENT` campaigns, budget department must match campaign `departmentId` when both are present.
- Existing campaigns may leave this field null.
- A linked campaign can still use manual `totalBudget`; budget module values are exposed separately.

### RepairCampaignStageDto

Used by:

- `POST /api/v1/repair-campaigns/{id}/stages`
- `PUT /api/v1/repair-campaigns/{id}/stages/{stageId}`

New optional field:

```json
{
  "budgetLineId": "uuid"
}
```

Rules:

- Campaign must have `maintenanceBudgetId` before a stage can link a budget line.
- The budget line must belong to the campaign maintenance budget.
- Existing stages may leave this field null.
- `plannedCost` remains the campaign planning value. The linked budget line planned amount is returned separately.

### WorkOrderRequest

Used by normal work order create and campaign stage work order create.

New optional field:

```json
{
  "budgetLineId": "uuid"
}
```

Rules:

- When work order is created under a campaign stage and `budgetLineId` is omitted, backend inherits the stage `budgetLineId`.
- When `budgetLineId` is provided for a campaign work order, it must belong to the campaign maintenance budget.
- Existing work order create payloads remain valid.

## Updated response fields

### RepairCampaignDto

New fields:

```json
{
  "maintenanceBudgetId": "uuid",
  "budgetPlanned": 100000.0,
  "budgetActual": 25000.0,
  "budgetRemaining": 75000.0,
  "budgetStatus": "APPROVED"
}
```

Use these for the linked budget card. Do not treat `budgetActual` as campaign actual if the maintenance budget can include non-campaign work.

Campaign actual fields remain campaign-scoped:

- `approvedActual`
- `pendingActual`
- `totalActual`
- `variance`

### RepairCampaignStageDto

New fields:

```json
{
  "budgetLineId": "uuid",
  "budgetLinePlanned": 20000.0,
  "budgetLineActual": 7500.0,
  "budgetLineRemaining": 12500.0
}
```

Use these for stage-level budget control.

Stage operational fields remain stage-scoped:

- `plannedCost`
- `approvedActual`
- `pendingActual`
- `actualCost`

### WorkOrderDto

New field:

```json
{
  "budgetLineId": "uuid"
}
```

Use this to display or preselect the work order accounting allocation.

## New endpoints

### Budget summary

`GET /api/v1/repair-campaigns/{id}/budget-summary`

Response:

```json
{
  "campaignId": "uuid",
  "maintenanceBudgetId": "uuid",
  "budgetStatus": "APPROVED",
  "campaignPlannedBudget": 100000.0,
  "campaignApprovedActual": 25000.0,
  "campaignPendingActual": 4000.0,
  "linkedBudgetPlanned": 150000.0,
  "linkedBudgetActual": 60000.0,
  "linkedBudgetRemaining": 90000.0,
  "unallocatedActualCostCount": 1,
  "unallocatedActualCostAmount": 750.0,
  "stages": [
    {
      "stageId": "uuid",
      "stageName": "Preparation",
      "budgetLineId": "uuid",
      "stagePlannedCost": 20000.0,
      "stageApprovedActual": 7500.0,
      "stagePendingActual": 500.0,
      "budgetLinePlanned": 20000.0,
      "budgetLineActual": 7500.0,
      "budgetLineRemaining": 12500.0,
      "variance": 12500.0
    }
  ]
}
```

Recommended UI:

- Show campaign planned vs campaign approved/pending actual as the project view.
- Show linked budget planned/actual/remaining as the finance ledger view.
- Show `unallocatedActualCostCount` and `unallocatedActualCostAmount` as a warning.

### Available budget lines

`GET /api/v1/repair-campaigns/{id}/available-budget-lines`

Response:

```json
[
  {
    "id": "uuid",
    "costCategoryId": "uuid",
    "description": "Contractor repair",
    "plannedAmount": 50000.0,
    "actualAmount": 12000.0
  }
]
```

Use this for stage budget-line selectors after a campaign maintenance budget is selected.

If the campaign has no linked maintenance budget, the endpoint returns an empty array.

## Actual cost behavior

Actual cost approval behavior is unchanged:

- Pending actual costs are created first.
- Finance approval updates `BudgetLine.actualAmount`.
- Finance approval updates `MaintenanceBudget.totalActual`.

New defaulting behavior:

- Manual actual cost with `workOrderId` and no `budgetLineId` uses work order budget line, then stage budget line.
- Contractor work acceptance uses linked work order or stage budget line.
- Material issue actual cost uses linked work order or stage budget line.
- Labor entry actual cost uses linked work order or stage budget line.

Frontend can still explicitly send `budgetLineId` when users override allocation.

## Close behavior

Campaign close now still blocks pending campaign actual costs.

Campaign close also blocks approved campaign actual costs that are not allocated to a budget line. Use the budget summary warning fields to guide users before close.
