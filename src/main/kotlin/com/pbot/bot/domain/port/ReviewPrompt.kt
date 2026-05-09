package com.pbot.bot.domain.port

/**
 * 모든 LLM 구현체가 공유하는 코드 리뷰 시스템 프롬프트.
 *
 * 실제 텍스트는 `src/main/resources/prompts/` 하위 파일에서 로드한다.
 * Kotlin 문자열로 인라인하지 않는 이유:
 * - 비프로그래머도 PR로 프롬프트만 수정할 수 있음
 * - 문자열 escape (`\$`) 신경 안 써도 됨
 * - 향후 variant(예: 보안 특화) 추가 시 파일만 늘리면 됨
 *
 * Class#getResource는 JVM 표준 API라 도메인이 Spring 의존성을 끌어들이지 않는다.
 */
object ReviewPrompt {
    val SYSTEM: String = load("/prompts/code-review-system.txt")

    private fun load(path: String): String {
        return ReviewPrompt::class.java.getResource(path)?.readText()
            ?: error("Prompt resource not found: $path")
    }
}
