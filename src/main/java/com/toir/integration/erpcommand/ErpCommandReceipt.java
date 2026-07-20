package com.toir.integration.erpcommand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "erp_command_receipt", uniqueConstraints = @UniqueConstraint(
    name = "uk_erp_command_receipt_source_key", columnNames = {"source_system", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class ErpCommandReceipt {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "source_system", nullable = false, length = 40) private String sourceSystem;
  @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
  @Column(name = "command_id", nullable = false, unique = true) private UUID commandId;
  @Column(name = "command_type", nullable = false, length = 160) private String commandType;
  @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
  @Column(nullable = false, length = 20) private String status = "PENDING";
  @Column(name = "error_code", length = 80) private String errorCode;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public ErpCommandReceipt(String source, String key, UUID commandId, String type, String hash) {
    this.sourceSystem = source; this.idempotencyKey = key; this.commandId = commandId;
    this.commandType = type; this.payloadHash = hash;
  }
  public void accept() { status = "ACCEPTED"; errorCode = null; }
  @PrePersist void createTime() { createdAt = Instant.now(); updatedAt = createdAt; }
  @PreUpdate void updateTime() { updatedAt = Instant.now(); }
}
