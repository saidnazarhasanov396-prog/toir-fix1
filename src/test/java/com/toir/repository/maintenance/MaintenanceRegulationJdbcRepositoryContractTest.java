package com.toir.repository.maintenance;

import com.toir.dto.maintenanceregulation.MaintenanceRegulationFilter;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationStatsDto;
import com.toir.enums.MaintenanceKind;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceRegulationJdbcRepositoryContractTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private MaintenanceRegulationJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MaintenanceRegulationJdbcRepository(jdbc);
    }

    @Test
    void generalPageBindsEveryCanonicalFilterAndPreservesDatabasePageMetadata() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                "Pump 10", MaintenanceKind.PREVENTIVE, equipmentTypeId, true);
        PageRequest pageable = PageRequest.of(2, 25);
        MaintenanceRegulationReadRepository.GeneralKey key =
                new MaintenanceRegulationReadRepository.GeneralKey(
                        MaintenanceRegulationReadRepository.DisplaySource.REGULATION,
                        regulationId);

        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(51L);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceRegulationReadRepository.GeneralKey>>any()))
                .thenReturn(List.of(key));

        Page<MaintenanceRegulationReadRepository.GeneralKey> result =
                repository.findGeneral(filter, pageable);

        assertThat(result.getContent()).containsExactly(key);
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(25);
        assertThat(result.getTotalElements()).isEqualTo(51);

        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(jdbc).query(
                anyString(),
                parameters.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceRegulationReadRepository.GeneralKey>>any());
        assertThat(parameters.getValue().getValue("searchPattern")).isEqualTo("%pump 10%");
        assertThat(parameters.getValue().getValue("maintenanceType")).isEqualTo("PREVENTIVE");
        assertThat(parameters.getValue().getValue("equipmentTypeId")).isEqualTo(equipmentTypeId);
        assertThat(parameters.getValue().getValue("active")).isEqualTo(true);
        assertThat(parameters.getValue().getValue("limit")).isEqualTo(25);
        assertThat(parameters.getValue().getValue("offset")).isEqualTo(50L);
    }

    @Test
    void statsReturnsUnpagedDatabaseAggregatesForTheSameFilter() {
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                "pump", MaintenanceKind.OVERHAUL, null, false);
        MaintenanceRegulationStatsDto expected = new MaintenanceRegulationStatsDto(17, 0, 0, 17);
        when(jdbc.queryForObject(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceRegulationStatsDto>>any()))
                .thenReturn(expected);

        MaintenanceRegulationStatsDto result = repository.stats(filter);

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(
                anyString(),
                parameters.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceRegulationStatsDto>>any());
        assertThat(parameters.getValue().getValue("searchPattern")).isEqualTo("%pump%");
        assertThat(parameters.getValue().getValue("maintenanceType")).isEqualTo("OVERHAUL");
        assertThat(parameters.getValue().getValue("active")).isEqualTo(false);
        assertThat(parameters.getValue().hasValue("limit")).isFalse();
    }

    @Test
    void equipmentPageAndMatchesRemainBoundedToCurrentParentIds() {
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                null, MaintenanceKind.PREVENTIVE, null, true);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any()))
                .thenReturn(List.of(equipmentId));

        Page<UUID> page = repository.findEquipmentIds(filter, PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly(equipmentId);
        assertThat(page.getTotalElements()).isEqualTo(1);

        MaintenanceRegulationReadRepository.EquipmentMatch match =
                new MaintenanceRegulationReadRepository.EquipmentMatch(
                        equipmentId,
                        MaintenanceRegulationReadRepository.DisplaySource.REGULATION,
                        regulationId,
                        true);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceRegulationReadRepository.EquipmentMatch>>any()))
                .thenReturn(List.of(match));

        List<MaintenanceRegulationReadRepository.EquipmentMatch> matches =
                repository.findEquipmentMatches(filter, List.of(equipmentId));

        assertThat(matches).containsExactly(match);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(jdbc).query(
                anyString(),
                parameters.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceRegulationReadRepository.EquipmentMatch>>any());
        assertThat(parameters.getValue().getValue("equipmentIds")).isEqualTo(List.of(equipmentId));
    }

    @Test
    void equipmentTypeRowsExposeFilteredNestedItemsAndGroupedEquipmentCounts() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                "seal", null, equipmentTypeId, false);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any()))
                .thenReturn(List.of(equipmentTypeId));

        Page<UUID> page = repository.findEquipmentTypeIds(filter, PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly(equipmentTypeId);

        MaintenanceRegulationReadRepository.EquipmentTypeMatch match =
                new MaintenanceRegulationReadRepository.EquipmentTypeMatch(
                        equipmentTypeId,
                        MaintenanceRegulationReadRepository.DisplaySource.EQUIPMENT_RULE,
                        ruleId,
                        false);
        MaintenanceRegulationReadRepository.EquipmentTypeCount count =
                new MaintenanceRegulationReadRepository.EquipmentTypeCount(equipmentTypeId, 3);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    return sql.contains("equipment_count") ? List.of(count) : List.of(match);
                });

        List<MaintenanceRegulationReadRepository.EquipmentTypeMatch> matches =
                repository.findEquipmentTypeMatches(filter, List.of(equipmentTypeId));
        Map<UUID, Integer> counts =
                repository.countMatchingEquipmentByType(filter, List.of(equipmentTypeId));

        assertThat(matches).containsExactly(match);
        assertThat(counts).containsEntry(equipmentTypeId, 3);
    }
}
