# Frontend Integration Report: Work Order Approval

Date: 2026-05-31

This report explains how the backend approval flow works for work orders and what the frontend should implement.

## Summary

Work order approval is not a single direct status update from the work order endpoint.

The flow has two steps:

1. The frontend asks the backend to create or reuse an approval request for the work order.
2. The assigned approver approves or rejects that approval request through the approvals API.

Only after the approval request reaches final `APPROVED` state does the backend update the work order itself to `APPROVED`.

## Relevant Statuses

### Work Order Status

```text
DRAFT
PLANNED
APPROVED
IN_PROGRESS
SUSPENDED
COMPLETED
CLOSED
CANCELLED
```

Only work orders in `DRAFT` or `PLANNED` can enter the approval workflow.

### Approval Request Status

```text
PENDING
APPROVED
REJECTED
CANCELLED
```

### Approval Step Decision

```text
PENDING
APPROVED
REJECTED
```

## Main Flow

### 1. Request Approval For Work Order

Use the work order endpoint to create or reuse an approval request.

```http
POST /api/v1/work-orders/{workOrderId}/approve?approverId={approverId}
```

Required permission:

```text
WORK_ORDER_APPROVE
```

Example:

```js
await fetch(`/api/v1/work-orders/${workOrderId}/approve?approverId=${approverId}`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`,
  },
});
```

Backend behavior:

- Validates that the work order exists.
- Validates department scope access.
- Allows only `DRAFT` or `PLANNED` work orders.
- Creates or reuses a pending approval request with `documentType = WORK_ORDER`.
- Sends a notification to the current approver.
- Returns the current work order DTO.

Important frontend note:

The response work order will usually still have status `DRAFT` or `PLANNED`. This endpoint starts the approval process; it does not finalize approval.

If a pending approval already exists for the same work order, the backend reuses it instead of creating a duplicate.

### 2. Load Approval Request For Work Order

After requesting approval, fetch approval requests for the work order.

```http
GET /api/v1/approvals?documentType=WORK_ORDER&documentId={workOrderId}
```

Required permission:

```text
APPROVAL_READ
```

Example:

```js
const response = await fetch(
  `/api/v1/approvals?documentType=WORK_ORDER&documentId=${workOrderId}`,
  {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  }
);

const page = await response.json();
const approvalRequest = page.content?.[0];
```

Expected approval shape:

```json
{
  "id": "9343bdf0-4f5f-420d-a6e2-4686b97846da",
  "documentType": "WORK_ORDER",
  "documentId": "0d35e8a0-7b2a-4db4-a1c4-45571ce98c9d",
  "title": "Work order approval: WO-2026-001",
  "requesterId": "c7f6a5f1-855f-4dd5-9273-56f5c8abf8a9",
  "status": "PENDING",
  "currentStep": 1,
  "completedAt": null,
  "description": "Approval workflow request for work order WO-2026-001",
  "createdAt": "2026-05-31T12:00:00Z",
  "steps": [
    {
      "id": "bd48da8d-806c-4425-8143-e2b9aa9e6724",
      "stepNumber": 1,
      "approverId": "c7f6a5f1-855f-4dd5-9273-56f5c8abf8a9",
      "approverRole": "WORK_ORDER_APPROVER",
      "decision": "PENDING",
      "decidedAt": null,
      "comment": null
    }
  ]
}
```

### 3. Approve The Approval Request

Use the approval request ID, not the work order ID.

```http
POST /api/v1/approvals/{approvalRequestId}/approve
Content-Type: application/json
```

Required permission:

```text
APPROVAL_APPROVE
```

Body:

```json
{
  "approverId": "c7f6a5f1-855f-4dd5-9273-56f5c8abf8a9",
  "comment": "Approved"
}
```

Example:

```js
await fetch(`/api/v1/approvals/${approvalRequestId}/approve`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    approverId: currentUserId,
    comment,
  }),
});
```

Backend behavior:

- Validates the approval request is `PENDING`.
- Finds the current step using `currentStep`.
- Validates that `approverId` matches the current step approver.
- Marks the current step as `APPROVED`.
- If there are more steps, advances to the next step.
- If there are no more steps, marks the approval request as `APPROVED`.
- For `WORK_ORDER`, final approval calls the work order approval logic.
- The work order status becomes `APPROVED`.
- The work order `approvedById` is set to the approver ID.

Frontend should refresh both:

```http
GET /api/v1/approvals?documentType=WORK_ORDER&documentId={workOrderId}
GET /api/v1/work-orders/{workOrderId}
```

### 4. Reject The Approval Request

Use the approval request ID.

```http
POST /api/v1/approvals/{approvalRequestId}/reject
Content-Type: application/json
```

Required permission:

```text
APPROVAL_REJECT
```

Body:

```json
{
  "approverId": "c7f6a5f1-855f-4dd5-9273-56f5c8abf8a9",
  "comment": "Please update planned end date"
}
```

Backend behavior:

- Marks the current approval step as `REJECTED`.
- Marks the approval request as `REJECTED`.
- Sends a notification to the requester.
- Does not change the work order status.

Important frontend note:

Rejected approval does not automatically move the work order to `CANCELLED` or a separate rejected status. The work order usually remains `DRAFT` or `PLANNED`.

The frontend should display the rejected approval request and its comment so the user knows what needs to be corrected.

## Suggested Frontend UI Behavior

### Work Order Detail Page

Show approval controls only when:

- Work order status is `DRAFT` or `PLANNED`.
- User has permission to request work order approval.
- User can access the work order department scope.

Primary action:

```text
Request approval
```

After clicking it:

- Call `POST /api/v1/work-orders/{id}/approve`.
- Refresh work order detail.
- Load related approval request.
- Show approval status as `Pending approval`.

### Approval Panel

For each related approval request, show:

- Approval status.
- Current step.
- Approver.
- Step decision.
- Comment.
- Decided date.

If the current user is the current step approver, show:

- Approve button.
- Reject button.
- Optional comment input.

The approve/reject buttons should call the approvals API, not the work order API.

### Status Labels

Recommended display mapping:

```text
Work order DRAFT + no approval request: Draft
Work order PLANNED + no approval request: Planned
Approval request PENDING: Pending approval
Approval request APPROVED + work order APPROVED: Approved
Approval request REJECTED: Approval rejected
Approval request CANCELLED: Approval cancelled
Work order IN_PROGRESS: In progress
Work order COMPLETED: Completed
Work order CLOSED: Closed
```

## Error Cases To Handle

### Work Order Is Not Approvable

Possible response:

```text
Only DRAFT/PLANNED work orders can be approved
```

Frontend behavior:

- Disable request approval action when status is not `DRAFT` or `PLANNED`.
- If the backend still returns this error, refresh the work order because another user may have changed it.

### Wrong Approver

Possible response:

```text
Only designated approver can act on this step
```

Frontend behavior:

- Hide approve/reject actions unless the current user's ID equals the current step `approverId`.
- Refresh approval request after this error.

### Approval Already Completed

Possible response:

```text
Request is not pending: APPROVED
Request is not pending: REJECTED
Request is not pending: CANCELLED
```

Frontend behavior:

- Disable approve/reject actions when approval request status is not `PENDING`.
- Refresh approval state.

### Access Denied

The backend enforces permissions and department scope.

Frontend behavior:

- Hide actions based on permissions where possible.
- Still handle `403` as authoritative.
- Show a permission/access message instead of retrying.

## Minimal Integration Checklist

- Add a `Request approval` action on work orders in `DRAFT` or `PLANNED`.
- Call `POST /api/v1/work-orders/{id}/approve?approverId={approverId}`.
- Fetch related approvals using `GET /api/v1/approvals?documentType=WORK_ORDER&documentId={id}`.
- Render approval request status and current step.
- Approve using `POST /api/v1/approvals/{approvalRequestId}/approve`.
- Reject using `POST /api/v1/approvals/{approvalRequestId}/reject`.
- Refresh both work order and approval data after each action.
- Do not expect the work order to become `APPROVED` until the approval request is finally approved.

## Backend References

- Work order approval request endpoint: `src/main/java/com/toir/controller/WorkOrderController.java`
- Approval decision endpoints: `src/main/java/com/toir/controller/ApprovalController.java`
- Approval workflow service: `src/main/java/com/toir/service/ApprovalService.java`
- Work order status mutation: `src/main/java/com/toir/service/WorkOrderService.java`
