package com.toir.dto.knowledge;

import java.util.List;

public record KnowledgeSuggestionDto(
        KnowledgeArticleDto article,
        int score,
        List<String> matchReasons
) {
}
