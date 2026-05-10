package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.FileChange
import com.pbot.bot.domain.model.FileChangeType
import com.pbot.bot.domain.model.RiskHighlight
import com.pbot.bot.domain.model.Severity
import com.pbot.bot.domain.model.Walkthrough
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WalkthroughBuilderTest {

    @Test
    fun `header always present`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).contains("## 🐶 Pawranoid Review")
    }

    @Test
    fun `intent appears under What changed section`() {
        val w = Walkthrough(
            intent = "OAuth migration.",
            files = listOf(FileChange("Foo.kt", FileChangeType.REFACTOR, "JWT → OAuth")),
            risks = emptyList(),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).contains("### ① What changed")
        assertThat(md).contains("OAuth migration.")
    }

    @Test
    fun `files table renders filename only, plain-text type, and summary`() {
        val w = Walkthrough(
            intent = "x",
            files = listOf(
                FileChange("src/main/kotlin/com/pbot/bot/domain/Foo.kt", FileChangeType.NEW, "신규 OAuth callback"),
                FileChange("src/main/kotlin/com/pbot/bot/domain/Bar.kt", FileChangeType.REFACTOR, "JWT 제거"),
            ),
            risks = emptyList(),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).contains("| `Foo.kt` | New | 신규 OAuth callback |")
        assertThat(md).contains("| `Bar.kt` | Refactor | JWT 제거 |")
    }

    @Test
    fun `files table shows root-level filename as-is`() {
        val w = Walkthrough(
            intent = "x",
            files = listOf(FileChange("README.md", FileChangeType.DOC, "readme update")),
            risks = emptyList(),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).contains("| `README.md` | Doc | readme update |")
    }

    // --- evaluation blockquote (under Files changed) ---

    @Test
    fun `evaluation block omitted when empty`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough(), evaluation = emptyList())

        assertThat(md).doesNotContain("> ")
    }

    @Test
    fun `evaluation block renders each line with blockquote prefix`() {
        val lines = listOf(
            "**머지 가능** — 충돌 없음.",
            "**사이즈가 큽니다** (12 files) — 분리 권장.",
        )

        val md = WalkthroughBuilder.build(emptyWalkthrough(), evaluation = lines)

        assertThat(md).contains("> **머지 가능** — 충돌 없음.")
        assertThat(md).contains("> **사이즈가 큽니다** (12 files) — 분리 권장.")
    }

    @Test
    fun `evaluation lines separated by empty blockquote line for paragraph break`() {
        // GitHub markdown 에서 연속된 `>` 라인은 한 문단으로 합쳐짐 — 사이에 빈 `>` 가 있어야
        // 라인이 시각적으로 분리됨. 이 테스트는 그 분리자가 들어가는지 검증.
        val lines = listOf("first line", "second line", "third line")

        val md = WalkthroughBuilder.build(emptyWalkthrough(), evaluation = lines)

        // 첫째 ↔ 둘째 사이, 둘째 ↔ 셋째 사이에 `>` 만 있는 라인이 등장.
        val expected = "> first line\n>\n> second line\n>\n> third line"
        assertThat(md).contains(expected)
    }

    @Test
    fun `evaluation block sits between Files changed table and Risk highlights`() {
        val w = Walkthrough(
            intent = "x",
            files = listOf(FileChange("Foo.kt", FileChangeType.NEW, "x")),
            risks = listOf(RiskHighlight(Severity.HIGH, "위험", null)),
        )
        val lines = listOf("**머지 가능** — clean.")

        val md = WalkthroughBuilder.build(w, evaluation = lines)

        val filesIdx = md.indexOf("### ② Files changed")
        val evalIdx = md.indexOf("> **머지 가능**")
        val risksIdx = md.indexOf("### ③ Risk highlights")
        assertThat(filesIdx).isLessThan(evalIdx)
        assertThat(evalIdx).isLessThan(risksIdx)
    }

    // --- risks (renumbered to ③) ---

    @Test
    fun `risks section omitted when empty`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).doesNotContain("Risk highlights")
    }

    @Test
    fun `risks section shown with severity emoji and optional location`() {
        val w = Walkthrough(
            intent = "x",
            files = emptyList(),
            risks = listOf(
                RiskHighlight(Severity.HIGH, "인증 로직 교체", "AuthService.kt:42"),
                RiskHighlight(Severity.MEDIUM, "외부 의존성 추가", null),
            ),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).contains("### ③ Risk highlights")
        assertThat(md).contains("- 🔴 **HIGH** — 인증 로직 교체 (`AuthService.kt:42`)")
        assertThat(md).contains("- 🟡 **MEDIUM** — 외부 의존성 추가")
    }

    // --- removed legacy sections ---

    @Test
    fun `no Reviewed section, no Triggered footer, no Process notes heading, no horizontal rule`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).doesNotContain("Reviewed")
        assertThat(md).doesNotContain("Triggered by")
        assertThat(md).doesNotContain("Process notes")
        assertThat(md).doesNotContain("\n---")
    }

    private fun emptyWalkthrough() = Walkthrough(
        intent = "x",
        files = emptyList(),
        risks = emptyList(),
    )
}
