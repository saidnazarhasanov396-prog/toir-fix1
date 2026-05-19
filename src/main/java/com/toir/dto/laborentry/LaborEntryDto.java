package com.toir.dto.laborentry;

import com.toir.entity.LaborEntry;
import com.toir.entity.users.User;
import com.toir.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record LaborEntryDto(
        UUID id,
        UUID workOrderId,
        UUID userId,
        UserRef user,
        String contractorName,
        @NotNull LocalDate workDate,
        @PositiveOrZero double hours,
        Double rate,
        String description
) {
    public record UserRef(
            UUID id,
            String fullName,
            String username,
            String email,
            String phone,
            UserStatus status
    ) {
        public static UserRef from(User user) {
            return new UserRef(
                    user.getId(),
                    user.getFullName(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getStatus()
            );
        }
    }

    public static LaborEntryDto from(LaborEntry l) {
        return from(l, null);
    }

    public static LaborEntryDto from(LaborEntry l, UserRef user) {
        return new LaborEntryDto(l.getId(), l.getWorkOrderId(), l.getUserId(), user, l.getContractorName(),
                l.getWorkDate(), l.getHours(), l.getRate(), l.getDescription());
    }
}
