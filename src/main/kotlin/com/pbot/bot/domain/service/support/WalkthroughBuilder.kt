package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.Walkthrough

/**
 * [Walkthrough] 도메인 객체를 PR 메인 conversation 탭에 게시할 markdown으로 변환한다.
 *
 * 형식:
 * ```
 * ## 🐶 Pawranoid Review
 *
 * ### ① What changed
 * <intent>
 *
 * ### ② Files changed
 * | File | Type | Summary |
 * | Foo.kt | New | ... |
 *
 * > **머지 가능** — ...           (evaluation 라인이 있을 때 표 바로 아래 blockquote)
 * >
 * > **사이즈가 큽니다** ... — ...
 *
 * ### ③ Risk highlights      (risks가 있을 때만)
 * - 🔴 HIGH — ...
 * ```
 *
 * 표 폭을 줄이기 위해 File 컬럼은 디렉토리를 떼고 파일명만 표시.
 *
 * evaluation 은 PR 자체에 대한 결정적 평가 라인 리스트로, [PrEvaluator] 가 생성한다.
 * 항상 표시되는 머지 상태 한 줄 + 조건부 사이즈/테스트 라인.
 */
object WalkthroughBuilder {

    fun build(
        walkthrough: Walkthrough,
        evaluation: List<String> = emptyList(),
    ): String = buildString {
        appendLine("## 🐶 Pawranoid Review")
        appendLine()

        appendLine("### ① What changed")
        appendLine(walkthrough.intent)
        appendLine()

        appendLine("### ② Files changed")
        appendLine("| File | Type | Summary |")
        appendLine("|------|------|---------|")
        walkthrough.files.forEach { file ->
            val name = file.path.substringAfterLast('/')
            appendLine("| `$name` | ${file.type.label} | ${file.summary} |")
        }
        appendLine()

        if (evaluation.isNotEmpty()) {
            evaluation.forEachIndexed { i, line ->
                appendLine("> $line")
                if (i < evaluation.size - 1) appendLine(">")
            }
            appendLine()
        }

        if (walkthrough.risks.isNotEmpty()) {
            appendLine("### ③ Risk highlights")
            walkthrough.risks.forEach { risk ->
                val location = risk.location?.let { " (`$it`)" } ?: ""
                appendLine("- ${risk.severity.emoji} **${risk.severity.name}** — ${risk.description}$location")
            }
        }
    }
}
