package com.toir.dto.repairacceptance;

public record RepairAcceptanceRunInRequest(
        Integer runInShiftsRequired,
        String remarks
) {
}
