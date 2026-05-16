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

    @Query(value = "SELECT * FROM departments WHERE parent_id = :parentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Department> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query("""
            select d
            from Department d
            where d.isDeleted = false
              and (:type is null or d.type = :type)
              and (
                    :search is null
                    or trim(:search) = ''
                    or lower(coalesce(d.code, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(d.name, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(d.nameEn, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(d.nameUz, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(d.description, '')) like lower(concat('%', :search, '%'))
                  )
            order by d.updatedAt desc
            """)
    List<Department> findAllByIsDeletedFalseAndByType(@Param("type") DepartmentType type,
                                                      @Param("search") String search);
}
