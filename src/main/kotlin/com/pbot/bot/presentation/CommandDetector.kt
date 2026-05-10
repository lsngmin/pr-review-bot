package com.pbot.bot.presentation

/**
 * PR 코멘트가 봇 트리거(리뷰 또는 검증)를 발생시키는지 판정한다.
 *
 * 지원 패턴 (멘션 only — 슬래시 커맨드는 폐기됨):
 * - **리뷰**: `@<botMention>` (선택적으로 `review` 동사 포함)
 * - **검증**: `@<botMention> verify` — 이미 단 코멘트의 정당성 cross-verify
 *
 * GitHub은 커스텀 App을 reviewer 드롭다운에 노출하지 않아 다른 AI 리뷰 봇
 * (CodeRabbit, Greptile 등)도 멘션 패턴을 표준으로 채택한다.
 */
object CommandDetector {

    fun shouldTrigger(commentBody: String, botMention: String): Boolean {
        val trimmed = commentBody.trim()
        // verify는 별도 path (pull_request_review_comment) 에서 처리되므로 review 트리거에서 제외.
        // 사용자가 일반 PR 코멘트에 '@bot verify' 라고 적었을 때 풀 리뷰가 의도치 않게 도는 걸 방지.
        if (shouldVerify(trimmed, botMention)) return false
        return mentionPattern(botMention).containsMatchIn(trimmed)
    }

    /**
     * 봇 인라인 코멘트의 답글이 cross-verify 요청인지 판정.
     * `@<botMention> verify` 패턴.
     */
    fun shouldVerify(commentBody: String, botMention: String): Boolean {
        val trimmed = commentBody.trim()
        val verifyMention = Regex(
            "(^|\\s)@${Regex.escape(botMention)}(?![\\w-])\\s+verify\\b",
            RegexOption.IGNORE_CASE,
        )
        return verifyMention.containsMatchIn(trimmed)
    }

    // 앞에는 시작 또는 공백 (이메일 'user@bot' 차단), 뒤에는 영숫자/언더스코어/하이픈이 없어야 함
    // (부분 일치 '@pawranoid-staging' 차단). \b는 하이픈을 단어 경계로 보므로 부족하다.
    private fun mentionPattern(botMention: String): Regex = Regex(
        "(^|\\s)@${Regex.escape(botMention)}(?![\\w-])",
        RegexOption.IGNORE_CASE,
    )
}
