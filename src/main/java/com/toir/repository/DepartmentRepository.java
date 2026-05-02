package com.toir.repository;

import com.toir.enums.DepartmentType;
import org.springframework.stereotype.Repository;
import com.toir.entity.Department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    java.util.Optional<Department> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Department> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Department> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

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
                    or :search = ''
                    or lower(d.name) like lower(concat('%', :search, '%'))
                    or lower(d.nameEn) like lower(concat('%', :search, '%'))
                    or lower(d.nameUz) like lower(concat('%', :search, '%'))
                    or lower(d.description) like lower(concat('%', :search, '%'))
                  )
            order by d.updatedAt desc
            """)

    List<Department> findAllByIsDeletedFalseAndByType(DepartmentType type, String search);
}
