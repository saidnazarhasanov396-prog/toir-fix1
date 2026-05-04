package com.toir.repository.defects;

import com.toir.entity.defects.DefectListLine;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DefectListLineRepository extends JpaRepository<DefectListLine, UUID> {
    @Query(value = "SELECT * FROM defect_list_lines WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<DefectListLine> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM defect_list_lines WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DefectListLine> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM defect_list_lines WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<DefectListLine> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_list_lines WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM defect_list_lines WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

}
