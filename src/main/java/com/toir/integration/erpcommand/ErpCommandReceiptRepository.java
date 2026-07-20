package com.toir.integration.erpcommand;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpCommandReceiptRepository extends JpaRepository<ErpCommandReceipt, UUID> {
  Optional<ErpCommandReceipt> findBySourceSystemAndIdempotencyKey(String sourceSystem, String idempotencyKey);
}
