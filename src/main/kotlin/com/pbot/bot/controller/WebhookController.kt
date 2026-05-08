package com.pbot.bot.controller

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class WebhookController {

    @PostMapping("/webhook")
    fun receive(@RequestBody body: JsonNode): String {
        val action = body["action"].asText()
        val repoName = body["repository"]["full_name"].asText()
        val prNumber = body["pull_request"]["number"].asInt()
        if (action != "opened") return "skip"
        println("$action,$repoName,$prNumber")
        return "ok"
    }
}