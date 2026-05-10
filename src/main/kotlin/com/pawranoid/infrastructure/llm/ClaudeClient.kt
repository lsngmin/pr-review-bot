package com.pawranoid.infrastructure.llm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pawranoid.domain.model.ReviewResult
import com.pawranoid.domain.port.LlmPort
import com.pawranoid.domain.port.ReviewPrompt
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Anthropic Claude API 구현체.
 *
 * Anthropic은 OpenAI의 `response_format: json_schema` 같은 직접적인 구조화 출력을
 * 지원하지 않으므로, **tool use** 메커니즘으로 우회한다 — 가짜 도구를 정의하고
 * 모델이 그 도구를 호출하면서 input 파라미터로 구조화된 결과를 채워 넣게 한다.
 */
@Component
class ClaudeClient(
    @Value("\${anthropic.api-key}") private val apiKey: String,
) : LlmPort {
    private val rest = RestClient.create()
    private val mapper: ObjectMapper = jacksonObjectMapper()

    override fun review(diff: String): ReviewResult {
        val tool = mapOf(
            "name" to "submit_code_review",
            "description" to "Submit a structured code review with overview, summary and inline issues.",
            "input_schema" to ReviewSchema.ROOT,
        )

        val requestBody = mapOf(
            "model" to "claude-sonnet-4-6",
            "max_tokens" to 4096,
            "system" to ReviewPrompt.SYSTEM,
            "messages" to listOf(
                mapOf("role" to "user", "content" to diff),
            ),
            "tools" to listOf(tool),
            // 강제로 우리 도구만 호출 — 일반 텍스트 응답 안 받게.
            "tool_choice" to mapOf("type" to "tool", "name" to "submit_code_review"),
        )

        val response = rest.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(requestBody)
            .retrieve()
            .body(JsonNode::class.java)!!

        // 응답 형식: {"content": [{"type":"tool_use", "name":"submit_code_review", "input": {...}}]}
        val toolUse = response["content"]
            .firstOrNull { it["type"].asText() == "tool_use" }
            ?: error("Claude response missing tool_use block: $response")
        return mapper.treeToValue(toolUse["input"], ReviewResult::class.java)
    }
}
