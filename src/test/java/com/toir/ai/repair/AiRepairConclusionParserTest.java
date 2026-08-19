package com.toir.ai.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRepairConclusionParserTest {

    private final AiRepairConclusionParser parser = new AiRepairConclusionParser();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void visualInspectionWithDefectsIsBroken() throws Exception {
        ObjectNode report = mapper.createObjectNode();
        report.put("sufficient_for_inspection", true);
        report.put("highest_severity", "low");
        report.put("summary_uz", "Podshipnikda shovqin");
        report.putArray("defects").addObject()
                .put("type", "bearing_wear")
                .put("description_uz", "Podshipnik yeyilgan")
                .put("severity", "low");
        report.putArray("recommendations_uz").add("Podshipnikni almashtirish");
        ObjectNode payload = mapper.createObjectNode();
        payload.set("inspection_report", report);

        AiRepairConclusion conclusion = parser.parse(AiRepairKind.VISUAL_INSPECTION, payload);

        assertThat(conclusion.broken()).isTrue();
        assertThat(conclusion.defects()).hasSize(1);
        assertThat(conclusion.problemKey()).contains("bearing_wear");
        assertThat(conclusion.description()).contains("Podshipnikni almashtirish");
    }

    @Test
    void visualInspectionWithoutDefectsIsNotBroken() throws Exception {
        ObjectNode report = mapper.createObjectNode();
        report.put("sufficient_for_inspection", true);
        report.put("inspection_status", "normal");
        report.put("total_defects", 0);
        ObjectNode payload = mapper.createObjectNode();
        payload.set("result", mapper.createObjectNode().set("inspection_report", report));

        AiRepairConclusion conclusion = parser.parse(AiRepairKind.VISUAL_INSPECTION, payload);

        assertThat(conclusion.broken()).isFalse();
    }

    @Test
    void insufficientMediaWithoutDefectsIsSkipped() {
        ObjectNode report = mapper.createObjectNode();
        report.put("sufficient_for_inspection", false);
        ObjectNode payload = mapper.createObjectNode();
        payload.set("inspection_report", report);

        AiRepairConclusion conclusion = parser.parse(AiRepairKind.VISUAL_INSPECTION, payload);

        assertThat(conclusion.broken()).isFalse();
        assertThat(conclusion.insufficientMedia()).isTrue();
    }

    @Test
    void causeRepairWithGuidanceIsBroken() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("cause", "Seal leak");
        payload.put("repair_action", "Replace mechanical seal");

        AiRepairConclusion conclusion = parser.parse(AiRepairKind.CAUSE_REPAIR, payload);

        assertThat(conclusion.broken()).isTrue();
        assertThat(conclusion.description()).contains("Replace mechanical seal");
        assertThat(conclusion.problemKey()).contains("seal");
    }

    @Test
    void workOrderDraftMatchOnlyIsNotBroken() {
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("matching_work_orders").addObject().put("number", "WO-1");

        AiRepairConclusion conclusion = parser.parse(AiRepairKind.WORK_ORDER_DRAFT, payload);

        assertThat(conclusion.broken()).isFalse();
    }
}
