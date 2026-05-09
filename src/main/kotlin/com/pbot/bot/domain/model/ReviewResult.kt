package com.pbot.bot.domain.model

/**
 * 인라인 코멘트 한 건. PR의 특정 파일/라인에 달리는 단일 피드백.
 *
 * 사용자가 한 클릭으로 적용 가능한 코드 제안을 [suggestion] 으로 함께 줄 수 있다.
 * 다중 라인 제안은 [startLine] 과 [line] 으로 범위를 표현 (start ≤ end).
 *
 * @property path 코멘트 대상 파일 경로 (예: `src/main/kotlin/Foo.kt`)
 * @property line 코멘트가 달릴 새 파일 기준 라인 번호 (다중 라인 제안 시 마지막 라인)
 * @property startLine 다중 라인 제안의 시작 라인. 단일 라인이면 null
 * @property comment 한국어 피드백 본문
 * @property suggestion 적용 가능한 코드 제안. null이면 일반 코멘트
 */
data class ReviewIssue(
    val path: String,
    val line: Int,
    val startLine: Int?,
    val comment: String,
    val suggestion: String?,
)

/**
 * LLM이 돌려준 코드 리뷰 전체 결과.
 *
 * summary는 PR 전체에 대한 한두 문장 총평으로 "Pawranoid reviewed" 박스에 표시되고,
 * issues의 각 항목은 코드 라인 옆 인라인 코멘트로 매핑된다.
 *
 * @property summary PR 전체 총평 (1~3문장)
 * @property issues 라인별 인라인 코멘트 목록 (없을 수 있음)
 */
data class ReviewResult(
    val summary: String,
    val issues: List<ReviewIssue>,
)
