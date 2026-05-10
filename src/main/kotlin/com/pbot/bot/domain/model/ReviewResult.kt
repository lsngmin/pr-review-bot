package com.pbot.bot.domain.model

/**
 * 인라인 코멘트 한 건. PR의 특정 파일/라인에 달리는 단일 피드백.
 *
 * 사용자가 한 클릭으로 적용 가능한 코드 제안을 [suggestion] 으로 함께 줄 수 있다.
 * 다중 라인 제안은 [startLine] 과 [line] 으로 범위를 표현 (start ≤ end).
 *
 * @property path 코멘트 대상 파일 경로
 * @property line 코멘트가 달릴 새 파일 기준 라인 번호 (다중 라인이면 마지막 라인)
 * @property startLine 다중 라인 제안의 시작 라인. 단일 라인이면 null
 * @property severity 이슈 심각도
 * @property comment 한국어 피드백 본문
 * @property suggestion 적용 가능한 코드 제안. null이면 일반 코멘트
 */
data class ReviewIssue(
    val path: String,
    val line: Int,
    val startLine: Int?,
    val severity: Severity,
    val comment: String,
    val suggestion: String?,
)

/**
 * LLM이 돌려준 코드 리뷰 전체 결과.
 *
 * - [overview]: PR Review body 로 게시될 종합 요약 (intent/changes/files)
 * - [summary]: 자유 텍스트 응답 슬롯. 메인 코드 리뷰에서는 표시되지 않으나 schema 에
 *   필수로 남겨두어 verify 같은 자유 응답 use case 가 같은 schema 를 재사용한다.
 * - [issues]: 라인별 인라인 코멘트
 */
data class ReviewResult(
    val overview: PrOverview,
    val summary: String,
    val issues: List<ReviewIssue>,
)
