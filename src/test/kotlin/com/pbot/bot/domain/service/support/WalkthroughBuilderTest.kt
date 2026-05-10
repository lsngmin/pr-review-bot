package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.FileChange
import com.pbot.bot.domain.model.FileChangeType
import com.pbot.bot.domain.model.ProcessNote
import com.pbot.bot.domain.model.RiskHighlight
import com.pbot.bot.domain.model.Severity
import com.pbot.bot.domain.model.Walkthrough
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WalkthroughBuilderTest {

    @Test
    fun `header always present`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).contains("## 🐶 Pawranoid Walkthrough")
    }

    @Test
    fun `intent appears under What changed section`() {
        val w = Walkthrough(
            intent = "OAuth migration.",
            files = listOf(FileChange("Foo.kt", FileChangeType.REFACTOR, "JWT → OAuth")),
            risks = emptyList(),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).contains("### 📝 What changed")
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

        // 디렉토리는 떨어지고 파일명만, type은 이모지 없는 평문.
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

    // --- process notes ---

    @Test
    fun `process notes section omitted when empty`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough(), processNotes = emptyList())

        assertThat(md).doesNotContain("Process notes")
    }

    @Test
    fun `process notes section renders severity label and message in plain text`() {
        val notes = listOf(
            ProcessNote(Severity.MEDIUM, "PR 사이즈가 큼"),
            ProcessNote(Severity.LOW, "테스트 동반"),
        )

        val md = WalkthroughBuilder.build(emptyWalkthrough(), processNotes = notes)

        assertThat(md).contains("### 📋 Process notes")
        assertThat(md).contains("- **MEDIUM** — PR 사이즈가 큼")
        assertThat(md).contains("- **LOW** — 테스트 동반")
    }

    @Test
    fun `process notes ordered HIGH then MEDIUM then LOW`() {
        val notes = listOf(
            ProcessNote(Severity.LOW, "low one"),
            ProcessNote(Severity.HIGH, "high one"),
            ProcessNote(Severity.MEDIUM, "med one"),
        )

        val md = WalkthroughBuilder.build(emptyWalkthrough(), processNotes = notes)

        val highIdx = md.indexOf("high one")
        val medIdx = md.indexOf("med one")
        val lowIdx = md.indexOf("low one")
        assertThat(highIdx).isLessThan(medIdx)
        assertThat(medIdx).isLessThan(lowIdx)
    }

    @Test
    fun `process notes section appears between Files changed and Risk highlights`() {
        val w = Walkthrough(
            intent = "x",
            files = listOf(FileChange("Foo.kt", FileChangeType.NEW, "x")),
            risks = listOf(RiskHighlight(Severity.HIGH, "위험", null)),
        )
        val notes = listOf(ProcessNote(Severity.MEDIUM, "process note"))

        val md = WalkthroughBuilder.build(w, processNotes = notes)

        val filesIdx = md.indexOf("### 📂 Files changed")
        val processIdx = md.indexOf("### 📋 Process notes")
        val risksIdx = md.indexOf("### ⚠️ Risk highlights")
        assertThat(filesIdx).isLessThan(processIdx)
        assertThat(processIdx).isLessThan(risksIdx)
    }

    // --- risks ---

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

        assertThat(md).contains("### ⚠️ Risk highlights")
        assertThat(md).contains("- 🔴 **HIGH** — 인증 로직 교체 (`AuthService.kt:42`)")
        assertThat(md).contains("- 🟡 **MEDIUM** — 외부 의존성 추가")
    }

    // --- removed legacy sections ---

    @Test
    fun `no Reviewed section, no Triggered footer, no horizontal rule`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).doesNotContain("Reviewed")
        assertThat(md).doesNotContain("Triggered by")
        assertThat(md).doesNotContain("\n---")
    }

    private fun emptyWalkthrough() = Walkthrough(
        intent = "x",
        files = emptyList(),
        risks = emptyList(),
    )
}
