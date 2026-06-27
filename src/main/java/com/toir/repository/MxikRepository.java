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
                    or lower(m.kod) like :searchPattern
                    or lower(m.type) like :searchPattern
                    or lower(coalesce(m.groupName, '')) like :searchPattern
                    or lower(coalesce(m.positionName, '')) like :searchPattern
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
