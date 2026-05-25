package com.toir.repository.department;

import com.toir.entity.Department;
import com.toir.enums.DepartmentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    @Query(value = "SELECT * FROM departments WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Department> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM departments WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Department> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM departments WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Department> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM departments WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM departments WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM departments WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query("""
            select d.code
            from Department d
            where d.code like concat(:prefix, '-%')
            """)
    List<String> findCodesByPrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM departments WHERE parent_id = :parentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Department> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query("""
            select d
            from Department d
            where d.isDeleted = false
              and (:type is null or d.type = :type)
              and (
                    :searchPattern is null
                    or lower(coalesce(d.code, '')) like :searchPattern
                    or lower(coalesce(d.name, '')) like :searchPattern
                    or lower(coalesce(d.nameEn, '')) like :searchPattern
                    or lower(coalesce(d.nameUz, '')) like :searchPattern
                    or lower(coalesce(d.description, '')) like :searchPattern
                  )
            order by d.updatedAt desc
            """)
    List<Department> findAllByIsDeletedFalseAndByType(@Param("type") DepartmentType type,
                                                      @Param("searchPattern") String searchPattern);
}
