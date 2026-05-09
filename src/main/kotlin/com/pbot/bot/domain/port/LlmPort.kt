package com.pbot.bot.domain.port

import com.pbot.bot.domain.model.ReviewResult

/**
 * LLM 추상화 포트 (Hexagonal Architecture의 Outbound Port).
 *
 * 도메인 서비스([com.pbot.bot.domain.service.ReviewService])는 이 인터페이스에만 의존하며,
 * 실제 어떤 모델/공급자가 호출되는지는 알지 못한다.
 *
 * 새 LLM(Anthropic Claude, Google Gemini 등)을 추가할 때는 이 인터페이스를 구현하는
 * `@Component`를 `infrastructure/llm/`에 만들면 도메인 코드 변경 없이 교체된다.
 *
 * 구현체 예시:
 * - [com.pbot.bot.infrastructure.llm.GptClient] (OpenAI)
 */
interface LlmPort {
    /**
     * 라인 번호가 주석으로 붙은 diff 텍스트를 받아 코드 리뷰를 수행한다.
     *
     * 입력 형식 예시:
     * ```
     * === FULL FILE: src/main/kotlin/Foo.kt ===
     * <파일 전체 내용>
     *
     * === CHANGES IN src/main/kotlin/Foo.kt ===
     * @@ -10,5 +14,8 @@
     * L14     class Foo(
     * L15 [+]     @Async
     * L--  [-]     val old = ...
     * ```
     *
     * 구현체는 LLM 호출 + 응답 파싱까지 책임지고 [ReviewResult]를 반환한다.
     * 호출 실패 시(네트워크 오류, 응답 형식 불일치 등) 예외를 던져도 무방하다 — 호출 측의
     * `@Async` 처리로 webhook 응답에는 영향이 없다.
     *
     * @param diff 라인 번호 주석이 붙은 diff (필요 시 파일 전체 내용 포함). [com.pbot.bot.domain.service.ReviewService]가 조립.
     * @return summary와 라인별 issues를 담은 리뷰 결과
     */
    fun review(diff: String): ReviewResult
}
