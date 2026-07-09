package com.toir.repository.attachment;

import java.util.UUID;

public interface AttachmentTargetPhotoCountProjection {
    UUID getTargetId();
    Long getPhotoCount();
}
