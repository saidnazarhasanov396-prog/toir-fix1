package com.toir.repository.projects;

import com.toir.entity.users.Brigade;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface BrigadeRepository extends JpaRepository<Brigade, UUID> {
    @Query(value = "SELECT * FROM brigades WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Brigade> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM brigades WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Brigade> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM brigades WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Brigade> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM brigades WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM brigades WHERE is_deleted = false", nativeQuery = true)
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
