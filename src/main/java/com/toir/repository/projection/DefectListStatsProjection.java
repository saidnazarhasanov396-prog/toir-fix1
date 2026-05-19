package com.toir.repository.projection;

public interface DefectListStatsProjection {
    Long getTotalDefectLists();
    Long getDraft();
    Long getApproved();
    Long getClosed();
}
