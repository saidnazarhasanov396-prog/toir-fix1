package com.toir.repository.attachment;

import java.util.UUID;

public interface AttachmentTargetPrimaryPhotoProjection {
    UUID getTargetId();
    UUID getGroupId();
    UUID getFileId();
}
