package com.pbot.bot.infrastructure.llm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pbot.bot.domain.model.ReviewResult
import com.pbot.bot.domain.port.LlmPort
import com.pbot.bot.domain.port.ReviewPrompt
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GptClient(
    @Value("\${openai.api-key}") private val openaiKey: String,
) : LlmPort {
    private val rest = RestClient.create()
    private val mapper = jacksonObjectMapper()

    override fun review(diff: String): ReviewResult {
        val schema = mapOf(
            "name" to "code_review",
            "strict" to true,
            "schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "summary" to mapOf("type" to "string"),
                    "issues" to mapOf(
                        "type" to "array",
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "path" to mapOf("type" to "string"),
                                "line" to mapOf("type" to "integer"),
                                "comment" to mapOf("type" to "string"),
                            ),
                            "required" to listOf("path", "line", "comment"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
                "required" to listOf("summary", "issues"),
                "additionalProperties" to false,
            ),
        )

        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to ReviewPrompt.SYSTEM),
                mapOf("role" to "user", "content" to diff),
            ),
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to schema,
            ),
        )

        val content = rest.post()
            .uri("https://api.openai.com/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $openaiKey")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(requestBody)
            .retrieve()
            .body(JsonNode::class.java)!!["choices"][0]["message"]["content"].asText()

        return mapper.readValue(content)
    }
}
