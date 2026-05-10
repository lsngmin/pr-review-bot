package com.pbot.bot.presentation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pbot.bot.domain.service.ReviewService
import com.pbot.bot.domain.service.VerifyService
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
    private val verifyService: VerifyService,
    @Value("\${github.webhook-secret}") private val webhookSecret: String,
    @Value("\${github.bot.mention}") private val botMention: String,
) {
    private val log = LoggerFactory.getLogger(WebhookController::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val botLogin = "$botMention[bot]"

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
            "pull_request_review_comment" -> handleReviewCommentReply(json)
            else -> ResponseEntity.ok("ignored:$event")
        }
    }

    private fun handleIssueComment(json: JsonNode): ResponseEntity<String> {
        if (json["action"].asText() != "created") return ResponseEntity.ok("skip:not-created")
        if (json["issue"]["pull_request"] == null) return ResponseEntity.ok("skip:not-pr")
        val body = json["comment"]["body"].asText()
        if (!CommandDetector.shouldTrigger(body, botMention)) {
            return ResponseEntity.ok("skip:not-command")
        }

        val repoName = json["repository"]["full_name"].asText()
        val prNumber = json["issue"]["number"].asInt()
        reviewService.reviewPullRequest(repoName, prNumber)
        return ResponseEntity.ok("ok")
    }

    /**
     * 인라인 review 코멘트 답글로 들어오는 cross-verify 요청 처리.
     */
    private fun handleReviewCommentReply(json: JsonNode): ResponseEntity<String> {
        if (json["action"].asText() != "created") return ResponseEntity.ok("skip:not-created")
        val comment = json["comment"]

        // 봇 자신의 댓글이면 무한 루프 방지
        if (comment["user"]["login"].asText() == botLogin) return ResponseEntity.ok("skip:self")

        // 답글이어야 함 (in_reply_to_id 존재)
        val parentId = comment["in_reply_to_id"]?.takeIf { !it.isNull }?.asLong()
            ?: return ResponseEntity.ok("skip:not-reply")

        val body = comment["body"].asText()
        if (!CommandDetector.shouldVerify(body, botMention)) {
            return ResponseEntity.ok("skip:not-verify-command")
        }

        val repoName = json["repository"]["full_name"].asText()
        val prNumber = json["pull_request"]["number"].asInt()
        verifyService.verifyReviewComment(repoName, prNumber, parentId)
        return ResponseEntity.ok("ok")
    }
}
