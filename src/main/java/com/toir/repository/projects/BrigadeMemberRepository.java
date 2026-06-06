package com.toir.repository.projects;

import com.toir.entity.users.BrigadeMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface BrigadeMemberRepository extends JpaRepository<BrigadeMember, UUID> {
    @Query(value = "SELECT * FROM brigade_members WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<BrigadeMember> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM brigade_members WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<BrigadeMember> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM brigade_members WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<BrigadeMember> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM brigade_members WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM brigade_members WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM brigade_members WHERE brigade_id = :brigadeId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<BrigadeMember> findAllByBrigadeIdAndIsDeletedFalse(@Param("brigadeId") UUID brigadeId);

    @Query("""
            select m
            from BrigadeMember m
            join fetch m.brigade b
            where m.isDeleted = false
              and m.active = true
              and b.isDeleted = false
              and b.active = true
              and (:departmentId is null or b.departmentId = :departmentId)
            order by m.updatedAt desc
            """)
    List<BrigadeMember> findActivePerformersByDepartment(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM brigade_members WHERE user_id = :userId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<BrigadeMember> findAllByUserIdAndIsDeletedFalse(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM brigade_members WHERE brigade_id = :brigadeId AND user_id = :userId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<BrigadeMember> findByBrigadeIdAndUserIdAndIsDeletedFalse(@Param("brigadeId") UUID brigadeId, @Param("userId") UUID userId);
}
