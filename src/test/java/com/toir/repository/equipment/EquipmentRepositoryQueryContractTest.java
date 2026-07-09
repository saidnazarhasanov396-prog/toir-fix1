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
                .filter(m -> m.getParameterCount() == 12)
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
                .filter(m -> m.getParameterCount() == 13)
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
    void searchQueryAppliesNullableHasWarrantyFilterOnlyFromBooleanField() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("search"))
                .filter(m -> m.getParameterCount() == 13)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        String queryText = query.value().toLowerCase();
        assertThat(queryText).contains(":haswarranty is null");
        assertThat(queryText).contains(":haswarranty = true");
        assertThat(queryText).contains("e.haswarranty = true");
        assertThat(queryText).contains(":haswarranty = false");
        assertThat(queryText).contains("e.haswarranty = false");
        assertThat(queryText).contains("e.haswarranty is null");
        assertThat(queryText).doesNotContain("warrantyuntil");
        assertThat(queryText).doesNotContain("warrantystartdate");
        assertThat(queryText).doesNotContain("warrantyenddate");
        assertThat(queryText).doesNotContain("warrantyattachmentid");
        assertThat(queryText).doesNotContain("warrantycounteragentid");
    }

    @Test
    void searchQueryCombinesVehicleCategoryStatusAndHasWarrantyFilters() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("search"))
                .filter(m -> m.getParameterCount() == 13)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        String queryText = query.value().toLowerCase();
        assertThat(queryText).contains("e.status = :status");
        assertThat(queryText).contains("e.category = :category");
        assertThat(queryText).contains("e.haswarranty = true");
    }

    @Test
    void searchWithMxikQueryFiltersAndSearchesMxikCatalog() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchWithMxik"))
                .filter(m -> m.getParameterCount() == 14)
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
    void searchWithMxikQueryAlsoAppliesHasWarrantyFilter() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchWithMxik"))
                .filter(m -> m.getParameterCount() == 14)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        String queryText = query.value().toLowerCase();
        assertThat(queryText).contains("(:mxikid is null or e.mxikid = :mxikid)");
        assertThat(queryText).contains(":haswarranty is null");
        assertThat(queryText).contains("e.haswarranty = true");
        assertThat(queryText).contains("e.haswarranty = false");
        assertThat(queryText).contains("e.haswarranty is null");
    }

    @Test
    void searchQueryHasWarrantyTrueIncludesVehicleRowsThroughEquipmentCategoryOnly() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("search"))
                .filter(m -> m.getParameterCount() == 13)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        String queryText = query.value().toLowerCase();
        assertThat(queryText).contains("e.category = :category");
        assertThat(queryText).contains("e.haswarranty = true");
        assertThat(queryText).doesNotContain("vehicledetails");
    }

    @Test
    void availableReplacementWithMxikQueryFiltersByMxik() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchAvailableForReplacementWithMxik"))
                .filter(m -> m.getParameterCount() == 13)
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
                .filter(m -> m.getParameterCount() == 12)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("e.currentLocationType = :locationType");
        assertThat(query.value()).contains("e.currentWarehouseId = :warehouseId");
        assertThat(query.value()).contains("coalesce(e.responsibleDepartmentId, e.departmentId) = :departmentId");
    }

}
