package com.pbot.bot.service

import com.pbot.bot.github.GitHubClient
import com.pbot.bot.github.PullRequestFile
import com.pbot.bot.llm.GptClient
import com.pbot.bot.llm.ReviewIssue
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ReviewService(
    private val gitHubClient: GitHubClient,
    private val gptClient: GptClient,
) {
    @Async
    fun reviewPullRequest(repo: String, number: Int) {
        val files = gitHubClient.fetchFiles(repo, number)
        val annotatedDiff = files.joinToString("\n\n") { annotatePatch(it) }
        val result = gptClient.review(annotatedDiff)

        val validIssues = result.issues.filter { isLineInDiff(it, files) }
        val comments = validIssues.map {
            mapOf(
                "path" to it.path,
                "line" to it.line,
                "side" to "RIGHT",
                "body" to it.comment,
            )
        }

        gitHubClient.postReview(repo, number, result.summary, comments)
    }

    private fun isLineInDiff(issue: ReviewIssue, files: List<PullRequestFile>): Boolean {
        val file = files.find { it.path == issue.path } ?: return false
        val patch = file.patch ?: return false
        return lineNumbersInDiff(patch).contains(issue.line)
    }

    /**
     * patch에 라인 번호를 붙여서 LLM이 정확한 라인을 짚을 수 있게 한다.
     *
     * 출력 예:
     * ```
     * === src/main/kotlin/Foo.kt ===
     * @@ -10,5 +14,8 @@
     * L14     class Foo(
     * L15 [+]     @Async
     * L16     fun bar() {
     * L-  [-]     val old = ...
     * ```
     */
    private fun annotatePatch(file: PullRequestFile): String {
        val sb = StringBuilder()
        sb.appendLine("=== ${file.path} ===")
        val patch = file.patch ?: run {
            sb.appendLine("(binary or no patch)")
            return sb.toString()
        }
        var newLine = 0
        for (raw in patch.lines()) {
            when {
                raw.startsWith("@@") -> {
                    val match = Regex("""\+(\d+)""").find(raw) ?: continue
                    newLine = match.groupValues[1].toInt()
                    sb.appendLine(raw)
                }
                raw.startsWith("+++") -> {}
                raw.startsWith("---") -> {}
                raw.startsWith("+") -> {
                    sb.appendLine("L%-4d [+] %s".format(newLine, raw.substring(1)))
                    newLine++
                }
                raw.startsWith("-") -> {
                    sb.appendLine("L--   [-] %s".format(raw.substring(1)))
                }
                else -> {
                    val content = if (raw.startsWith(" ")) raw.substring(1) else raw
                    sb.appendLine("L%-4d     %s".format(newLine, content))
                    newLine++
                }
            }
        }
        return sb.toString()
    }

    /**
     * patch 텍스트를 파싱해서 diff hunk 안에 있는 새 파일 라인 번호 집합을 만든다.
     * 추가된 라인(+) 뿐 아니라 컨텍스트 라인(변경 안 된 주변 라인)도 인라인 코멘트 가능.
     */
    private fun lineNumbersInDiff(patch: String): Set<Int> {
        val lines = mutableSetOf<Int>()
        var newLine = 0
        for (raw in patch.lines()) {
            when {
                raw.startsWith("@@") -> {
                    val match = Regex("""\+(\d+)""").find(raw) ?: continue
                    newLine = match.groupValues[1].toInt()
                }
                raw.startsWith("+++") -> {}
                raw.startsWith("---") -> {}
                raw.startsWith("+") -> {
                    lines.add(newLine)
                    newLine++
                }
                raw.startsWith("-") -> {}
                else -> {
                    lines.add(newLine)
                    newLine++
                }
            }
        }
        return lines
    }
}
