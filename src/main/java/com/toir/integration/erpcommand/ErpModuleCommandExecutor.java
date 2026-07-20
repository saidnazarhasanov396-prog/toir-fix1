package com.toir.integration.erpcommand;

import java.util.Set;

public interface ErpModuleCommandExecutor {
  Set<String> supportedTypes();
  long currentRevision(ErpModuleCommandEnvelope command);
  void execute(ErpModuleCommandEnvelope command);
}
