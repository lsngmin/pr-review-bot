package com.pbot.bot.domain.model

/**
 * PR 메인 conversation 탭에 게시되는 종합 요약.
 * 인라인 리뷰가 "라인별 디테일" 이라면 walkthrough는 "이 PR이 뭐 하는 거지?"
 * 처음 본 사람을 위한 한 페이지짜리 오리엔테이션.
 *
 * @property intent PR 전체 의도 (1~3 문장)
 * @property files 파일별 변경 분류
 * @property risks 리뷰어가 우선 봐야 할 위험 항목 (없으면 빈 리스트)
 */
data class Walkthrough(
    val intent: String,
    val files: List<FileChange>,
    val risks: List<RiskHighlight>,
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

data class RiskHighlight(
    val severity: Severity,
    val description: String,
    val location: String?,
)
