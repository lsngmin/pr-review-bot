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

    // --- shouldVerify ---

    @Test
    fun `verify triggers on slash verify`() {
        assertThat(CommandDetector.shouldVerify("/verify", botMention)).isTrue()
    }

    @Test
    fun `verify triggers on mention with verify keyword`() {
        assertThat(CommandDetector.shouldVerify("@pawranoid verify", botMention)).isTrue()
    }

    @Test
    fun `verify case-insensitive`() {
        assertThat(CommandDetector.shouldVerify("@Pawranoid Verify", botMention)).isTrue()
        assertThat(CommandDetector.shouldVerify("/VERIFY", botMention)).isTrue()
    }

    @Test
    fun `verify mention with sentence after`() {
        assertThat(CommandDetector.shouldVerify("@pawranoid verify this please", botMention)).isTrue()
    }

    @Test
    fun `verify does not trigger on plain mention without verify keyword`() {
        // 그냥 @pawranoid 만 있으면 verify 아니라 review 트리거가 됨
        assertThat(CommandDetector.shouldVerify("@pawranoid", botMention)).isFalse()
        assertThat(CommandDetector.shouldVerify("@pawranoid review", botMention)).isFalse()
    }

    @Test
    fun `verify does not trigger on similar bot name`() {
        assertThat(CommandDetector.shouldVerify("@pawranoid-staging verify", botMention)).isFalse()
    }

    // --- review/verify 충돌 방지 ---

    @Test
    fun `shouldTrigger returns false when comment is a verify command`() {
        // verify 의도인데 review 트리거되면 안 됨 (verify는 별도 path)
        assertThat(CommandDetector.shouldTrigger("@pawranoid verify", botMention)).isFalse()
        assertThat(CommandDetector.shouldTrigger("/verify", botMention)).isFalse()
    }

    // --- 정규식 단어 경계 검증 ---

    @Test
    fun `shouldTrigger does not match slash review with extra suffix chars`() {
        // /reviewer, /review123 같은 부분 일치는 false positive
        assertThat(CommandDetector.shouldTrigger("/reviewer please", botMention)).isFalse()
        assertThat(CommandDetector.shouldTrigger("/review123", botMention)).isFalse()
        assertThat(CommandDetector.shouldTrigger("/review-now", botMention)).isFalse()
    }

    @Test
    fun `shouldVerify does not match slash verify with extra suffix chars`() {
        assertThat(CommandDetector.shouldVerify("/verifying", botMention)).isFalse()
        assertThat(CommandDetector.shouldVerify("/verify123", botMention)).isFalse()
        assertThat(CommandDetector.shouldVerify("/verify-now", botMention)).isFalse()
    }
}
