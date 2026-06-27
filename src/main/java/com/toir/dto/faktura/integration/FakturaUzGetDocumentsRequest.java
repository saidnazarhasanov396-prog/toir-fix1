package com.toir.dto.faktura.integration;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FakturaUzGetDocumentsRequest {
    private String organizationInn;
    @Builder.Default
    private Integer limit = 500;
    @Builder.Default
    private Integer skip = 0;
    private Boolean isInbox;
    private LocalDateTime fromDateTime;
    private LocalDateTime toDateTime;
    private List<Integer> types;
    private List<Integer> statuses;
    private Integer type;
    private Integer status;
    private String authToken;

    public Long getCreatedFrom() {
        return fromDateTime == null ? null : fromDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public Long getCreatedTo() {
        return toDateTime == null ? null : toDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
