package com.pbot.bot.llm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GptClient(
    @Value("\${openai.api-key}") private val openaiKey: String,
) {
    private val rest = RestClient.create()
    private val mapper = jacksonObjectMapper()

    fun review(diff: String): ReviewResult {
        val systemPrompt = """
            You are a senior code reviewer.
            Review the following git diff and provide concise feedback in Korean.
            Focus on bugs, security issues, and clear improvements.

            Return:
            - summary: 1~3 sentences overall feedback in Korean.
            - issues: list of specific concerns. Each must reference an actual changed file (path) and an actual changed line (line number on the new file). Skip if uncertain.
        """.trimIndent()

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
                mapOf("role" to "system", "content" to systemPrompt),
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
