package com.toir.dto.knowledge;

public record KnowledgeStatsResponse(
        long totalArticles,
        long lessonLearned,
        long procedures,
        long troubleshooting
) {
}
