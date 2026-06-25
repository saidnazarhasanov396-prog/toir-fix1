package com.toir.audit;

import com.toir.entity.Material;
import com.toir.enums.AuditAction;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UniversalAuditChangeFactoryTest {

    private final UniversalAuditChangeFactory factory = new UniversalAuditChangeFactory();

    @Test
    void metadataOnlyUpdateIsSkipped() {
        Optional<UniversalEntityAuditChange> change = factory.fromUpdate(
                Material.class,
                UUID.randomUUID(),
                new String[]{"updatedAt", "updatedById"},
                new Object[]{"2026-06-24T10:00:00Z", UUID.randomUUID()},
                new Object[]{"2026-06-24T10:01:00Z", UUID.randomUUID()}
        );

        assertThat(change).isEmpty();
    }

    @Test
    void softDeleteUpdateBecomesDeleteAuditAction() {
        UUID id = UUID.randomUUID();

        Optional<UniversalEntityAuditChange> change = factory.fromUpdate(
                Material.class,
                id,
                new String[]{"name", "isDeleted", "updatedAt"},
                new Object[]{"Oil", false, "2026-06-24T10:00:00Z"},
                new Object[]{"Oil", true, "2026-06-24T10:01:00Z"}
        );

        assertThat(change).isPresent();
        assertThat(change.get().entityId()).isEqualTo(id.toString());
        assertThat(change.get().action()).isEqualTo(AuditAction.DELETE);
        assertThat(change.get().previousSnapshot()).containsEntry("isDeleted", false);
        assertThat(change.get().currentSnapshot()).containsEntry("isDeleted", true);
    }

    @Test
    void businessUpdateKeepsOnlyChangedPropertiesAndUsesUpdateAction() {
        Optional<UniversalEntityAuditChange> change = factory.fromUpdate(
                Material.class,
                UUID.randomUUID(),
                new String[]{"name", "unit", "updatedAt"},
                new Object[]{"Oil", "L", "2026-06-24T10:00:00Z"},
                new Object[]{"Synthetic oil", "L", "2026-06-24T10:01:00Z"}
        );

        assertThat(change).isPresent();
        assertThat(change.get().action()).isEqualTo(AuditAction.UPDATE);
        assertThat(change.get().changedProperties()).containsExactly("name");
        assertThat(change.get().previousSnapshot()).containsEntry("name", "Oil");
        assertThat(change.get().currentSnapshot()).containsEntry("name", "Synthetic oil");
    }
}
