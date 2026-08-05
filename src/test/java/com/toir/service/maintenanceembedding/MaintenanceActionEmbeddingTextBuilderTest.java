package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.entity.maintenance.MaintenanceAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceActionEmbeddingTextBuilderTest {

    private MaintenanceActionSemanticSearchProperties properties;
    private MaintenanceActionEmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new MaintenanceActionSemanticSearchProperties();
        configureLimits(properties.getInputLimits(), 10_000);
        builder = new MaintenanceActionEmbeddingTextBuilder(properties);
    }

    @Test
    void buildsExactVersionedFieldOrderAndUtf8Hash() {
        MaintenanceAction action = action();
        action.setName("  Nasos\t podshipnigi ");
        action.setCategory(" Mexanik\n ta'mir ");
        action.setRequiredSkill(" Usta ");
        action.setSafetyNotes(" Tokni\u00A0 uzing ");
        action.setToolsRequired(" Kalit ");
        action.setSparePartsRequired(" Подшипник ");
        action.setConsumablesRequired(" Moy ");

        var result = builder.build(action);

        assertThat(MaintenanceActionEmbeddingTextBuilder.SOURCE_SCHEMA_VERSION)
                .isEqualTo("maintenance-action-text-v1");
        assertThat(result.normalizedText()).isEqualTo("""
                Nasos podshipnigi
                Mexanik ta'mir
                Usta
                Tokni uzing
                Kalit
                Подшипник
                Moy""");
        assertThat(result.sourceTextHash())
                .isEqualTo("aaf186f166f398e0b246736ad570794e432d08dff29cc25632b8b92ded5587ef");
        assertThat(result.utf8Bytes()).isGreaterThan(result.normalizedText().length());
        assertThat(result.blank()).isFalse();
    }

    @Test
    void preservesUzbekRussianAndEnglishWithoutCaseFoldingOrTranslation() {
        MaintenanceAction action = action();
        action.setName("O‘ZBEK Насос PUMP");

        assertThat(builder.build(action).normalizedText()).isEqualTo("O‘ZBEK Насос PUMP");
    }

    @Test
    void omitsBlankFieldsAndCanRepresentBlankLegacySourceAsSkippedInput() {
        MaintenanceAction action = action();
        action.setName(" \t\n ");
        action.setCategory(null);

        var result = builder.build(action);

        assertThat(result.normalizedText()).isEmpty();
        assertThat(result.blank()).isTrue();
        assertThat(result.sourceTextHash())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void rejectsPerFieldAndComposedLimits() {
        MaintenanceAction action = action();
        action.setName("abcd");
        properties.getInputLimits().setNameCharacters(3);

        assertThatThrownBy(() -> builder.build(action))
                .isInstanceOf(MaintenanceActionEmbeddingTextBuilder.InputLimitExceededException.class)
                .hasMessageContaining("name");

        properties.getInputLimits().setNameCharacters(10);
        properties.getInputLimits().setComposedUtf8Bytes(3);
        assertThatThrownBy(() -> builder.build(action))
                .isInstanceOf(MaintenanceActionEmbeddingTextBuilder.InputLimitExceededException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void refusesToBuildUntilAiTeamLimitsAreConfigured() {
        MaintenanceActionEmbeddingTextBuilder unconfigured =
                new MaintenanceActionEmbeddingTextBuilder(new MaintenanceActionSemanticSearchProperties());

        assertThatThrownBy(() -> unconfigured.build(action()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI-team input limits are not configured");
    }

    private static MaintenanceAction action() {
        MaintenanceAction action = new MaintenanceAction();
        action.setName("Action");
        return action;
    }

    private static void configureLimits(
            MaintenanceActionSemanticSearchProperties.InputLimits limits,
            int value
    ) {
        limits.setNameCharacters(value);
        limits.setCategoryCharacters(value);
        limits.setRequiredSkillCharacters(value);
        limits.setSafetyNotesCharacters(value);
        limits.setToolsRequiredCharacters(value);
        limits.setSparePartsRequiredCharacters(value);
        limits.setConsumablesRequiredCharacters(value);
        limits.setComposedCharacters(value);
        limits.setComposedUtf8Bytes(value);
    }
}
