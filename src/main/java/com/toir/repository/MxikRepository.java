package com.toir.repository;

import com.toir.dto.mxik.MxikNameCodeCountProjection;
import com.toir.dto.mxik.MxikNameCountProjection;
import com.toir.entity.Mxik;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MxikRepository extends JpaRepository<Mxik, UUID> {

    Optional<Mxik> findByIdAndIsDeletedFalse(UUID id);

    Optional<Mxik> findByKodAndIsDeletedFalse(String kod);

    @Query("""
            select count(m) > 0
            from Mxik m
            where m.isDeleted = false
              and lower(m.kod) = lower(:kod)
            """)
    boolean existsActiveByKod(@Param("kod") String kod);

    @Query("""
            select count(m) > 0
            from Mxik m
            where m.isDeleted = false
              and lower(m.kod) = lower(:kod)
              and m.id <> :id
            """)
    boolean existsActiveByKodAndIdNot(@Param("kod") String kod, @Param("id") UUID id);

    @Query("""
            select m
            from Mxik m
            where m.isDeleted = false
              and (
                    :searchPattern is null
                    or lower(m.name) like :searchPattern
                    or lower(coalesce(m.nameUzLatn, '')) like :searchPattern
                    or lower(coalesce(m.nameRu, '')) like :searchPattern
                    or lower(m.kod) like :searchPattern
                    or lower(m.type) like :searchPattern
                    or lower(coalesce(m.barcode, '')) like :searchPattern
                    or lower(coalesce(m.brandName, '')) like :searchPattern
                    or lower(coalesce(m.groupName, '')) like :searchPattern
                    or lower(coalesce(m.className, '')) like :searchPattern
                    or lower(coalesce(m.positionName, '')) like :searchPattern
                    or lower(coalesce(m.subPositionName, '')) like :searchPattern
                    or lower(coalesce(m.attributeName, '')) like :searchPattern
                  )
            order by m.createdAt desc
            """)
    Page<Mxik> search(@Param("searchPattern") String searchPattern, Pageable pageable);

    @Query("""
            select max(m.groupName) as name, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.groupName is not null
              and trim(m.groupName) <> ''
            group by m.groupName
            order by name asc
            """)
    List<MxikNameCountProjection> findGroupCounts();

    @Query("""
            select max(m.className) as name, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.groupName = :groupName
              and m.className is not null
              and trim(m.className) <> ''
            group by m.className
            order by name asc
            """)
    List<MxikNameCountProjection> findClassCounts(@Param("groupName") String groupName);

    @Query("""
            select max(m.positionName) as name, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.className = :className
              and m.positionName is not null
              and trim(m.positionName) <> ''
            group by m.positionName
            order by name asc
            """)
    List<MxikNameCountProjection> findPositionCounts(@Param("className") String className);

    @Query("""
            select max(m.subPositionName) as name, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.positionName = :positionName
              and m.subPositionName is not null
              and trim(m.subPositionName) <> ''
            group by m.subPositionName
            order by name asc
            """)
    List<MxikNameCountProjection> findSubPositionCounts(@Param("positionName") String positionName);

    @Query("""
            select max(m.name) as name, max(m.kod) as code, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.subPositionName = :subPositionName
            group by m.name
            order by name asc
            """)
    List<MxikNameCodeCountProjection> findMxikCounts(@Param("subPositionName") String subPositionName);
}
