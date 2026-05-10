package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ReviewIssue
import com.pbot.bot.domain.model.Severity
import com.pbot.bot.domain.model.Walkthrough

/**
 * [Walkthrough] 도메인 객체를 PR 메인 conversation 탭에 게시할 markdown으로 변환한다.
 *
 * 형식:
 * ```
 * ## 🐶 Pawranoid Walkthrough
 *
 * ### 📝 What changed
 * <intent>
 *
 * ### 📂 Files changed
 * | File | Type | Summary |
 * ...
 *
 * ### ⚠️ Risk highlights      (risks가 있을 때만)
 * - 🔴 HIGH — ...
 *
 * ### 🔍 Reviewed
 * - N issues found: 🔴 a · 🟡 b · 🟢 c   (또는 No issues found.)
 * - M suggestions with auto-fix available  (있을 때만)
 *
 * ---
 * *Triggered by `/review`*
 * ```
 */
object WalkthroughBuilder {

    fun build(walkthrough: Walkthrough, issues: List<ReviewIssue>): String = buildString {
        appendLine("## 🐶 Pawranoid Walkthrough")
        appendLine()

        appendLine("### 📝 What changed")
        appendLine(walkthrough.intent)
        appendLine()

        appendLine("### 📂 Files changed")
        appendLine("| File | Type | Summary |")
        appendLine("|------|------|---------|")
        walkthrough.files.forEach { file ->
            appendLine("| `${file.path}` | ${file.type.label} | ${file.summary} |")
        }
        appendLine()

        if (walkthrough.risks.isNotEmpty()) {
            appendLine("### ⚠️ Risk highlights")
            walkthrough.risks.forEach { risk ->
                val location = risk.location?.let { " (`$it`)" } ?: ""
                appendLine("- ${risk.severity.emoji} **${risk.severity.name}** — ${risk.description}$location")
            }
            appendLine()
        }

        appendLine("### 🔍 Reviewed")
        if (issues.isEmpty()) {
            appendLine("No issues found. Looks good.")
        } else {
            val high = issues.count { it.severity == Severity.HIGH }
            val med = issues.count { it.severity == Severity.MEDIUM }
            val low = issues.count { it.severity == Severity.LOW }
            appendLine("- **${issues.size} issues found**: 🔴 $high · 🟡 $med · 🟢 $low")

            val suggestions = issues.count { !it.suggestion.isNullOrBlank() }
            if (suggestions > 0) {
                appendLine("- **$suggestions suggestions** with auto-fix available")
            }
        }
        appendLine()

        append("---")
        appendLine()
        append("*Triggered by `/review`*")
    }
}
