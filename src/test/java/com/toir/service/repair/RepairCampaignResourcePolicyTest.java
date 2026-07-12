package com.toir.service.repair;
import com.toir.dto.repaircampaign.RepairCampaignResourceRequest;
import com.toir.entity.Counteragent;
import com.toir.entity.repair.*;
import com.toir.entity.users.*;
import com.toir.enums.*;
import com.toir.repository.CounteragentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.repair.*;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.extension.ExtendWith;import org.mockito.*;
import java.time.Instant;import java.util.*;
import static org.assertj.core.api.Assertions.*;import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class RepairCampaignResourcePolicyTest {
 @Mock RepairCampaignRepository campaigns;@Mock RepairCampaignWorkItemRepository items;@Mock RepairCampaignResourceAssignmentRepository assignments;@Mock EmployeeRepository employees;@Mock BrigadeRepository brigades;@Mock CounteragentRepository counteragents;@Mock ScopeAccessService scope;@Mock AuditBuilderService audit;@InjectMocks RepairCampaignResourcePolicy policy;
 UUID campaignId=UUID.randomUUID(),itemId=UUID.randomUUID(),employeeId=UUID.randomUUID();RepairCampaign campaign;Instant start=Instant.parse("2026-08-01T08:00:00Z"),end=Instant.parse("2026-08-01T12:00:00Z");
 @BeforeEach void setup(){campaign=new RepairCampaign();campaign.setId(campaignId);campaign.setVersion(1L);campaign.setStatus(RepairCampaignStatus.DRAFT);lenient().when(campaigns.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));RepairCampaignWorkItem item=new RepairCampaignWorkItem();item.setId(itemId);lenient().when(items.findByIdAndCampaignIdAndIsDeletedFalse(itemId,campaignId)).thenReturn(Optional.of(item));}
 @Test void rejectsInvalidCardinalityWindowAndInactiveResource(){assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,null,null,null,"A",start,end,null))).hasMessageContaining("EXACTLY_ONE");when(employees.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.empty());assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,employeeId,null,null,"A",start,end,null))).hasMessageContaining("INACTIVE");}
 @Test void rejectsMissingCompetencyAndOverlappingResource(){Employee e=new Employee();e.setActive(true);e.setPosition("mechanic");when(employees.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(e));assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,employeeId,null,null,"A",start,end,"welder"))).hasMessageContaining("COMPETENCY_MISSING");RepairCampaignResourceAssignment old=new RepairCampaignResourceAssignment();old.setEmployeeId(employeeId);old.setPlannedStartAt(start);old.setPlannedEndAt(end);when(assignments.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedStartAtAscIdAsc(campaignId)).thenReturn(List.of(old));assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,employeeId,null,null,"B",start.plusSeconds(60),end.plusSeconds(60),"mechanic"))).hasMessageContaining("RESOURCE_CONFLICT");}
 @Test void criticalBlockerUsesExplicitPriorityOnly(){RepairCampaignWorkItem critical=new RepairCampaignWorkItem();critical.setId(itemId);critical.setPriority(RepairCampaignPriority.CRITICAL);when(assignments.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedStartAtAscIdAsc(campaignId)).thenReturn(List.of());when(items.findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaignId)).thenReturn(List.of(critical));assertThat(policy.blockers(campaignId)).containsExactly("CRITICAL_WORK_UNASSIGNED:"+itemId);}
 @Test void rejectsBlankShiftInvalidWindowAndInactiveBrigadeOrCounteragent(){assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,employeeId,null,null," ",start,end,null))).hasMessageContaining("WINDOW_INVALID");assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,employeeId,null,null,"A",end,start,null))).hasMessageContaining("WINDOW_INVALID");UUID brigade=UUID.randomUUID(),counteragent=UUID.randomUUID();when(brigades.findByIdAndIsDeletedFalse(brigade)).thenReturn(Optional.empty());when(counteragents.findByIdAndIsDeletedFalse(counteragent)).thenReturn(Optional.empty());assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,null,brigade,null,"A",start,end,null))).hasMessageContaining("INACTIVE");assertThatThrownBy(()->policy.add(campaignId,new RepairCampaignResourceRequest(1L,itemId,null,null,counteragent,"A",start,end,null))).hasMessageContaining("INACTIVE");}
}
