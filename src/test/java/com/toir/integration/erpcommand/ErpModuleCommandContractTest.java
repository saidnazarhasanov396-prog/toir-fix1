package com.toir.integration.erpcommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

class ErpModuleCommandContractTest {
  private static final String TYPE = "maintenance.work-order.start";

  @Test
  void acceptsDuplicateOnceAndRejectsInvalidContracts() {
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    ErpCommandReceiptRepository repository = mock(ErpCommandReceiptRepository.class);
    ErpModuleCommandExecutor executor = mock(ErpModuleCommandExecutor.class);
    AtomicReference<ErpCommandReceipt> saved = new AtomicReference<>();
    when(executor.supportedTypes()).thenReturn(Set.of(TYPE)); when(executor.currentRevision(any())).thenReturn(9L);
    when(repository.findBySourceSystemAndIdempotencyKey("ERP", "start-1"))
        .thenAnswer(ignored -> Optional.ofNullable(saved.get()));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
      ErpCommandReceipt receipt = invocation.getArgument(0); saved.set(receipt); return receipt;
    });
    ErpModuleCommandService service = new ErpModuleCommandService(repository, executor, json);
    ErpModuleCommandEnvelope command = envelope(json, "TOIR_GENERAL", "1.0", TYPE, "start-1", null);

    assertThat(service.accept(command).duplicate()).isFalse();
    assertThat(service.accept(command).duplicate()).isTrue();
    verify(executor).execute(command);
    assertStatus(service, envelope(json, "MES_GENERAL", "1.0", TYPE, "bad-1", null), 422);
    assertStatus(service, envelope(json, "TOIR_GENERAL", "2.0", TYPE, "bad-2", null), 422);
    assertStatus(service, envelope(json, "TOIR_GENERAL", "1.0", "maintenance.unknown", "bad-3", null), 422);
    assertStatus(service, envelope(json, "TOIR_GENERAL", "1.0", TYPE, "bad-4", 8L), 409);
    assertThat(ErpModuleCommandController.class.getAnnotation(PreAuthorize.class).value()).contains("ERP_INTEGRATION");
  }
  private void assertStatus(ErpModuleCommandService service, ErpModuleCommandEnvelope command, int status) {
    assertThatThrownBy(() -> service.accept(command)).isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode().value()).isEqualTo(status);
  }
  private ErpModuleCommandEnvelope envelope(ObjectMapper json, String target, String schema,
      String type, String key, Long revision) {
    UUID id = UUID.randomUUID(); ObjectNode data = json.createObjectNode();
    data.put("commandId", id.toString()); data.put("commandType", type); data.put("idempotencyKey", key);
    data.put("actorReference", "erp-service"); data.set("payload", json.createObjectNode());
    if (revision != null) data.put("expectedRevision", revision);
    return new ErpModuleCommandEnvelope(id, "ERP", target, "erp.command." + type, "module-command/" + id,
        Instant.now(), "COMMAND", schema, "module-command", id, revision == null ? 0 : revision,
        key, id.toString(), null, data);
  }
}
