package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceActionRepository extends JpaRepository<MaintenanceAction, UUID> {

    @Query(value = "SELECT * FROM maintenance_actions WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<MaintenanceAction> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = """
            SELECT * FROM maintenance_actions
            WHERE is_deleted = false
              AND (:active IS NULL OR is_active = :active)
              AND (
                  :search IS NULL OR :search = '' OR
                  LOWER(code) LIKE LOWER(:search) OR
                  LOWER(name) LIKE LOWER(:search) OR
                  LOWER(category) LIKE LOWER(:search)
              )
            ORDER BY updated_at DESC
            """, nativeQuery = true)
    List<MaintenanceAction> search(@Param("search") String search, @Param("active") Boolean active);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM maintenance_actions
                WHERE UPPER(code) = UPPER(:code)
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsByCodeIgnoreCaseAndIsDeletedFalse(@Param("code") String code);
}
