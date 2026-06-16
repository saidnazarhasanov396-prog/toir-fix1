package com.toir.repository.attachment;

import com.toir.entity.attachment.AttachmentGroup;
import com.toir.enums.AttachmentTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttachmentGroupRepository extends JpaRepository<AttachmentGroup, UUID> {

    @Query("""
            select distinct ag
            from AttachmentGroup ag
            left join fetch ag.items item
            left join fetch item.file file
            where ag.id = :id
              and ag.deleted = false
            order by item.orderNumber asc
            """)
    Optional<AttachmentGroup> findByIdWithItems(@Param("id") UUID id);

    @Query("""
            select distinct ag
            from AttachmentGroup ag
            left join fetch ag.items item
            left join fetch item.file file
            where ag.targetType = :targetType
              and ag.targetId = :targetId
              and ag.deleted = false
            order by ag.createdAt desc, item.orderNumber asc
            """)
    List<AttachmentGroup> findActiveByTarget(
            @Param("targetType") AttachmentTargetType targetType,
            @Param("targetId") UUID targetId
    );

    @Query("""
            select distinct ag
            from AttachmentGroup ag
            join fetch ag.items item
            join fetch item.file file
            where ag.targetType = :targetType
              and ag.targetId = :targetId
              and file.id = :fileId
              and ag.deleted = false
              and file.deleted = false
            """)
    Optional<AttachmentGroup> findActiveByTargetAndFileId(
            @Param("targetType") AttachmentTargetType targetType,
            @Param("targetId") UUID targetId,
            @Param("fileId") UUID fileId
    );
}
