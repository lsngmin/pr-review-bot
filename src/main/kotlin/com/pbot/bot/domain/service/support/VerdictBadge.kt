package com.pbot.bot.domain.service.support

/**
 * verifier LLM이 회신한 summary 앞에 한눈에 결과를 알 수 있는 헤더 배지를 붙인다.
 *
 * verifier 프롬프트는 summary를 `AGREE: ...`, `DISAGREE: ...`, `PARTIAL: ...` 으로 시작하라고 지시.
 * 그 키워드를 보고 ✅/❌/⚠️ 헤더를 prepend. 어느 것도 매칭 안 되면 🤔 UNCLEAR.
 *
 * 부분 일치 함정: "DISAGREE"는 "AGREE"를 포함하므로 검사 순서가 중요.
 */
object VerdictBadge {

    fun render(summary: String): String {
        val verdict = detect(summary)
        return "### ${verdict.badge} ${verdict.label}\n\n$summary"
    }

    fun detect(summary: String): Verdict {
        val head = summary.uppercase().take(SCAN_LEN)
        return when {
            "DISAGREE" in head -> Verdict.DISAGREE
            "PARTIAL" in head -> Verdict.PARTIAL
            "AGREE" in head -> Verdict.AGREE
            else -> Verdict.UNCLEAR
        }
    }

    enum class Verdict(val badge: String, val label: String) {
        AGREE("✅", "AGREE"),
        DISAGREE("❌", "DISAGREE"),
        PARTIAL("⚠️", "PARTIAL"),
        UNCLEAR("🤔", "UNCLEAR"),
    }

    // summary 첫 부분만 본다. 본문 안의 단어가 헤더에 영향 주는 걸 방지.
    private const val SCAN_LEN = 30
}
