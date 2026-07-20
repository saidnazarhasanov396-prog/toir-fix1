package com.toir.integration.erpcommand;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/erp/v1/commands")
@PreAuthorize("hasAnyAuthority('ERP_INTEGRATION','SYSTEM_ADMIN','*')")
public class ErpModuleCommandController {
  private final ErpModuleCommandService commands;
  public ErpModuleCommandController(ErpModuleCommandService commands) { this.commands = commands; }
  @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
  public ErpModuleCommandService.ReceiptResponse accept(@RequestBody ErpModuleCommandEnvelope command) {
    return commands.accept(command);
  }
}
