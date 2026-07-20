CREATE TABLE erp_command_receipt (
  id UUID PRIMARY KEY,
  source_system VARCHAR(40) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  command_id UUID NOT NULL UNIQUE,
  command_type VARCHAR(160) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  error_code VARCHAR(80),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uk_erp_command_receipt_source_key UNIQUE (source_system, idempotency_key)
);
