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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MxikRepository extends JpaRepository<Mxik, UUID> {

    Optional<Mxik> findByIdAndIsDeletedFalse(UUID id);

    @Query("""
            select m
            from Mxik m
            where m.isDeleted = false
              and m.id in :ids
            """)
    List<Mxik> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query("""
            select m
            from Mxik m
            where m.isDeleted = false
              and (
                    :searchPattern is null
                    or lower(m.name) like :searchPattern
                    or lower(m.kod) like :searchPattern
                    or lower(m.type) like :searchPattern
                    or lower(coalesce(m.groupName, '')) like :searchPattern
                    or lower(coalesce(m.positionName, '')) like :searchPattern
                    or lower(coalesce(m.nameUzLatn, '')) like :searchPattern
                    or lower(coalesce(m.nameRu, '')) like :searchPattern
                    or lower(coalesce(m.groupNameRu, '')) like :searchPattern
                    or lower(coalesce(m.groupNameCyril, '')) like :searchPattern
                    or lower(coalesce(m.className, '')) like :searchPattern
                    or lower(coalesce(m.classNameRu, '')) like :searchPattern
                    or lower(coalesce(m.classNameCyril, '')) like :searchPattern
                    or lower(coalesce(m.positionNameRu, '')) like :searchPattern
                    or lower(coalesce(m.positionNameCyril, '')) like :searchPattern
                    or lower(coalesce(m.subPositionName, '')) like :searchPattern
                    or lower(coalesce(m.subPositionNameRu, '')) like :searchPattern
                    or lower(coalesce(m.subPositionNameCyril, '')) like :searchPattern
                    or lower(coalesce(m.brandName, '')) like :searchPattern
                    or lower(coalesce(m.brandNameRu, '')) like :searchPattern
                    or lower(coalesce(m.brandNameCyril, '')) like :searchPattern
                    or lower(coalesce(m.attributeName, '')) like :searchPattern
                    or lower(coalesce(m.attributeNameRu, '')) like :searchPattern
                    or lower(coalesce(m.attributeNameCyril, '')) like :searchPattern
                    or lower(coalesce(m.barcode, '')) like :searchPattern
                  )
            order by m.name asc
            """)
    Page<Mxik> search(@Param("searchPattern") String searchPattern, Pageable pageable);

    @Query("""
            select m.groupName as name, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.groupName is not null
              and trim(m.groupName) <> ''
            group by m.groupName
            order by m.groupName asc
            """)
    List<MxikNameCountProjection> findGroupCounts();

    @Query("""
            select m.positionName as name, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.groupName = :groupName
              and m.positionName is not null
              and trim(m.positionName) <> ''
            group by m.positionName
            order by m.positionName asc
            """)
    List<MxikNameCountProjection> findSubPositionCounts(@Param("groupName") String groupName);

    @Query("""
            select m.name as name, max(m.kod) as code, count(m) as count
            from Mxik m
            where m.isDeleted = false
              and m.positionName = :positionName
            group by m.name
            order by m.name asc
            """)
    Page<MxikNameCodeCountProjection> findMxikCounts(@Param("positionName") String positionName, Pageable pageable);
}
