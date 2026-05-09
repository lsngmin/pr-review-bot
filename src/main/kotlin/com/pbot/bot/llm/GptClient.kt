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
            Review the following annotated diff and provide concise feedback in Korean.
            Focus on bugs, security issues, and clear improvements.

            The diff is annotated with explicit line numbers in the format:
              L42 [+] <added line>          → line 42 in new file, added
              L42     <context line>        → line 42 in new file, unchanged context
              L--  [-] <removed line>       → removed line (no new file line number)

            Return:
            - summary: 1~3 sentences overall feedback in Korean.
            - issues: list of specific concerns. For each issue:
              * path: exact filename from the annotated diff
              * line: the EXACT L-prefix line number where the issue actually occurs
              * comment: the feedback in Korean

            CRITICAL line precision rules:
            - The line number MUST be the line where the actual problem exists.
              If you say "exception handling missing here", `line` must point at the
              line that needs the handling, NOT a nearby declaration line.
            - Do NOT point at constructor parameters, imports, or class declarations
              unless the issue is literally about that line.
            - If you are not certain of the exact line, OMIT that issue entirely.
            - Never reference removed lines (L-- ones) — they have no valid line number.

            Be conservative: 2 precise issues are better than 5 vague ones.
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
