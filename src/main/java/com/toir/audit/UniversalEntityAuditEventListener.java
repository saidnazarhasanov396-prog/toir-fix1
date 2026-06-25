package com.toir.audit;

import lombok.RequiredArgsConstructor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UniversalEntityAuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final UniversalAuditChangeFactory changeFactory;
    private final UniversalEntityAuditService auditService;
    private final UniversalAuditEntityResolver entityResolver;

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (!entityResolver.isAuditable(event.getEntity().getClass())) {
            return;
        }
        changeFactory.fromInsert(
                event.getEntity().getClass(),
                event.getId(),
                event.getPersister().getPropertyNames(),
                event.getState()
        ).ifPresent(auditService::record);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!entityResolver.isAuditable(event.getEntity().getClass())) {
            return;
        }
        changeFactory.fromUpdate(
                event.getEntity().getClass(),
                event.getId(),
                event.getPersister().getPropertyNames(),
                event.getOldState(),
                event.getState()
        ).ifPresent(auditService::record);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (!entityResolver.isAuditable(event.getEntity().getClass())) {
            return;
        }
        changeFactory.fromDelete(
                event.getEntity().getClass(),
                event.getId(),
                event.getPersister().getPropertyNames(),
                event.getDeletedState()
        ).ifPresent(auditService::record);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }
}
