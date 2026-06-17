# Repair Request Template Actions Backend Audit - 2026-06-16

## Scope

Frontendda repair request create modaliga alohida template/action bosqichi, multi-select UI, action select-all/clear, inline action create, maintenance template multi equipment type UI va action specialist fieldlari qo'shildi. Backend hozircha bu kontraktning faqat bir qismini qo'llaydi; quyidagi o'zgarishlar production persistence uchun kerak.

## Current Backend State

- `RepairRequestRequest`, `RepairRequest`, `RepairRequestDto` faqat bitta `templateId` bilan ishlaydi.
- `RepairRequestService.create(...)` `validateMaintenanceTemplate(request.templateId())` qiladi va `repair_requests.template_id`ga bitta UUID yozadi.
- `MaintenanceTemplateRequest` va `MaintenanceTemplate` faqat bitta `equipmentTypeId` talab qiladi.
- `MaintenanceOperation` action reference (`action_id`) saqlaydi, lekin optional specialist/responsible user saqlamaydi.
- `WorkOrderTask`da `assignedToId` bor va work order detail source template/operation reference qaytara oladi, lekin template/regulation operationdan specialist ko'chirish yo'q.
- Repair request detail uchun backend template action reference DTO qaytarmaydi; frontend hozir bitta `templateId` orqali template detailni alohida chaqiradi.

## Required Backend Changes

1. Multi-template repair request persistence:
   - Add table `repair_request_templates(id, repair_request_id, template_id, sequence, created_at, ...)`.
   - Keep `repair_requests.template_id` as legacy primary/first template during transition.
   - Extend `RepairRequestRequest` with `templateIds: List<UUID>` and normalize `templateId` + `templateIds`.
   - Extend `RepairRequestDto` with selected template summaries and action reference rows.

2. Template action selection persistence:
   - Add table `repair_request_template_actions(id, repair_request_id, template_id, operation_id, action_id, specialist_id nullable, sequence, custom_name nullable)`.
   - Validate selected action/operation belongs to one of selected templates unless it is an explicit custom repair-request action.
   - Preserve selected order for work order generation and detail reference.

3. New action attach flow:
   - Existing `POST /api/v1/maintenance-actions` creates global action reference only.
   - Add endpoint or service method to attach newly created actions to a selected template or to the repair request action table.
   - Enforce inactive/deleted action and template checks.

4. Optional specialist per action:
   - Add nullable `specialist_id` to `maintenance_operations`.
   - Extend `MaintenanceOperationDto` request/response with `specialistId` and `specialistName`.
   - In `WorkOrderService` template operation copy logic, set `WorkOrderTask.assignedToId` from operation specialist when present.
   - Apply the same mapping for maintenance automation/PPR-generated work orders that clone template operations.

5. Multi equipment type templates:
   - Replace single ownership with join table `maintenance_template_equipment_types(template_id, equipment_type_id)`.
   - Keep `maintenance_templates.equipment_type_id` as primary legacy field until all clients migrate.
   - Update repository filters and stats to match any selected equipment type.
   - Validate template use against equipment's type through the join table.

6. Reference/read APIs:
   - Add repair request detail action reference DTO, e.g. `RepairRequestActionReferenceDto(templateId, templateCode, operationId, actionId, name, durationHours, requiredSkill, specialistId, specialistName)`.
   - Work order detail already exposes task source template/operation fields; add specialist names if `assignedToId` came from template operation.
   - Consider a dedicated `GET /api/v1/repair-requests/{id}/action-references` if DTO size becomes too large.

## Validation And Tests Needed

- `RepairRequestServiceTest`: create request with multiple templates and action subsets; legacy `templateId` still works.
- `RepairRequestController` MVC/security tests: reject actions outside selected templates; scoped user cannot attach inaccessible template/action.
- `MaintenanceTemplateServiceTest`: create/update template with multiple equipment types; filter by equipment type returns matching templates.
- `WorkOrderServiceTest`: copied template operations preserve source ids and optional specialist assignment.
- Migration test/seed: existing rows with `repair_requests.template_id` and `maintenance_templates.equipment_type_id` backfill join rows.

## Frontend Compatibility Note

Current frontend sends only the first selected template as legacy `templateId` in repair request create payload so today's backend keeps accepting requests. Extra selected template/action/specialist UI state needs the backend work above before it becomes durable.
