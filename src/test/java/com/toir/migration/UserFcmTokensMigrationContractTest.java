package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserFcmTokensMigrationContractTest {

    @Test
    void migrationCreatesUserFcmTokensTableAndIndexes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V20260615_1__user_fcm_tokens.sql");

        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("create table if not exists user_fcm_tokens");
        assertThat(sql).contains("user_id uuid not null");
        assertThat(sql).contains("token text not null");
        assertThat(sql).contains("platform varchar(16) not null");
        assertThat(sql).contains("last_seen_at timestamptz not null");
        assertThat(sql).contains("foreign key (user_id) references users(id)");
        assertThat(sql).contains("ux_user_fcm_tokens_token");
        assertThat(sql).contains("idx_user_fcm_tokens_user_active");
    }
}
