package com.pbot.bot.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommandDetectorTest {

    private val botMention = "pawranoid"

    // --- 슬래시 커맨드 ---

    @Test
    fun `triggers on bare slash command`() {
        assertThat(CommandDetector.shouldTrigger("/review", botMention)).isTrue()
    }

    @Test
    fun `triggers on slash command with leading whitespace`() {
        assertThat(CommandDetector.shouldTrigger("   /review", botMention)).isTrue()
    }

    @Test
    fun `triggers on slash command with arguments`() {
        assertThat(CommandDetector.shouldTrigger("/review --security", botMention)).isTrue()
    }

    @Test
    fun `does not trigger on slash command in middle of comment`() {
        // 슬래시 커맨드는 코멘트 시작에만 — 코드 인용 등에서 false positive 방지
        assertThat(CommandDetector.shouldTrigger("see also /review later", botMention)).isFalse()
    }

    // --- @멘션 ---

    @Test
    fun `triggers on bare mention`() {
        assertThat(CommandDetector.shouldTrigger("@pawranoid", botMention)).isTrue()
    }

    @Test
    fun `triggers on mention with review keyword`() {
        assertThat(CommandDetector.shouldTrigger("@pawranoid review please", botMention)).isTrue()
    }

    @Test
    fun `triggers on mention in middle of sentence`() {
        assertThat(CommandDetector.shouldTrigger("Hey @pawranoid can you review", botMention)).isTrue()
    }

    @Test
    fun `case-insensitive mention`() {
        assertThat(CommandDetector.shouldTrigger("@Pawranoid review", botMention)).isTrue()
        assertThat(CommandDetector.shouldTrigger("@PAWRANOID", botMention)).isTrue()
    }

    // --- false positive 방지 ---

    @Test
    fun `does not trigger on similar bot name (substring)`() {
        assertThat(CommandDetector.shouldTrigger("@pawranoid-staging review", botMention)).isFalse()
        assertThat(CommandDetector.shouldTrigger("@pawranoidx", botMention)).isFalse()
    }

    @Test
    fun `does not trigger on email-like patterns`() {
        assertThat(CommandDetector.shouldTrigger("contact me at email@pawranoid.com", botMention)).isFalse()
        assertThat(CommandDetector.shouldTrigger("user@pawranoid", botMention)).isFalse()
    }

    @Test
    fun `does not trigger on plain text without mention or slash`() {
        assertThat(CommandDetector.shouldTrigger("good change", botMention)).isFalse()
        assertThat(CommandDetector.shouldTrigger("review this please", botMention)).isFalse()
    }

    @Test
    fun `does not trigger on empty comment`() {
        assertThat(CommandDetector.shouldTrigger("", botMention)).isFalse()
    }
}
