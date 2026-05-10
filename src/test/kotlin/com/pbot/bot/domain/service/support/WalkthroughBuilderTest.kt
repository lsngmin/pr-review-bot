package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.FileChange
import com.pbot.bot.domain.model.FileChangeType
import com.pbot.bot.domain.model.Walkthrough
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WalkthroughBuilderTest {

    @Test
    fun `header is plain text Pawranoid PR overview`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).contains("## Pawranoid PR overview")
        assertThat(md).doesNotContain("🐶")
    }

    @Test
    fun `intent appears as floating paragraph just below the header`() {
        val w = Walkthrough(
            intent = "OAuth migration.",
            changes = emptyList(),
            files = listOf(FileChange("Foo.kt", FileChangeType.REFACTOR, "JWT → OAuth")),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).doesNotContain("### ① What changed")
        assertThat(md).contains("OAuth migration.")
        // intent 가 헤더 바로 다음 등장.
        assertThat(md.indexOf("OAuth migration.")).isGreaterThan(md.indexOf("## Pawranoid PR overview"))
    }

    // --- changes (### What Changed) ---

    @Test
    fun `changes section omitted when empty`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).doesNotContain("### What Changed")
    }

    @Test
    fun `changes section renders bullets one per line`() {
        val w = Walkthrough(
            intent = "x",
            changes = listOf("OAuth refresh 회전 추가", "기존 JWT 검증 제거"),
            files = emptyList(),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).contains("### What Changed")
        assertThat(md).contains("- OAuth refresh 회전 추가")
        assertThat(md).contains("- 기존 JWT 검증 제거")
    }

    // --- review stats (### What Reviewed) ---

    @Test
    fun `reviewed full coverage with comments`() {
        val md = WalkthroughBuilder.build(
            emptyWalkthrough(),
            reviewedFileCount = 23,
            totalFileCount = 23,
            inlineCommentCount = 3,
        )

        assertThat(md).contains("### What Reviewed")
        assertThat(md).contains("변경된 파일 23개를 모두 살펴봤어요. 인라인 코멘트는 3개 남겼습니다.")
    }

    @Test
    fun `reviewed full coverage with no comments`() {
        val md = WalkthroughBuilder.build(
            emptyWalkthrough(),
            reviewedFileCount = 5,
            totalFileCount = 5,
            inlineCommentCount = 0,
        )

        assertThat(md).contains("변경된 파일 5개를 모두 살펴봤어요. 인라인 코멘트는 따로 남기지 않았습니다.")
    }

    @Test
    fun `reviewed partial coverage (truncated by maxFiles)`() {
        val md = WalkthroughBuilder.build(
            emptyWalkthrough(),
            reviewedFileCount = 20,
            totalFileCount = 25,
            inlineCommentCount = 2,
        )

        assertThat(md).contains("변경된 파일 25개 중 20개를 살펴봤어요. 인라인 코멘트는 2개 남겼습니다.")
    }

    @Test
    fun `reviewed mentions dropped only when greater than zero`() {
        val cleanMd = WalkthroughBuilder.build(emptyWalkthrough(), inlineCommentCount = 2, droppedCommentCount = 0)
        val droppedMd = WalkthroughBuilder.build(emptyWalkthrough(), inlineCommentCount = 2, droppedCommentCount = 4)

        assertThat(cleanMd).doesNotContain("보류")
        assertThat(droppedMd).contains("(4개 의견은 라인 매칭 실패로 보류)")
    }

    // --- collapsible files table ---

    @Test
    fun `files table omitted when no files`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).doesNotContain("<details>")
        assertThat(md).doesNotContain("파일별 요약")
    }

    @Test
    fun `files table wrapped in details summary collapsible block`() {
        val w = Walkthrough(
            intent = "x",
            changes = emptyList(),
            files = listOf(
                FileChange("src/main/kotlin/com/pbot/bot/domain/Foo.kt", FileChangeType.NEW, "신규 OAuth callback"),
                FileChange("src/main/kotlin/com/pbot/bot/domain/Bar.kt", FileChangeType.REFACTOR, "JWT 제거"),
            ),
        )

        val md = WalkthroughBuilder.build(w)

        assertThat(md).contains("<details>")
        assertThat(md).contains("<summary>파일별 요약</summary>")
        assertThat(md).contains("| `Foo.kt` | New | 신규 OAuth callback |")
        assertThat(md).contains("| `Bar.kt` | Refactor | JWT 제거 |")
        assertThat(md).contains("</details>")
    }

    // --- evaluation blockquote (at the very end) ---

    @Test
    fun `evaluation block omitted when empty`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough(), evaluation = emptyList())

        assertThat(md).doesNotContain("> ")
    }

    @Test
    fun `evaluation block renders each line with blockquote prefix`() {
        val lines = listOf(
            "**병합 가능** — 충돌 없음.",
            "**사이즈가 큽니다** (12 files) — 분리 권장.",
        )

        val md = WalkthroughBuilder.build(emptyWalkthrough(), evaluation = lines)

        assertThat(md).contains("> **병합 가능** — 충돌 없음.")
        assertThat(md).contains("> **사이즈가 큽니다** (12 files) — 분리 권장.")
    }

    @Test
    fun `evaluation lines separated by empty blockquote line for paragraph break`() {
        val lines = listOf("first line", "second line", "third line")

        val md = WalkthroughBuilder.build(emptyWalkthrough(), evaluation = lines)

        val expected = "> first line\n>\n> second line\n>\n> third line"
        assertThat(md).contains(expected)
    }

    // --- ordering invariant ---

    @Test
    fun `sections appear in order header, intent, changes, reviewed stats, files details, evaluation`() {
        val w = Walkthrough(
            intent = "PR overview text",
            changes = listOf("change A"),
            files = listOf(FileChange("Foo.kt", FileChangeType.NEW, "x")),
        )

        val md = WalkthroughBuilder.build(
            w,
            evaluation = listOf("**병합 가능** — clean."),
            reviewedFileCount = 1,
            totalFileCount = 1,
            inlineCommentCount = 0,
        )

        val headerIdx = md.indexOf("## Pawranoid PR overview")
        val intentIdx = md.indexOf("PR overview text")
        val changesIdx = md.indexOf("### What Changed")
        val statsIdx = md.indexOf("### What Reviewed")
        val detailsIdx = md.indexOf("<details>")
        val evalIdx = md.indexOf("> **병합 가능**")

        assertThat(headerIdx).isLessThan(intentIdx)
        assertThat(intentIdx).isLessThan(changesIdx)
        assertThat(changesIdx).isLessThan(statsIdx)
        assertThat(statsIdx).isLessThan(detailsIdx)
        assertThat(detailsIdx).isLessThan(evalIdx)
    }

    // --- removed legacy sections ---

    @Test
    fun `no legacy sections remain`() {
        val md = WalkthroughBuilder.build(emptyWalkthrough())

        assertThat(md).doesNotContain("What changed")
        assertThat(md).doesNotContain("Files changed")
        assertThat(md).doesNotContain("Risk highlights")
        assertThat(md).doesNotContain("Reviewed section")
        assertThat(md).doesNotContain("Process notes")
        assertThat(md).doesNotContain("Triggered by")
        assertThat(md).doesNotContain("\n---")
    }

    private fun emptyWalkthrough() = Walkthrough(
        intent = "x",
        changes = emptyList(),
        files = emptyList(),
    )
}
