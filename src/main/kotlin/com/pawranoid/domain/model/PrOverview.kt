package com.pawranoid.domain.model

/**
 * PR Review body 에 게시되는 종합 요약.
 * "이 PR이 뭐 하는 거지?" 처음 본 사람을 위한 한 페이지짜리 오리엔테이션.
 *
 * @property intent PR 전체 개요 (2~4 문장, 친절한 설명)
 * @property changes 변경 사항 글머리 (한 줄 한 줄, change-level)
 * @property files 파일별 분류 (collapsible 표 데이터)
 */
data class PrOverview(
    val intent: String,
    val changes: List<String>,
    val files: List<FileChange>,
)

data class FileChange(
    val path: String,
    val type: FileChangeType,
    val summary: String,
)

enum class FileChangeType {
    REFACTOR,
    NEW,
    FIX,
    CONFIG,
    DEPENDENCY,
    TEST,
    DOC,
    STYLE;

    val label: String
        get() = when (this) {
            REFACTOR -> "Refactor"
            NEW -> "New"
            FIX -> "Fix"
            CONFIG -> "Config"
            DEPENDENCY -> "Dep"
            TEST -> "Test"
            DOC -> "Doc"
            STYLE -> "Style"
        }
}
