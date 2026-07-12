package com.toir.repository;

import com.toir.entity.Counteragent;
import com.toir.enums.CounteragentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CounteragentRepository extends JpaRepository<Counteragent, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Counteragent c where c.id=:id and c.isDeleted=false")
    Optional<Counteragent> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    Optional<Counteragent> findByIdAndIsDeletedFalse(UUID id);

    List<Counteragent> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    List<Counteragent> findAllByIdInAndIsDeletedFalse(Collection<UUID> ids);

    boolean existsByCodeAndIsDeletedFalse(String code);

    boolean existsByInnAndIsDeletedFalse(String inn);

    boolean existsByInnAndIdNotAndIsDeletedFalse(String inn, UUID id);

    @Query("""
            select c
            from Counteragent c
            where c.isDeleted = false
              and (:status is null or c.status = :status)
            order by c.updatedAt desc
            """)
    List<Counteragent> findAllFiltered(@Param("status") CounteragentStatus status);

    @Query("""
            select c
            from Counteragent c
            where c.isDeleted = false
              and (:status is null or c.status = :status)
              and (
                    lower(c.code) like lower(concat('%', :search, '%'))
                    or lower(c.name) like lower(concat('%', :search, '%'))
                    or lower(coalesce(c.inn, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(c.contactName, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(c.contactPhone, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(c.contactEmail, '')) like lower(concat('%', :search, '%'))
                  )
            order by c.updatedAt desc
            """)
    List<Counteragent> search(
            @Param("search") String search,
            @Param("status") CounteragentStatus status
    );

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM counteragents
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
              AND is_deleted = false
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
