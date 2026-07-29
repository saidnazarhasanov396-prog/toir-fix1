package com.toir.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.enums.ApprovalResolutionCode;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.TaskMaterializationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnnualMaintenanceApprovalFirstMappingTest {

    @Test
    void newManualPlanKeepsLegacySafeMaterializationDefaults() {
        PprPlan plan = new PprPlan();

        assertThat(plan.getOrigin()).isEqualTo(PprPlanOrigin.MANUAL);
        assertThat(plan.getMaterializationMode()).isEqualTo(MaterializationMode.LEGACY_MATERIALIZED);
        assertThat(plan.getTaskMaterializationStatus())
                .isEqualTo(TaskMaterializationStatus.NOT_APPLICABLE);
        assertThat(plan.getCalculationRevision()).isNull();
        assertThat(plan.getCalculationContentHash()).isNull();
        assertThat(plan.getCalculationContentHashVersion()).isNull();
        assertThat(plan.getMaterializedRevision()).isNull();
        assertThat(plan.getMaterializedTaskCount()).isNull();
    }

    @Test
    void existingGeneratedPlanAndTaskIdentityRemainUnchangedByFoundationDefaults() {
        UUID taskId = UUID.randomUUID();
        PprTask task = new PprTask();
        task.setId(taskId);
        task.setStatus(PprTaskStatus.PLANNED);

        PprPlan plan = new PprPlan();
        plan.setStatus(PlanStatus.GENERATED);
        plan.setTasks(List.of(task));

        assertThat(plan.getStatus()).isEqualTo(PlanStatus.GENERATED);
        assertThat(plan.getTasks()).extracting(PprTask::getId).containsExactly(taskId);
        assertThat(plan.getTasks()).extracting(PprTask::getStatus)
                .containsExactly(PprTaskStatus.PLANNED);
        assertThat(plan.getMaterializationMode()).isNotEqualTo(MaterializationMode.APPROVAL_FIRST);
    }

    @Test
    void planAndApprovalEnumsUseStringPersistence() throws Exception {
        assertStringEnum(PprPlan.class, "materializationMode");
        assertStringEnum(PprPlan.class, "taskMaterializationStatus");
        assertStringEnum(ApprovalRequest.class, "resolutionCode");
    }

    @Test
    void approvalRequestReusesCanonicalMetadataAndMapsOnlyNewFoundationFields()
            throws Exception {
        assertThat(columnName(ApprovalRequest.class, "templateId")).isEqualTo("template_id");
        assertThat(columnName(ApprovalRequest.class, "templateVersion")).isEqualTo("template_version");
        assertThat(columnName(ApprovalRequest.class, "actionType")).isEqualTo("action_type");
        assertThat(columnName(ApprovalRequest.class, "failureReason")).isEqualTo("failure_reason");
        assertThat(columnName(ApprovalRequest.class, "lastReturnComment"))
                .isEqualTo("last_return_comment");
        assertThat(columnName(ApprovalRequest.class, "lastReturnedBy"))
                .isEqualTo("last_returned_by");
        assertThat(columnName(ApprovalRequest.class, "lastReturnedAt"))
                .isEqualTo("last_returned_at");

        assertThat(columnName(ApprovalRequest.class, "calculationRevision"))
                .isEqualTo("calculation_revision");
        assertThat(columnName(ApprovalRequest.class, "calculationContentHash"))
                .isEqualTo("calculation_content_hash");
        assertThat(columnName(ApprovalRequest.class, "calculationContentHashVersion"))
                .isEqualTo("calculation_content_hash_version");
        assertThat(columnName(ApprovalRequest.class, "resolvedRouteFingerprint"))
                .isEqualTo("resolved_route_fingerprint");
        assertThat(columnName(ApprovalRequest.class, "requesterContextFingerprint"))
                .isEqualTo("requester_context_fingerprint");
        assertThat(columnName(ApprovalRequest.class, "resolutionCode")).isEqualTo("resolution_code");
        assertThat(columnName(ApprovalRequest.class, "supersededByRequestId"))
                .isEqualTo("superseded_by_request_id");

        assertThat(field(ApprovalRequest.class, "resolutionCode").getType())
                .isEqualTo(ApprovalResolutionCode.class);
        assertThatThrownBy(() -> field(ApprovalRequest.class, "resolutionReason"))
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> field(ApprovalRequest.class, "resolvedBy"))
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> field(ApprovalRequest.class, "resolvedAt"))
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> field(ApprovalRequest.class, "approvedRevision"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    private static void assertStringEnum(Class<?> owner, String fieldName) throws Exception {
        Enumerated enumerated = field(owner, fieldName).getAnnotation(Enumerated.class);
        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
    }

    private static String columnName(Class<?> owner, String fieldName) throws Exception {
        Column column = field(owner, fieldName).getAnnotation(Column.class);
        assertThat(column).isNotNull();
        return column.name();
    }

    private static Field field(Class<?> owner, String fieldName) throws NoSuchFieldException {
        return owner.getDeclaredField(fieldName);
    }
}
