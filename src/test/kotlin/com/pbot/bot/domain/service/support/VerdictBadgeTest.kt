package com.pbot.bot.domain.service.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerdictBadgeTest {

    @Test
    fun `detects AGREE`() {
        assertThat(VerdictBadge.detect("AGREE: IOException 가능.")).isEqualTo(VerdictBadge.Verdict.AGREE)
    }

    @Test
    fun `detects DISAGREE before AGREE substring match`() {
        // DISAGREE 안에 AGREE가 들어있어 순서가 잘못되면 false positive 가능 — 순서 검증
        assertThat(VerdictBadge.detect("DISAGREE: foo()는 throws 없음.")).isEqualTo(VerdictBadge.Verdict.DISAGREE)
    }

    @Test
    fun `detects PARTIAL`() {
        assertThat(VerdictBadge.detect("PARTIAL: 라인 어긋남.")).isEqualTo(VerdictBadge.Verdict.PARTIAL)
    }

    @Test
    fun `case insensitive`() {
        assertThat(VerdictBadge.detect("agree: 좋음")).isEqualTo(VerdictBadge.Verdict.AGREE)
        assertThat(VerdictBadge.detect("Disagree.")).isEqualTo(VerdictBadge.Verdict.DISAGREE)
    }

    @Test
    fun `falls back to UNCLEAR when no keyword found`() {
        assertThat(VerdictBadge.detect("음... 잘 모르겠습니다.")).isEqualTo(VerdictBadge.Verdict.UNCLEAR)
    }

    @Test
    fun `keyword in body does not affect detection`() {
        // SCAN_LEN 30자 밖의 키워드는 무시 — 본문에 'agree' 단어 들어와도 헤더에 영향 없음
        val long = "음 잘 모르겠지만 일단 살펴보자면 결국 I would agree with the original on most points."
        assertThat(VerdictBadge.detect(long)).isEqualTo(VerdictBadge.Verdict.UNCLEAR)
    }

    @Test
    fun `render prepends header and preserves body`() {
        val rendered = VerdictBadge.render("AGREE: 실제 이슈 맞음.")

        assertThat(rendered).startsWith("### ✅ AGREE\n\n")
        assertThat(rendered).endsWith("AGREE: 실제 이슈 맞음.")
    }

    @Test
    fun `render uses UNCLEAR badge for unrecognized summary`() {
        val rendered = VerdictBadge.render("그냥 잘 모름")

        assertThat(rendered).startsWith("### 🤔 UNCLEAR\n\n")
    }
}
