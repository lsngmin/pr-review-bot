package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.Walkthrough

/**
 * walkthrough + 평가 + 리뷰 통계를 합쳐 PR Review body 로 게시할 markdown 으로 변환.
 *
 * 형식:
 * ```
 * ## Pawranoid PR overview
 *
 * <intent paragraph>
 *
 * ### What Changed
 * - change 1
 * - change 2
 *
 * ### What Reviewed
 * 변경된 파일 N개를 모두 살펴봤어요. 인라인 코멘트는 K개 남겼습니다.
 *
 * <details>
 * <summary>파일별 요약</summary>
 *
 * | File | Type | Summary |
 * | Foo.kt | New | ... |
 *
 * </details>
 *
 * > **머지 가능** — ...
 * >
 * > **사이즈가 큽니다** ... — ...
 * ```
 *
 * 별도 PR 대화 코멘트로 walkthrough 를 분리해 띄우지 않는다 — Copilot 식으로
 * 한 곳(Review body)에 모여 있어야 사용자가 PR을 한 화면에서 파악하기 쉽다.
 */
object WalkthroughBuilder {

    fun build(
        walkthrough: Walkthrough,
        evaluation: List<String> = emptyList(),
        reviewedFileCount: Int = walkthrough.files.size,
        totalFileCount: Int = walkthrough.files.size,
        inlineCommentCount: Int = 0,
        droppedCommentCount: Int = 0,
    ): String = buildString {
        appendLine("## Pawranoid PR overview")
        appendLine()

        appendLine(walkthrough.intent)

        if (walkthrough.changes.isNotEmpty()) {
            appendLine()
            appendLine("### What Changed")
            walkthrough.changes.forEach { appendLine("- $it") }
        }

        appendLine()
        appendLine("### What Reviewed")
        append(reviewStatsLine(reviewedFileCount, totalFileCount, inlineCommentCount, droppedCommentCount))
        appendLine()

        if (walkthrough.files.isNotEmpty()) {
            appendLine()
            appendLine("<details>")
            appendLine("<summary>파일별 요약</summary>")
            appendLine()
            appendLine("| File | Type | Summary |")
            appendLine("|------|------|---------|")
            walkthrough.files.forEach { file ->
                val name = file.path.substringAfterLast('/')
                appendLine("| `$name` | ${file.type.label} | ${file.summary} |")
            }
            appendLine()
            appendLine("</details>")
        }

        if (evaluation.isNotEmpty()) {
            appendLine()
            evaluation.forEachIndexed { i, line ->
                appendLine("> $line")
                if (i < evaluation.size - 1) appendLine(">")
            }
        }
    }

    /**
     * Copilot 식 영문 통계 ("Copilot reviewed N out of M ... and generated K comments")
     * 와 차별화하기 위해 한국어 conversational 톤으로 작성. 부분 검토(maxFiles cap)
     * 인 경우 "N개 중 M개" 형식으로 분기.
     */
    private fun reviewStatsLine(
        reviewedFileCount: Int,
        totalFileCount: Int,
        inlineCommentCount: Int,
        droppedCommentCount: Int,
    ): String {
        val reviewedPart = if (reviewedFileCount == totalFileCount) {
            "변경된 파일 ${totalFileCount}개를 모두 살펴봤어요"
        } else {
            "변경된 파일 ${totalFileCount}개 중 ${reviewedFileCount}개를 살펴봤어요"
        }
        val commentsPart = if (inlineCommentCount > 0) {
            "인라인 코멘트는 ${inlineCommentCount}개 남겼습니다"
        } else {
            "인라인 코멘트는 따로 남기지 않았습니다"
        }
        val droppedPart = if (droppedCommentCount > 0) {
            " (${droppedCommentCount}개 의견은 라인 매칭 실패로 보류)"
        } else {
            ""
        }
        return "$reviewedPart. $commentsPart.$droppedPart"
    }
}
