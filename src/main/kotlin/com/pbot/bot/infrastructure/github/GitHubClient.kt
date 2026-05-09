package com.pbot.bot.infrastructure.github

import com.fasterxml.jackson.databind.JsonNode
import com.pbot.bot.domain.model.ReviewComment
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PullRequestFile(
    val path: String,
    val patch: String?,
)

@Component
class GitHubClient(private val authService: GitHubAuthService) {
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
     */
    fun fetchFiles(repo: String, number: Int): List<PullRequestFile> {
        val all = mutableListOf<PullRequestFile>()
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
            if (response.size() < PER_PAGE) break // 마지막 페이지
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
        val apiComments = comments.map {
            mapOf(
                "path" to it.path,
                "line" to it.line,
                "side" to "RIGHT",
                "body" to it.body,
            )
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

    private fun bearer() = "Bearer ${authService.getInstallationToken()}"
}
