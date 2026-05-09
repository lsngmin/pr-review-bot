package com.pbot.bot.controller

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

@RestController
class WebhookController(
    @Value("\${github.token}") val token: String
) {
    private val rest = RestClient.create()

    @PostMapping("/webhook")
    fun receive(@RequestBody body: JsonNode): String {
        val action = body["action"].asText()
        val repoName = body["repository"]["full_name"].asText()
        val prNumber = body["pull_request"]["number"].asInt()
        if (action != "opened") return "skip"

        println(fetchDiff(repoName, prNumber))
        return "ok"
    }

    private fun fetchDiff(repo: String, number: Int): String {
        return rest.get()
            .uri("https://api.github.com/repos/$repo/pulls/$number")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff")
            .retrieve()
            .body(String::class.java)?: ""
    }
}