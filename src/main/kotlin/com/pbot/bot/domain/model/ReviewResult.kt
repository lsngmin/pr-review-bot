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
 * @property severity 이슈 심각도 — walkthrough 통계 + 인라인 prefix 표시에 사용
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
 * - [walkthrough]: PR 메인 thread에 게시될 종합 요약
 * - [summary]: 인라인 review API의 body로 들어가는 짧은 총평
 * - [issues]: 라인별 인라인 코멘트
 *
 * @property walkthrough PR 종합 요약
 * @property summary PR 인라인 review의 본문 (1~3문장)
 * @property issues 라인별 인라인 코멘트 목록
 */
data class ReviewResult(
    val walkthrough: Walkthrough,
    val summary: String,
    val issues: List<ReviewIssue>,
)
