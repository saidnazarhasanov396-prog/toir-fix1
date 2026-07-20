package com.toir.integration.erpcommand;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ErpModuleCommandService {
  private final ErpCommandReceiptRepository receipts;
  private final ErpModuleCommandExecutor executor;
  private final ObjectMapper json;
  public ErpModuleCommandService(ErpCommandReceiptRepository receipts, ErpModuleCommandExecutor executor,
      ObjectMapper json) {
    this.receipts = receipts; this.executor = executor; this.json = json;
  }
  @Transactional
  public ReceiptResponse accept(ErpModuleCommandEnvelope command) {
    validate(command);
    String hash = fingerprint(command);
    ErpCommandReceipt existing = receipts.findBySourceSystemAndIdempotencyKey("ERP", command.idempotencyKey()).orElse(null);
    if (existing != null) {
      if (!existing.getCommandId().equals(command.id()) || !existing.getCommandType().equals(command.commandType())
          || !existing.getPayloadHash().equals(hash)) throw conflict("ERP_COMMAND_IDEMPOTENCY_CONFLICT");
      if (!"ACCEPTED".equals(existing.getStatus())) throw conflict("ERP_COMMAND_ALREADY_PROCESSING");
      return response(existing, true);
    }
    Long expected = command.expectedRevision();
    if (expected != null && expected != executor.currentRevision(command)) throw conflict("ERP_COMMAND_REVISION_CONFLICT");
    ErpCommandReceipt receipt = receipts.saveAndFlush(new ErpCommandReceipt(
        "ERP", command.idempotencyKey(), command.id(), command.commandType(), hash));
    executor.execute(command);
    receipt.accept();
    return response(receipt, false);
  }
  private void validate(ErpModuleCommandEnvelope command) {
    if (command == null || command.id() == null || command.data() == null || !command.data().isObject()
        || command.payload() == null || !command.payload().isObject()) throw invalid("ERP_COMMAND_ENVELOPE_INVALID");
    if (!"ERP".equalsIgnoreCase(command.source()) || !"TOIR_GENERAL".equalsIgnoreCase(command.target()))
      throw invalid("ERP_COMMAND_ROUTE_INVALID");
    if (!"1.0".equals(command.schemaVersion()) || !"COMMAND".equalsIgnoreCase(command.kind()))
      throw invalid("ERP_COMMAND_SCHEMA_INVALID");
    if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
        || command.idempotencyKey().length() > 160) throw invalid("ERP_COMMAND_IDEMPOTENCY_INVALID");
    if (!executor.supportedTypes().contains(command.commandType())) throw invalid("ERP_COMMAND_TYPE_UNSUPPORTED");
    if (!command.id().toString().equals(command.data().path("commandId").asText())
        || !command.commandType().equals(command.data().path("commandType").asText())
        || !command.idempotencyKey().equals(command.data().path("idempotencyKey").asText())
        || !command.data().hasNonNull("actorReference")) throw invalid("ERP_COMMAND_DATA_MISMATCH");
  }
  private String fingerprint(ErpModuleCommandEnvelope command) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(command)));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw invalid("ERP_COMMAND_ENVELOPE_INVALID");
    }
  }
  private ReceiptResponse response(ErpCommandReceipt row, boolean duplicate) {
    return new ReceiptResponse(row.getId(), row.getCommandId(), row.getStatus(), duplicate);
  }
  private ResponseStatusException invalid(String code) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, code);
  }
  private ResponseStatusException conflict(String code) {
    return new ResponseStatusException(HttpStatus.CONFLICT, code);
  }
  public record ReceiptResponse(UUID receiptId, UUID commandId, String status, boolean duplicate) { }
}
