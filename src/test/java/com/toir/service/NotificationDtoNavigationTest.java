package com.toir.service;

import com.toir.dto.notification.NotificationDto;
import com.toir.entity.Notification;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDtoNavigationTest {

    @Test
    void mapsAdditiveNavigationFieldsAndPreservesLegacyNulls() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(UUID.randomUUID());
        notification.setTitle("PPR overdue");
        notification.setMessage("Task is overdue");
        notification.setSeverity(NotificationSeverity.WARNING);
        notification.setStatus(NotificationStatus.SENT);
        notification.setEntityType(NotificationEntityTypes.PPR_TASK);
        notification.setEntityId("task-1");
        notification.setEventType("PPR_TASK_OVERDUE");
        notification.setActionUrl("/ppr-calendar/plan-1?taskId=task-1");
        notification.setMetadata(Map.of("planId", "plan-1", "taskId", "task-1"));

        NotificationDto dto = NotificationDto.from(notification);

        assertThat(dto.eventType()).isEqualTo("PPR_TASK_OVERDUE");
        assertThat(dto.actionUrl()).isEqualTo("/ppr-calendar/plan-1?taskId=task-1");
        assertThat(dto.metadata()).containsEntry("planId", "plan-1");

        Notification legacy = new Notification();
        legacy.setRecipientId(UUID.randomUUID());
        legacy.setTitle("Legacy");
        legacy.setMessage("Still readable");
        assertThat(NotificationDto.from(legacy).eventType()).isNull();
        assertThat(NotificationDto.from(legacy).actionUrl()).isNull();
    }
}
