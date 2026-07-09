package com.toir.repository.projection;

public interface MonthlyDowntimeProjection {
    String getMonth();

    long getDowntimeMinutes();
}
