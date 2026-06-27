package com.toir.repository.equipment;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentRepositoryQueryContractTest {



    @Test
    void availableForReplacementQueryRequiresActiveNonDeletedWarehouseEquipmentItems() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchAvailableForReplacement"))
                .filter(m -> m.getParameterCount() == 11)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("wei.active = true");
        assertThat(query.value()).contains("wei.isDeleted = false");
    }

    @Test
    void searchQueryScopesByResponsibleDepartmentFallbackAndLocationFilters() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("search"))
                .filter(m -> m.getParameterCount() == 12)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("coalesce(e.responsibleDepartmentId, e.departmentId) = :scopeDepartmentId");
        assertThat(query.value()).contains("e.departmentId = :departmentId");
        assertThat(query.value()).contains("e.currentLocationType = :locationType");
        assertThat(query.value()).contains("e.currentWarehouseId = :warehouseId");
        assertThat(query.value()).contains("e.outsideReason = :outsideReason");
        assertThat(query.value()).contains("e.outsideExpectedReturnDate < :today");
    }

    @Test
    void searchWithMxikQueryFiltersAndSearchesMxikCatalog() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchWithMxik"))
                .filter(m -> m.getParameterCount() == 13)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("(:mxikId is null or e.mxikId = :mxikId)");
        assertThat(query.value()).contains("from Mxik m");
        assertThat(query.value()).contains("m.id = e.mxikId");
        assertThat(query.value()).contains("lower(m.kod) like :searchPattern");
        assertThat(query.value()).contains("lower(coalesce(m.barcode, '')) like :searchPattern");
    }

    @Test
    void availableReplacementWithMxikQueryFiltersByMxik() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchAvailableForReplacementWithMxik"))
                .filter(m -> m.getParameterCount() == 12)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("(:mxikId is null or e.mxikId = :mxikId)");
        assertThat(query.value()).contains("from Mxik m");
    }

    @Test
    void availableReplacementQueryRequiresCanonicalWarehouseLocation() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchAvailableForReplacement"))
                .filter(m -> m.getParameterCount() == 11)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("e.currentLocationType = :locationType");
        assertThat(query.value()).contains("e.currentWarehouseId = :warehouseId");
        assertThat(query.value()).contains("coalesce(e.responsibleDepartmentId, e.departmentId) = :departmentId");
    }

}
