package com.pbot.bot.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pbot.bot.service.ReviewService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
class WebhookController(
    private val reviewService: ReviewService,
    @Value("\${github.webhook-secret}") private val webhookSecret: String,
) {
    private val objectMapper = jacksonObjectMapper()

    @PostMapping("/webhook")
    fun receive(
        @RequestHeader("X-Hub-Signature-256") signature: String?,
        @RequestBody body: String,
    ): ResponseEntity<String> {
        if (!verifySignature(body, signature)) {
            return ResponseEntity.status(401).body("invalid signature")
        }

        val json = objectMapper.readTree(body)
        val action = json["action"].asText()
        if (action != "opened") return ResponseEntity.ok("skip")

        val repoName = json["repository"]["full_name"].asText()
        val prNumber = json["pull_request"]["number"].asInt()

        reviewService.reviewPullRequest(repoName, prNumber)
        return ResponseEntity.ok("ok")
    }

    private fun verifySignature(body: String, signatureHeader: String?): Boolean {
        if (signatureHeader == null) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(webhookSecret.toByteArray(), "HmacSHA256"))
        val computed = "sha256=" + mac.doFinal(body.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(
            signatureHeader.toByteArray(),
            computed.toByteArray()
        )
    }
}
