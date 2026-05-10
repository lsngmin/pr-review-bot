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
 * ### 변경사항
 * - change 1
 * - change 2
 *
 * ### 검토된 변경 사항
 * Pawranoid는 이 풀 리퀘스트에서 변경된 파일 N개 중 M개를 검토하고 K개의 댓글을 생성했습니다.
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
            appendLine("### 변경사항")
            walkthrough.changes.forEach { appendLine("- $it") }
        }

        appendLine()
        appendLine("### 검토된 변경 사항")
        append(
            "Pawranoid는 이 풀 리퀘스트에서 변경된 파일 ${totalFileCount}개 중 ${reviewedFileCount}개를 " +
                "검토하고 ${inlineCommentCount}개의 댓글을 생성했습니다.",
        )
        if (droppedCommentCount > 0) {
            append(" (${droppedCommentCount}개 의견은 라인 매칭 실패로 보류)")
        }
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
}
