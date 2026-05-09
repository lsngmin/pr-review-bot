package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ReviewIssue

/**
 * LLM의 summary 본문에 인라인으로 못 단 issue들을 합쳐 잃지 않게 한다.
 *
 * GitHub 인라인 코멘트는 diff hunk 안의 라인에만 가능. LLM이 그 외 라인을 짚으면
 * 인라인은 못 달지만 의견 자체는 가치 있을 수 있다 — 그래서 summary 박스에 노출.
 */
object SummaryBuilder {

    /**
     * [original] summary에 [dropped] 이슈들을 "추가 의견" 섹션으로 덧붙인다.
     * dropped가 비어있으면 [original] 그대로 반환.
     */
    fun mergeDroppedIntoSummary(original: String, dropped: List<ReviewIssue>): String {
        if (dropped.isEmpty()) return original
        return buildString {
            append(original)
            append("\n\n**추가 의견 (인라인 위치 매칭 실패):**\n")
            dropped.forEach { append("- `${it.path}:${it.line}` ${it.comment}\n") }
        }
    }
}
