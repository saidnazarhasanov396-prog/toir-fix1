package com.toir.enums;

public enum ApprovalStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    REWORK,
    CANCELLED,
    EXPIRED,
    FAILED,
    SUPERSEDED;

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isActionable() {
        return this == PENDING;
    }

    public boolean isTerminal() {
        return switch (this) {
            case APPROVED, REJECTED, CANCELLED, EXPIRED, FAILED, SUPERSEDED -> true;
            case DRAFT, PENDING, REWORK -> false;
        };
    }
}
