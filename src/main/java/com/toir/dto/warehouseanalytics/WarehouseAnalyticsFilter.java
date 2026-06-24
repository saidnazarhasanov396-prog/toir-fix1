package com.toir.dto.warehouseanalytics;

import java.util.UUID;

public class WarehouseAnalyticsFilter {

    private String period = "MONTH";
    private UUID warehouseId;
    private String search;
    private String categoryId;
    private String status;
    private Boolean onlyDeficit;
    private Boolean onlyCritical;
    private Boolean noMovement;
    private Boolean hasReserve;

    public String period() {
        return period == null || period.isBlank() ? "MONTH" : period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public UUID warehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String search() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String categoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String status() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean onlyDeficit() {
        return onlyDeficit;
    }

    public void setOnlyDeficit(Boolean onlyDeficit) {
        this.onlyDeficit = onlyDeficit;
    }

    public Boolean onlyCritical() {
        return onlyCritical;
    }

    public void setOnlyCritical(Boolean onlyCritical) {
        this.onlyCritical = onlyCritical;
    }

    public Boolean noMovement() {
        return noMovement;
    }

    public void setNoMovement(Boolean noMovement) {
        this.noMovement = noMovement;
    }

    public Boolean hasReserve() {
        return hasReserve;
    }

    public void setHasReserve(Boolean hasReserve) {
        this.hasReserve = hasReserve;
    }
}
