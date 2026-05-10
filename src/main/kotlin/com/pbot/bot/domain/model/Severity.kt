package com.pbot.bot.domain.model

/**
 * 이슈/위험의 심각도. 인라인 코멘트와 walkthrough risk highlights에서 공통 사용.
 */
enum class Severity {
    HIGH,
    MEDIUM,
    LOW;

    val emoji: String
        get() = when (this) {
            HIGH -> "🔴"
            MEDIUM -> "🟡"
            LOW -> "🟢"
        }
}
