package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ReviewIssue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SummaryBuilderTest {

    @Test
    fun `returns original summary when no dropped issues`() {
        val result = SummaryBuilder.mergeDroppedIntoSummary("All looks good.", emptyList())

        assertThat(result).isEqualTo("All looks good.")
    }

    @Test
    fun `appends section header when one dropped issue`() {
        val dropped = listOf(
            ReviewIssue(path = "Foo.kt", line = 42, comment = "잠재적 버그"),
        )

        val result = SummaryBuilder.mergeDroppedIntoSummary("요약", dropped)

        assertThat(result).startsWith("요약")
        assertThat(result).contains("**추가 의견 (인라인 위치 매칭 실패):**")
        assertThat(result).contains("`Foo.kt:42`")
        assertThat(result).contains("잠재적 버그")
    }

    @Test
    fun `lists every dropped issue in markdown bullet form`() {
        val dropped = listOf(
            ReviewIssue("A.kt", 1, "first"),
            ReviewIssue("B.kt", 2, "second"),
            ReviewIssue("C.kt", 3, "third"),
        )

        val result = SummaryBuilder.mergeDroppedIntoSummary("base", dropped)

        assertThat(result).contains("- `A.kt:1` first")
        assertThat(result).contains("- `B.kt:2` second")
        assertThat(result).contains("- `C.kt:3` third")
    }

    @Test
    fun `keeps original summary text intact at the start`() {
        val original = "이 PR은 X를 수정합니다.\n버그 위험이 있습니다."
        val dropped = listOf(ReviewIssue("Foo.kt", 1, "comment"))

        val result = SummaryBuilder.mergeDroppedIntoSummary(original, dropped)

        assertThat(result).startsWith(original)
    }
}
