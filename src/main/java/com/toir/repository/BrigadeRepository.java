package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Brigade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface BrigadeRepository extends JpaRepository<Brigade, UUID> {
    java.util.Optional<Brigade> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Brigade> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Brigade> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM brigades WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM brigades WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Brigade> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM brigades WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Brigade> findAllByActiveTrueAndIsDeletedFalse();

    @Query("""
        select b from Brigade b
        where b.isDeleted = false
        and (:departmentId is null or b.departmentId = :departmentId)
        and (:activeOnly is null or :activeOnly = false or b.active = true)
        and (:search is null or lower(b.name) like :search or
             :search is null or lower(b.code) like :search or
             :search is null or lower(b.specialization) like :search)
        order by b.updatedAt desc
""")
    List<Brigade> findAllByDepartmentAndIsActiveOnlyAndDeletedAndSearch(UUID departmentId, Boolean activeOnly, String search);
}
