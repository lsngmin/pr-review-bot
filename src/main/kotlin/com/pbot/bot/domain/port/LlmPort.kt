package com.pbot.bot.domain.port

import com.pbot.bot.domain.model.ReviewResult

/**
 * LLM 추상화 포트.
 *
 * 어떤 LLM (OpenAI, Anthropic, Gemini, ...) 든 이 인터페이스를 구현하면
 * 도메인 코드 변경 없이 교체 가능.
 */
interface LlmPort {
    /**
     * annotated diff 텍스트를 받아 코드 리뷰 결과를 반환.
     */
    fun review(diff: String): ReviewResult
}
