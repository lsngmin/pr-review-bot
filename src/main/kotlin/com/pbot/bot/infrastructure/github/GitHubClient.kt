package com.pbot.bot.infrastructure.github

import com.fasterxml.jackson.databind.JsonNode
import com.pbot.bot.domain.model.ReviewComment
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PullRequestFile(
    val path: String,
    val patch: String?,
)

@Component
class GitHubClient(private val authService: GitHubAuthService) {
    private val log = LoggerFactory.getLogger(GitHubClient::class.java)
    private val rest = RestClient.create()

    fun fetchDiff(repo: String, number: Int): String {
        return rest.get()
            .uri("https://api.github.com/repos/$repo/pulls/$number")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff")
            .retrieve()
            .body(String::class.java) ?: ""
    }

    fun fetchPullRequestHeadSha(repo: String, number: Int): String {
        val response = rest.get()
            .uri("https://api.github.com/repos/$repo/pulls/$number")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
            .retrieve()
            .body(JsonNode::class.java)!!
        return response["head"]["sha"].asText()
    }

    fun fetchFileContent(repo: String, path: String, ref: String): String {
        return rest.get()
            .uri("https://api.github.com/repos/$repo/contents/$path?ref=$ref")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.raw")
            .retrieve()
            .body(String::class.java) ?: ""
    }

    /**
     * PR의 변경 파일 목록을 페이징으로 전부 받아온다.
     * GitHub API는 한 페이지 최대 100개. 100개 넘는 PR도 정확히 처리.
     * 안전장치로 페이지 [MAX_FILE_PAGES] 까지만 받음 (오버사이즈 PR 방어).
     * cap 도달해도 더 가져올 페이지가 남아있으면 경고 로깅 (silent truncation 방지).
     */
    fun fetchFiles(repo: String, number: Int): List<PullRequestFile> {
        val all = mutableListOf<PullRequestFile>()
        var lastPageSize = 0
        var pagesFetched = 0
        for (page in 1..MAX_FILE_PAGES) {
            val response = rest.get()
                .uri("https://api.github.com/repos/$repo/pulls/$number/files?per_page=$PER_PAGE&page=$page")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .retrieve()
                .body(JsonNode::class.java) ?: break
            if (!response.isArray || response.isEmpty) break
            response.forEach {
                all += PullRequestFile(
                    path = it["filename"].asText(),
                    patch = it["patch"]?.asText(),
                )
            }
            lastPageSize = response.size()
            pagesFetched = page
            if (response.size() < PER_PAGE) break // 마지막 페이지
        }
        // cap 도달했고 마지막 페이지가 가득 = 더 있을 가능성 있음
        if (pagesFetched == MAX_FILE_PAGES && lastPageSize == PER_PAGE) {
            log.warn("PR {}#{} may have more than {} files; remaining pages are truncated", repo, number, MAX_FILE_PAGES * PER_PAGE)
        }
        return all
    }

    private companion object {
        const val PER_PAGE = 100
        const val MAX_FILE_PAGES = 10 // 최대 1000 파일까지 (방어선)
    }

    fun postReview(
        repo: String,
        number: Int,
        body: String,
        comments: List<ReviewComment> = emptyList(),
    ) {
        val apiComments = comments.map { c ->
            // GitHub PR review API는 다중 라인일 때 start_line + start_side 필요.
            // 단일 라인은 line + side만 (start_line 보내면 거절됨).
            val base = mapOf(
                "path" to c.path,
                "line" to c.line,
                "side" to "RIGHT",
                "body" to c.body,
            )
            if (c.startLine != null) {
                base + mapOf(
                    "start_line" to c.startLine,
                    "start_side" to "RIGHT",
                )
            } else {
                base
            }
        }
        val requestBody = mapOf(
            "body" to body,
            "event" to "COMMENT",
            "comments" to apiComments,
        )
        rest.post()
            .uri("https://api.github.com/repos/$repo/pulls/$number/reviews")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
            .body(requestBody)
            .retrieve()
            .toBodilessEntity()
    }

    /**
     * 단일 review comment(인라인 코멘트) 한 건의 본문/메타를 가져온다.
     * 경로: GET /repos/{repo}/pulls/comments/{id}  (PR 번호 불필요)
     */
    fun fetchReviewComment(repo: String, commentId: Long): JsonNode {
        return rest.get()
            .uri("https://api.github.com/repos/$repo/pulls/comments/$commentId")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
            .retrieve()
            .body(JsonNode::class.java)!!
    }

    /**
     * review comment 스레드에 답글을 등록한다.
     * 경로: POST /repos/{repo}/pulls/{n}/comments/{id}/replies
     */
    fun replyToReviewComment(repo: String, number: Int, parentCommentId: Long, body: String) {
        rest.post()
            .uri("https://api.github.com/repos/$repo/pulls/$number/comments/$parentCommentId/replies")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
            .body(mapOf("body" to body))
            .retrieve()
            .toBodilessEntity()
    }

    private fun bearer() = "Bearer ${authService.getInstallationToken()}"
}
