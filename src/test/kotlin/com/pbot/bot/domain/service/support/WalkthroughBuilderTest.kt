package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.FileChange
import com.pbot.bot.domain.model.FileChangeType
import com.pbot.bot.domain.model.ReviewIssue
import com.pbot.bot.domain.model.RiskHighlight
import com.pbot.bot.domain.model.Severity
import com.pbot.bot.domain.model.Walkthrough
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WalkthroughBuilderTest {

    private fun issue(severity: Severity, suggestion: String? = null) = ReviewIssue(
        path = "Foo.kt",
        line = 1,
        startLine = null,
        severity = severity,
        comment = "...",
        suggestion = suggestion,
    )

    @Test
    fun `header always present`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough(), emptyList())

        assertThat(md).contains("## 🐶 Pawranoid Walkthrough")
    }

    @Test
    fun `intent appears under What changed section`() {
        val w = Walkthrough(
            intent = "OAuth migration.",
            files = listOf(FileChange("Foo.kt", FileChangeType.REFACTOR, "JWT → OAuth")),
            risks = emptyList(),
        )

        val md = WalkthroughBuilder.build(w, emptyList())

        assertThat(md).contains("### 📝 What changed")
        assertThat(md).contains("OAuth migration.")
    }

    @Test
    fun `files table renders type label and summary`() {
        val w = Walkthrough(
            intent = "x",
            files = listOf(
                FileChange("Foo.kt", FileChangeType.NEW, "신규 OAuth callback"),
                FileChange("Bar.kt", FileChangeType.REFACTOR, "JWT 제거"),
            ),
            risks = emptyList(),
        )

        val md = WalkthroughBuilder.build(w, emptyList())

        assertThat(md).contains("| `Foo.kt` | ✨ New | 신규 OAuth callback |")
        assertThat(md).contains("| `Bar.kt` | 🔄 Refactor | JWT 제거 |")
    }

    @Test
    fun `risks section omitted when empty`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough(), emptyList())

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

        val md = WalkthroughBuilder.build(w, emptyList())

        assertThat(md).contains("### ⚠️ Risk highlights")
        assertThat(md).contains("- 🔴 **HIGH** — 인증 로직 교체 (`AuthService.kt:42`)")
        assertThat(md).contains("- 🟡 **MEDIUM** — 외부 의존성 추가")
    }

    @Test
    fun `Reviewed section shows No issues found when empty`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough(), emptyList())

        assertThat(md).contains("### 🔍 Reviewed")
        assertThat(md).contains("No issues found")
    }

    @Test
    fun `Reviewed section shows severity-bucketed counts`() {
        val issues = listOf(
            issue(Severity.HIGH),
            issue(Severity.HIGH),
            issue(Severity.MEDIUM),
            issue(Severity.LOW),
        )

        val md = WalkthroughBuilder.build(emptyWalkthrough(), issues)

        assertThat(md).contains("**4 issues found**: 🔴 2 · 🟡 1 · 🟢 1")
    }

    @Test
    fun `Reviewed shows suggestion count when any issue has suggestion`() {
        val issues = listOf(
            issue(Severity.MEDIUM, suggestion = "fix code"),
            issue(Severity.MEDIUM, suggestion = null),
            issue(Severity.LOW, suggestion = "another fix"),
        )

        val md = WalkthroughBuilder.build(emptyWalkthrough(), issues)

        assertThat(md).contains("**2 suggestions** with auto-fix available")
    }

    @Test
    fun `Reviewed omits suggestion line when none have suggestions`() {
        val issues = listOf(issue(Severity.MEDIUM, suggestion = null))

        val md = WalkthroughBuilder.build(emptyWalkthrough(), issues)

        assertThat(md).doesNotContain("suggestions with auto-fix")
    }

    @Test
    fun `footer is consistent`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough(), emptyList())

        assertThat(md).contains("---")
        assertThat(md).contains("*Triggered by `/review`*")
    }

    private fun emptyWalkthrough() = Walkthrough(
        intent = "x",
        files = emptyList(),
        risks = emptyList(),
    )
}
