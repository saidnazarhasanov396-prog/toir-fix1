package com.toir.entity;

import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.defects.DefectList;
import com.toir.entity.maintenance.RegulationChangeProposal;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActorStampedEntityContractTest {

    @Test
    void scopedBusinessEntitiesInheritActorStamping() {
        assertThat(ActorStampedEntity.class).isAssignableFrom(WorkOrder.class);
        assertThat(ActorStampedEntity.class).isAssignableFrom(PprPlan.class);
        assertThat(ActorStampedEntity.class).isAssignableFrom(DefectList.class);
        assertThat(ActorStampedEntity.class).isAssignableFrom(StockMovement.class);
        assertThat(ActorStampedEntity.class).isAssignableFrom(ContractorWork.class);
        assertThat(ActorStampedEntity.class).isAssignableFrom(ActualCostReviewRouteOverride.class);
        assertThat(ActorStampedEntity.class).isAssignableFrom(RegulationChangeProposal.class);
    }

    @Test
    void auditLogRemainsSeparateFromActorStampedEntities() {
        assertThat(ActorStampedEntity.class.isAssignableFrom(AuditLog.class)).isFalse();
        assertThat(BaseEntity.class.isAssignableFrom(AuditLog.class)).isFalse();
    }

    @Test
    void inheritedActorFieldsRemainSettableForServerSideActorSources() {
        WorkOrder workOrder = new WorkOrder();
        UUID createdById = UUID.randomUUID();
        UUID updatedById = UUID.randomUUID();

        workOrder.setCreatedById(createdById);
        workOrder.setUpdatedById(updatedById);

        assertThat(workOrder.getCreatedById()).isEqualTo(createdById);
        assertThat(workOrder.getUpdatedById()).isEqualTo(updatedById);
    }
}
