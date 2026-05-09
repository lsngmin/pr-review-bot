package com.pbot.bot.presentation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pbot.bot.domain.service.ReviewService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class WebhookController(
    private val reviewService: ReviewService,
    @Value("\${github.webhook-secret}") private val webhookSecret: String,
) {
    private val log = LoggerFactory.getLogger(WebhookController::class.java)
    private val objectMapper = jacksonObjectMapper()

    @PostMapping("/webhook")
    fun receive(
        @RequestHeader("X-Hub-Signature-256") signature: String?,
        @RequestHeader("X-GitHub-Event") event: String?,
        @RequestBody body: String,
    ): ResponseEntity<String> {
        if (!WebhookSignatureVerifier.verify(body, signature, webhookSecret)) {
            log.warn("Rejected webhook with invalid signature: event={}", event)
            return ResponseEntity.status(401).body("invalid signature")
        }

        val json = objectMapper.readTree(body)
        log.debug("Webhook received: event={}", event)
        return when (event) {
            "issue_comment" -> handleIssueComment(json)
            else -> ResponseEntity.ok("ignored:$event")
        }
    }

    private fun handleIssueComment(json: JsonNode): ResponseEntity<String> {
        if (json["action"].asText() != "created") return ResponseEntity.ok("skip:not-created")
        if (json["issue"]["pull_request"] == null) return ResponseEntity.ok("skip:not-pr")
        if (!json["comment"]["body"].asText().trim().startsWith("/review")) {
            return ResponseEntity.ok("skip:not-command")
        }

        val repoName = json["repository"]["full_name"].asText()
        val prNumber = json["issue"]["number"].asInt()
        reviewService.reviewPullRequest(repoName, prNumber)
        return ResponseEntity.ok("ok")
    }

}
