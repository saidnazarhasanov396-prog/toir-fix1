package com.toir.security;

/**
 * Shared {@code @PreAuthorize} expressions for the unified approvals API.
 * Must be compile-time constants (string literal concatenation only).
 */
public final class ApprovalSecurityExpressions {

  private static final String ADMIN = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*')";

  public static final String CAN_CREATE = ADMIN
      + " or hasAuthority('APPROVAL_CREATE')"
      + " or hasAuthority('WORK_ORDER_CREATE')"
      + " or hasAuthority('WORK_ORDER_UPDATE')"
      + " or hasAuthority('REPAIR_REQUEST_CREATE')"
      + " or hasAuthority('REPAIR_REQUEST_UPDATE')"
      + " or hasAuthority('PPR_PLAN_CREATE')"
      + " or hasAuthority('PPR_PLAN_UPDATE')"
      + " or hasAuthority('PPR_PLAN_GENERATE')"
      + " or hasAuthority('PPR_CALENDAR_CREATE')"
      + " or hasAuthority('PPR_CALENDAR_UPDATE')"
      + " or hasAuthority('PPR_CALENDAR_GENERATE')"
      + " or hasAuthority('PPR_TASK_CREATE')"
      + " or hasAuthority('PPR_TASK_UPDATE')"
      + " or hasAuthority('DEFECT_LIST_CREATE')"
      + " or hasAuthority('DEFECT_LIST_UPDATE')"
      + " or hasAuthority('PROCUREMENT_CREATE')"
      + " or hasAuthority('PROCUREMENT_SUBMIT')"
      + " or hasAuthority('ACTUAL_COST_CREATE')"
      + " or hasAuthority('BUDGET_CREATE')"
      + " or hasAuthority('BUDGET_UPDATE')"
      + " or hasAuthority('MAINTENANCE_REGULATION_CREATE')"
      + " or hasAuthority('MAINTENANCE_REGULATION_UPDATE')"
      + " or hasAuthority('MAINTENANCE_EVENT_APPROVE')"
      + " or hasAuthority('EQUIPMENT_COMMISSIONING_CREATE')"
      + " or hasAuthority('EQUIPMENT_COMMISSIONING_SUBMIT')"
      + " or hasAuthority('WORK_ORDER_APPROVE')"
      + " or hasAuthority('REPAIR_REQUEST_APPROVE')"
      + " or hasAuthority('PPR_PLAN_APPROVE')"
      + " or hasAuthority('PPR_CALENDAR_APPROVE')"
      + " or hasAuthority('PPR_TASK_APPROVE')"
      + " or hasAuthority('DEFECT_LIST_APPROVE')"
      + " or hasAuthority('PROCUREMENT_APPROVE')"
      + " or hasAuthority('ACTUAL_COST_APPROVE')"
      + " or hasAuthority('BUDGET_APPROVE')"
      + " or hasAuthority('PLANNED_SHUTDOWN_APPROVE')"
      + " or hasAuthority('PLANNED_SHUTDOWN_REQUEST_APPROVAL')"
      + " or hasAuthority('REPAIR_CAMPAIGN_APPROVE')";

  public static final String CAN_APPROVE = ADMIN
      + " or hasAuthority('APPROVAL_APPROVE')"
      + " or hasAuthority('WORK_ORDER_APPROVE')"
      + " or hasAuthority('REPAIR_REQUEST_APPROVE')"
      + " or hasAuthority('PPR_PLAN_APPROVE')"
      + " or hasAuthority('PPR_CALENDAR_APPROVE')"
      + " or hasAuthority('PPR_TASK_APPROVE')"
      + " or hasAuthority('DEFECT_LIST_APPROVE')"
      + " or hasAuthority('PROCUREMENT_APPROVE')"
      + " or hasAuthority('ACTUAL_COST_APPROVE')"
      + " or hasAuthority('BUDGET_APPROVE')"
      + " or hasAuthority('PLANNED_SHUTDOWN_APPROVE')"
      + " or hasAuthority('REPAIR_CAMPAIGN_APPROVE')"
      + " or hasAuthority('MAINTENANCE_EVENT_APPROVE')"
      + " or hasAuthority('MAINTENANCE_REGULATION_UPDATE')"
      + " or hasAuthority('EQUIPMENT_COMMISSIONING_UPDATE')"
      + " or hasAuthority('PURCHASE_ORDER_APPROVE')"
      + " or hasAuthority('TIMESHEET_APPROVE')";

  public static final String CAN_REJECT = ADMIN
      + " or hasAuthority('APPROVAL_REJECT')"
      + " or hasAuthority('WORK_ORDER_APPROVE')"
      + " or hasAuthority('REPAIR_REQUEST_APPROVE')"
      + " or hasAuthority('REPAIR_REQUEST_REJECT')"
      + " or hasAuthority('PPR_PLAN_APPROVE')"
      + " or hasAuthority('PPR_CALENDAR_APPROVE')"
      + " or hasAuthority('PPR_TASK_APPROVE')"
      + " or hasAuthority('DEFECT_LIST_APPROVE')"
      + " or hasAuthority('PROCUREMENT_APPROVE')"
      + " or hasAuthority('PROCUREMENT_REJECT')"
      + " or hasAuthority('ACTUAL_COST_APPROVE')"
      + " or hasAuthority('ACTUAL_COST_REJECT')"
      + " or hasAuthority('BUDGET_APPROVE')"
      + " or hasAuthority('PLANNED_SHUTDOWN_APPROVE')"
      + " or hasAuthority('REPAIR_CAMPAIGN_APPROVE')"
      + " or hasAuthority('MAINTENANCE_EVENT_APPROVE')"
      + " or hasAuthority('MAINTENANCE_REGULATION_UPDATE')"
      + " or hasAuthority('EQUIPMENT_COMMISSIONING_UPDATE')";

  private ApprovalSecurityExpressions() {
  }
}
