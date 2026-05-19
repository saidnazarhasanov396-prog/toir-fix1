package com.toir.repository.projects;

public interface EmployeeStatsProjection {
    Long getTotal();
    Long getActive();
    Long getTerminated();
    Long getWithoutEmail();
}
