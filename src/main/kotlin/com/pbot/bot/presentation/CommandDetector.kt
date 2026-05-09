package com.pbot.bot.presentation

/**
 * PR 코멘트가 봇 리뷰를 트리거하는지 판정한다.
 *
 * 두 가지 패턴 지원:
 * 1. **슬래시 커맨드**: 코멘트가 `/review` 로 시작
 * 2. **@멘션**: 코멘트 어디든 `@<botMention>` 포함 (단어 경계로 정확 매칭)
 *
 * GitHub은 커스텀 App을 reviewer 드롭다운에 노출하지 않아 다른 AI 리뷰 봇
 * (CodeRabbit, Greptile 등)도 멘션 패턴을 표준으로 채택한다.
 */
object CommandDetector {

    fun shouldTrigger(commentBody: String, botMention: String): Boolean {
        val trimmed = commentBody.trim()
        if (trimmed.startsWith("/review")) return true

        // 앞에는 시작 또는 공백 (이메일 'user@bot' 차단), 뒤에는 영숫자/언더스코어/하이픈이 없어야 함
        // (부분 일치 '@pawranoid-staging' 차단). \b는 하이픈을 단어 경계로 보므로 부족하다.
        val mentionPattern = Regex(
            "(^|\\s)@${Regex.escape(botMention)}(?![\\w-])",
            RegexOption.IGNORE_CASE,
        )
        return mentionPattern.containsMatchIn(trimmed)
    }
}
