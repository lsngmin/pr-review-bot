package com.pbot.bot.domain.model

/**
 * PR 자체(코드가 아닌 프로세스)에 대한 결정적 평가 항목.
 *
 * 예: "PR이 47개 파일·3,200줄로 너무 큼", "테스트가 없음", "PR 제목이 'update'".
 * LLM 토큰 0, 재현 가능, 빠름. 룰은 [com.pbot.bot.domain.service.support.ProcessReviewer] 참고.
 */
data class ProcessNote(
    val severity: Severity,
    val message: String,
)
