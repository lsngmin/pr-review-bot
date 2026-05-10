package com.pawranoid.infrastructure.llm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pawranoid.domain.model.ReviewResult
import com.pawranoid.domain.port.LlmPort
import com.pawranoid.domain.port.ReviewPrompt
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@Primary
class GptClient(
    @Value("\${openai.api-key}") private val openaiKey: String,
) : LlmPort {
    private val rest = RestClient.create()
    private val mapper = jacksonObjectMapper()

    override fun review(diff: String): ReviewResult {
        val schema = mapOf(
            "name" to "code_review",
            "strict" to true,
            "schema" to ReviewSchema.ROOT,
        )

        val requestBody = mapOf(
            "model" to "gpt-5.4",
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
