package com.toir.repository.projection;

public interface DefectStatsProjection {
    Long getTotalDefects();
    Long getOpen();
    Long getResolved();
    Long getWithRecurrence();
}
