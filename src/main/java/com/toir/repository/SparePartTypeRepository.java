package com.toir.repository;

import com.toir.entity.SparePartType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SparePartTypeRepository extends JpaRepository<SparePartType, UUID> {

    @Query("""
            select t
            from SparePartType t
            where t.active = true
              and (
                    :searchPattern is null
                    or lower(t.code) like :searchPattern
                    or lower(t.name) like :searchPattern
                    or lower(coalesce(t.description, '')) like :searchPattern
                  )
            order by t.name asc
            """)
    List<SparePartType> findAllActive(@Param("searchPattern") String searchPattern);

    @Query("""
            select t
            from SparePartType t
            where (
                    :searchPattern is null
                    or lower(t.code) like :searchPattern
                    or lower(t.name) like :searchPattern
                    or lower(coalesce(t.description, '')) like :searchPattern
                  )
            order by t.active desc, t.name asc
            """)
    List<SparePartType> findAllIncludingInactive(@Param("searchPattern") String searchPattern);

    @Query("""
            select t
            from SparePartType t
            where t.id = :id
              and t.active = true
            """)
    Optional<SparePartType> findByIdAndActiveTrue(@Param("id") UUID id);

    @Query("""
            select t
            from SparePartType t
            where lower(t.code) = lower(:code)
              and t.active = true
            """)
    Optional<SparePartType> findByCodeIgnoreCaseAndActiveTrue(@Param("code") String code);

    @Query("""
            select count(t) > 0
            from SparePartType t
            where lower(t.code) = lower(:code)
            """)
    boolean existsByCodeIgnoreCase(@Param("code") String code);

    @Query("""
            select count(t) > 0
            from SparePartType t
            where lower(t.code) = lower(:code)
              and t.id <> :id
            """)
    boolean existsByCodeIgnoreCaseAndIdNot(@Param("code") String code, @Param("id") UUID id);

    List<SparePartType> findAllByIdIn(Collection<UUID> ids);
}
