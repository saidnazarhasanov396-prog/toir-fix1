package com.toir.dto.knowledge;

import java.util.List;

public record KnowledgeContextResponse(
        List<KnowledgeArticleDto> linked,
        List<KnowledgeSuggestionDto> suggestions
) {
}
