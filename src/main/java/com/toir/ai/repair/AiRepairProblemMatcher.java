package com.toir.ai.repair;

import com.toir.entity.defects.Defect;
import com.toir.entity.repair.RepairRequest;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AiRepairProblemMatcher {

    private AiRepairProblemMatcher() {
    }

    static RepairRequest findMatch(
            List<RepairRequest> candidates,
            Collection<Defect> defectsByRequest,
            AiRepairConclusion conclusion
    ) {
        if (candidates == null || candidates.isEmpty() || conclusion == null) {
            return null;
        }
        Set<String> conclusionTokens = tokensOf(conclusion);
        RepairRequest keyMatch = null;
        RepairRequest overlapMatch = null;
        int overlapScore = 0;
        for (RepairRequest candidate : candidates) {
            if (StringUtils.hasText(candidate.getAiProblemKey())
                    && candidate.getAiProblemKey().equals(conclusion.problemKey())) {
                keyMatch = candidate;
                break;
            }
            Set<String> candidateTokens = tokensOf(candidate, defectsFor(defectsByRequest, candidate));
            int score = overlap(conclusionTokens, candidateTokens);
            if (score > overlapScore) {
                overlapScore = score;
                overlapMatch = candidate;
            }
        }
        if (keyMatch != null) {
            return keyMatch;
        }
        if (overlapScore > 0) {
            return overlapMatch;
        }
        if (conclusionTokens.size() == 1 && conclusionTokens.contains("general") && candidates.size() == 1) {
            return candidates.getFirst();
        }
        return null;
    }

    private static List<Defect> defectsFor(Collection<Defect> all, RepairRequest request) {
        if (all == null || request == null || request.getId() == null) {
            return List.of();
        }
        return all.stream()
                .filter(defect -> request.getId().equals(defect.getRepairRequestId()))
                .toList();
    }

    private static Set<String> tokensOf(AiRepairConclusion conclusion) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addNormalized(tokens, conclusion.problemKey());
        if (conclusion.problemKey() != null && conclusion.problemKey().contains("+")) {
            for (String part : conclusion.problemKey().split("\\+")) {
                addNormalized(tokens, part);
            }
        }
        for (AiRepairDefect defect : conclusion.defects()) {
            addNormalized(tokens, defect.type());
            addNormalized(tokens, defect.title());
        }
        return tokens;
    }

    private static Set<String> tokensOf(RepairRequest request, List<Defect> defects) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addNormalized(tokens, request.getAiProblemKey());
        if (request.getAiProblemKey() != null && request.getAiProblemKey().contains("+")) {
            for (String part : request.getAiProblemKey().split("\\+")) {
                addNormalized(tokens, part);
            }
        }
        addNormalized(tokens, request.getTitle());
        for (Defect defect : defects) {
            addNormalized(tokens, defect.getCategory());
            addNormalized(tokens, defect.getTitle());
        }
        return tokens;
    }

    private static void addNormalized(Set<String> tokens, String value) {
        String normalized = AiJson.normalizeKey(value);
        if (normalized != null && !"general".equals(normalized)) {
            tokens.add(normalized);
        }
        if ("general".equals(normalized)) {
            tokens.add(normalized);
        }
    }

    private static int overlap(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String token : left) {
            if ("general".equals(token)) {
                continue;
            }
            if (right.contains(token)) {
                score++;
                continue;
            }
            for (String other : right) {
                if ("general".equals(other)) {
                    continue;
                }
                if (token.contains(other) || other.contains(token)) {
                    score++;
                    break;
                }
            }
        }
        return score;
    }
}
