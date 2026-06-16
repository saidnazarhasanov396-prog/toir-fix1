package com.toir.repository.attachment;

import com.toir.entity.attachment.AttachmentGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AttachmentGroupItemRepository extends JpaRepository<AttachmentGroupItem, UUID> {
}
