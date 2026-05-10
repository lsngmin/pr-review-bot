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
        val prefix = commonDirPrefix(walkthrough.files.map { it.path })
        if (prefix.isNotEmpty()) {
            appendLine("_Paths relative to_ `$prefix`")
            appendLine()
        }
        appendLine("| File | Type | Summary |")
        appendLine("|------|------|---------|")
        walkthrough.files.forEach { file ->
            val display = file.path.removePrefix(prefix)
            appendLine("| `$display` | ${file.type.label} | ${file.summary} |")
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

    // 표 가독성을 위해 모든 파일이 공유하는 디렉토리 prefix를 한 번만 표기하고 행에서는 제외.
    // 2개 미만이거나 prefix가 너무 짧으면(절약 효과 < 10자) 그대로 둔다.
    private fun commonDirPrefix(paths: List<String>): String {
        if (paths.size < 2) return ""
        var idx = paths[0].length
        for (other in paths.drop(1)) {
            idx = minOf(idx, sharedLength(paths[0], other))
            if (idx == 0) return ""
        }
        val lastSlash = paths[0].substring(0, idx).lastIndexOf('/')
        if (lastSlash < 0) return ""
        val prefix = paths[0].substring(0, lastSlash + 1)
        return if (prefix.length >= 10) prefix else ""
    }

    private fun sharedLength(a: String, b: String): Int {
        val len = minOf(a.length, b.length)
        for (i in 0 until len) if (a[i] != b[i]) return i
        return len
    }
}
