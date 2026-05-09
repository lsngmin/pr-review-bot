package com.pbot.bot.service

import com.pbot.bot.github.GitHubClient
import com.pbot.bot.github.PullRequestFile
import com.pbot.bot.llm.GptClient
import com.pbot.bot.llm.ReviewIssue
import org.springframework.stereotype.Service

@Service
class ReviewService(
    private val gitHubClient: GitHubClient,
    private val gptClient: GptClient,
) {
    fun reviewPullRequest(repo: String, number: Int) {
        val diff = gitHubClient.fetchDiff(repo, number)
        val files = gitHubClient.fetchFiles(repo, number)
        val result = gptClient.review(diff)

        val validIssues = result.issues.filter { isLineValid(it, files) }
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

    private fun isLineValid(issue: ReviewIssue, files: List<PullRequestFile>): Boolean {
        val file = files.find { it.path == issue.path } ?: return false
        val patch = file.patch ?: return false
        return addedLineNumbers(patch).contains(issue.line)
    }

    /**
     * patch 텍스트를 파싱해서 새 파일에 추가된 라인 번호 집합을 만든다.
     * GitHub PR Review API는 인라인 코멘트 위치가 diff 안의 추가된 라인이어야 통과시킨다.
     */
    private fun addedLineNumbers(patch: String): Set<Int> {
        val added = mutableSetOf<Int>()
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
                    added.add(newLine)
                    newLine++
                }
                raw.startsWith("-") -> { /* old 파일 라인은 새 파일 카운트에 영향 없음 */ }
                else -> {
                    newLine++
                }
            }
        }
        return added
    }
}
