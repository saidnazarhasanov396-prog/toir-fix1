package com.toir.service.repair;
import com.toir.dto.repaircampaign.RepairCampaignDependencyRequest;
import com.toir.entity.repair.*;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.RepairCampaignWorkItemStatus;
import com.toir.repository.repair.*;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class RepairCampaignDependencyPolicyTest {
 @Mock RepairCampaignRepository campaigns; @Mock RepairCampaignWorkItemRepository items; @Mock RepairCampaignWorkDependencyRepository dependencies; @Mock ScopeAccessService scope; @Mock AuditBuilderService audit; @InjectMocks RepairCampaignDependencyPolicy policy;
 UUID campaignId=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(); RepairCampaign campaign;
 @BeforeEach void setup(){campaign=new RepairCampaign();campaign.setId(campaignId);campaign.setVersion(1L);campaign.setStatus(RepairCampaignStatus.DRAFT);lenient().when(campaigns.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));}
 @Test void rejectsSelfAndForeignItems(){assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignDependencyRequest(1L,a,a))).hasMessageContaining("SELF");when(items.findByIdAndCampaignIdAndIsDeletedFalse(a,campaignId)).thenReturn(Optional.empty());assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignDependencyRequest(1L,a,b))).hasMessageContaining("FOREIGN_ITEM");}
 @Test void deterministicDfsRejectsCircularEdge(){RepairCampaignWorkItem ia=new RepairCampaignWorkItem();ia.setId(a);RepairCampaignWorkItem ib=new RepairCampaignWorkItem();ib.setId(b);when(items.findByIdAndCampaignIdAndIsDeletedFalse(any(),eq(campaignId))).thenReturn(Optional.of(ia));RepairCampaignWorkDependency existing=new RepairCampaignWorkDependency();existing.setPredecessorWorkItemId(b);existing.setSuccessorWorkItemId(a);when(dependencies.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPredecessorWorkItemIdAscSuccessorWorkItemIdAsc(campaignId)).thenReturn(List.of(existing));assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignDependencyRequest(1L,a,b))).hasMessageContaining("DEPENDENCY_CYCLE");}
 @Test void predecessorBlocksOnlyWhenSuccessorAttemptsProgress(){RepairCampaignWorkItem predecessor=new RepairCampaignWorkItem();predecessor.setId(a);predecessor.setStatus(RepairCampaignWorkItemStatus.PENDING);RepairCampaignWorkItem successor=new RepairCampaignWorkItem();successor.setId(b);successor.setStatus(RepairCampaignWorkItemStatus.PENDING);RepairCampaignWorkDependency edge=new RepairCampaignWorkDependency();edge.setPredecessorWorkItemId(a);edge.setSuccessorWorkItemId(b);when(dependencies.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPredecessorWorkItemIdAscSuccessorWorkItemIdAsc(campaignId)).thenReturn(List.of(edge));when(items.findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaignId)).thenReturn(List.of(predecessor,successor));assertThat(policy.blockers(campaignId)).isEmpty();successor.setStatus(RepairCampaignWorkItemStatus.IN_PROGRESS);assertThat(policy.blockers(campaignId)).containsExactly("PREDECESSOR_INCOMPLETE:"+a+":"+b);}
 @Test void staleVersionAndApprovedFreezeFailBeforeMutation(){assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignDependencyRequest(0L,a,b))).hasMessageContaining("VERSION_CONFLICT");campaign.setStatus(RepairCampaignStatus.APPROVED);assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignDependencyRequest(1L,a,b))).hasMessageContaining("PLANNING_FROZEN");verify(dependencies,never()).saveAndFlush(any());}
}
