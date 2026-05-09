package com.pbot.bot.controller

import com.fasterxml.jackson.databind.JsonNode
import com.pbot.bot.auth.GitHubAuthService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

@RestController
class WebhookController(
    private val authService: GitHubAuthService,
    @Value("\${openai.api-key}") val openaiKey: String,
) {
    private val rest = RestClient.create()

    @PostMapping("/webhook")
    fun receive(@RequestBody body: JsonNode): String {
        val action = body["action"].asText()
        val repoName = body["repository"]["full_name"].asText()
        val prNumber = body["pull_request"]["number"].asInt()
        if (action != "opened") return "skip"
        val diff = fetchDiff(repoName, prNumber)
        val review = askGpt(diff)
        postReview(repoName, prNumber, review)
        println(review)
        return "ok"
    }

    private fun fetchDiff(repo: String, number: Int): String {
        return rest.get()
            .uri("https://api.github.com/repos/$repo/pulls/$number")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${authService.getInstallationToken()}")
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff")
            .retrieve()
            .body(String::class.java)?: ""
    }
    private fun askGpt(diff: String): String {
        val systemPrompt = """
            You are a senior code reviewer.
            Review the following git diff and provide concise feedback in Korean.
            Focus on bugs, security issues, and clear improvements.
            Keep it short (under 300 words).
        """.trimIndent()

        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to diff),
            )
        )
        return rest.post()
            .uri("https://api.openai.com/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $openaiKey")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(requestBody)
            .retrieve()
            .body(JsonNode::class.java)!!["choices"][0]["message"]["content"].asText()
    }
    private fun postReview(repo: String, number: Int, comment: String) {
        val requestBody = mapOf("body" to comment, "event" to "COMMENT")
        rest.post()
            .uri("https://api.github.com/repos/$repo/pulls/$number/reviews")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${authService.getInstallationToken()}")
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
            .body(requestBody)
            .retrieve()
            .toBodilessEntity()
    }
}