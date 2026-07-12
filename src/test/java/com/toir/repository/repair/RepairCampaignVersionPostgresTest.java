package com.toir.repository.repair;

import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TOIR_TEST_POSTGRES_URL", matches = ".+")
class RepairCampaignVersionPostgresTest {

    @Autowired
    private RepairCampaignRepository repository;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TOIR_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TOIR_TEST_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TOIR_TEST_POSTGRES_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void flushedUpdateAdvancesDatabaseAndReturnedDtoVersionExactlyOnce() {
        RepairCampaign campaign = new RepairCampaign();
        campaign.setCode("RC-PG-VERSION-1");
        campaign.setName("Before");
        campaign.setStatus(RepairCampaignStatus.DRAFT);
        campaign.setScopeType(RepairCampaignScopeType.CUSTOM);
        campaign.setStartDate(LocalDate.of(2026, 1, 1));
        campaign.setEndDate(LocalDate.of(2026, 1, 1));
        campaign.setTotalBudget(new BigDecimal("1.0000"));
        campaign.setTotalActual(BigDecimal.ZERO);
        campaign.setCurrencyCode("UZS");
        campaign.setClosureVersion(0L);

        RepairCampaign inserted = repository.saveAndFlush(campaign);
        long initialVersion = inserted.getVersion();
        inserted.setName("After");
        RepairCampaign flushed = repository.saveAndFlush(inserted);
        RepairCampaignDto response = RepairCampaignDto.from(flushed);

        entityManager.clear();
        RepairCampaign reloaded = repository.findById(flushed.getId()).orElseThrow();
        assertThat(response.version()).isEqualTo(initialVersion + 1);
        assertThat(reloaded.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(reloaded.getName()).isEqualTo("After");
    }
}
