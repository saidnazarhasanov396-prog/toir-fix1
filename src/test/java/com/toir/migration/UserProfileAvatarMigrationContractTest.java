package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileAvatarMigrationContractTest {

    @Test
    void addsNullableUserAvatarReferenceWithoutDestructiveChanges() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260730_1__user_profile_avatar.sql"
        ));

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS avatar_file_id uuid");
        assertThat(sql).contains("REFERENCES uploaded_files(id)");
        assertThat(sql).doesNotContain("NOT NULL");
        assertThat(sql).doesNotContain("DROP ");
    }
}
