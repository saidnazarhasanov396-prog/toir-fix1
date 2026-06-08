package com.toir.repository.projection;

import java.time.LocalDate;

public interface WorkOrderCalendarBucketProjection {
    Integer getBucketNumber();

    LocalDate getBucketDate();

    String getStatus();

    Long getCount();
}
