package com.pbot.bot.infrastructure.github

import com.fasterxml.jackson.databind.JsonNode
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

    fun fetchFiles(repo: String, number: Int): List<PullRequestFile> {
        val response = rest.get()
            .uri("https://api.github.com/repos/$repo/pulls/$number/files?per_page=100")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
            .retrieve()
            .body(JsonNode::class.java) ?: return emptyList()
        return response.map {
            PullRequestFile(
                path = it["filename"].asText(),
                patch = it["patch"]?.asText(),
            )
        }
    }

    fun postReview(
        repo: String,
        number: Int,
        body: String,
        comments: List<Map<String, Any>> = emptyList(),
    ) {
        val requestBody = mapOf(
            "body" to body,
            "event" to "COMMENT",
            "comments" to comments,
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
