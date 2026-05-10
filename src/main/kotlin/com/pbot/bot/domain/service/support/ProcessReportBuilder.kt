package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ProcessNote

/**
 * [ProcessReviewer] 결과를 별도 PR 코멘트용 markdown으로 변환.
 *
 * 항목 0개면 [build] 가 null을 반환해 게시 자체를 생략.
 * 시각적 무게 순(HIGH → MEDIUM → LOW)으로 정렬해 사용자가 위에서부터 우선순위대로 본다.
 *
 * 형식:
 * ```
 * ## 🐶 Pawranoid Process Review
 *
 * - 🔴 **HIGH** — ...
 * - 🟡 **MEDIUM** — ...
 * - 🟢 **LOW** — ...
 *
 * ---
 * *코드가 아닌 PR 자체에 대한 평가입니다.*
 * ```
 */
object ProcessReportBuilder {

    fun build(notes: List<ProcessNote>): String? {
        if (notes.isEmpty()) return null
        val ordered = notes.sortedBy { it.severity.ordinal } // HIGH=0 우선
        return buildString {
            appendLine("## 🐶 Pawranoid Process Review")
            appendLine()
            ordered.forEach { note ->
                appendLine("- ${note.severity.emoji} **${note.severity.name}** — ${note.message}")
            }
            appendLine()
            appendLine("---")
            append("*코드가 아닌 PR 자체에 대한 평가입니다.*")
        }
    }
}
