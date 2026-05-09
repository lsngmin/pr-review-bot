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
        val diff = gitHubClient.fetchDiff(repo, number)
        val files = gitHubClient.fetchFiles(repo, number)
        val result = gptClient.review(diff)

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
     * patch 텍스트를 파싱해서 diff hunk 안에 있는 새 파일 라인 번호 집합을 만든다.
     * 추가된 라인(+) 뿐 아니라 컨텍스트 라인(변경 안 된 주변 라인)도 인라인 코멘트 가능.
     */
    private fun lineNumbersInDiff(patch: String): Set<Int> {
        val lines = mutableSetOf<Int>()
        var newLine = 0
        for (raw in patch.lines()) {
            when {
                raw.startsWith("@@") -> {
                    // @@ -10,5 +20,7 @@ → +20부터 시작
                    val match = Regex("""\+(\d+)""").find(raw) ?: continue
                    newLine = match.groupValues[1].toInt()
                }
                raw.startsWith("+++") -> { /* 파일명 헤더 무시 */ }
                raw.startsWith("---") -> { /* 파일명 헤더 무시 */ }
                raw.startsWith("+") -> {
                    lines.add(newLine)
                    newLine++
                }
                raw.startsWith("-") -> { /* old 파일 라인은 새 파일 카운트에 영향 없음 */ }
                else -> {
                    // 컨텍스트 라인도 포함 (인라인 코멘트 가능)
                    lines.add(newLine)
                    newLine++
                }
            }
        }
        return lines
    }
}
