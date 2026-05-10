package com.pbot.bot.infrastructure.llm

/**
 * LLM structured output용 JSON Schema.
 * OpenAI(json_schema)와 Anthropic(tool input_schema) 양쪽이 같은 본문을 사용하고,
 * 각 클라이언트가 wrapping만 다르게 적용한다.
 *
 * OpenAI strict mode는 모든 properties가 required에 있어야 하므로 optional 필드는
 * `["type", "null"]` union 타입으로 표현한다.
 */
object ReviewSchema {

    private val FILE_CHANGE_TYPES = listOf(
        "REFACTOR", "NEW", "FIX", "CONFIG", "DEPENDENCY", "TEST", "DOC", "STYLE",
    )

    private val SEVERITIES = listOf("HIGH", "MEDIUM", "LOW")

    val ROOT: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "walkthrough" to walkthroughSchema(),
            "summary" to mapOf("type" to "string"),
            "issues" to issuesSchema(),
        ),
        "required" to listOf("walkthrough", "summary", "issues"),
        "additionalProperties" to false,
    )

    private fun walkthroughSchema() = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "intent" to mapOf("type" to "string"),
            "files" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string"),
                        "type" to mapOf("type" to "string", "enum" to FILE_CHANGE_TYPES),
                        "summary" to mapOf("type" to "string"),
                    ),
                    "required" to listOf("path", "type", "summary"),
                    "additionalProperties" to false,
                ),
            ),
            "risks" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "severity" to mapOf("type" to "string", "enum" to SEVERITIES),
                        "description" to mapOf("type" to "string"),
                        "location" to mapOf("type" to listOf("string", "null")),
                    ),
                    "required" to listOf("severity", "description", "location"),
                    "additionalProperties" to false,
                ),
            ),
        ),
        "required" to listOf("intent", "files", "risks"),
        "additionalProperties" to false,
    )

    private fun issuesSchema() = mapOf(
        "type" to "array",
        "items" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string"),
                "line" to mapOf("type" to "integer"),
                "startLine" to mapOf("type" to listOf("integer", "null")),
                "severity" to mapOf("type" to "string", "enum" to SEVERITIES),
                "comment" to mapOf("type" to "string"),
                "suggestion" to mapOf("type" to listOf("string", "null")),
            ),
            "required" to listOf("path", "line", "startLine", "severity", "comment", "suggestion"),
            "additionalProperties" to false,
        ),
    )
}
