package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ReviewIssue
import com.pbot.bot.domain.model.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommentBuilderTest {

    @Test
    fun `body is just the comment when no suggestion`() {
        val issue = ReviewIssue(
            path = "Foo.kt", line = 42, startLine = null, severity = Severity.MEDIUM,
            comment = "여기 null 처리 필요해요.", suggestion = null,
        )

        val comment = CommentBuilder.build(issue, actualPath = "src/Foo.kt")

        assertThat(comment.body).isEqualTo("여기 null 처리 필요해요.")
        assertThat(comment.path).isEqualTo("src/Foo.kt")
        assertThat(comment.line).isEqualTo(42)
        assertThat(comment.startLine).isNull()
    }

    @Test
    fun `body wraps suggestion in markdown suggestion fence`() {
        val issue = ReviewIssue(
            path = "Foo.kt", line = 42, startLine = null, severity = Severity.MEDIUM,
            comment = "null 가능성 있음.", suggestion = "val foo = parseFoo() ?: return",
        )

        val comment = CommentBuilder.build(issue, actualPath = "Foo.kt")

        assertThat(comment.body).isEqualTo(
            """
            null 가능성 있음.

            ```suggestion
            val foo = parseFoo() ?: return
            ```
            """.trimIndent()
        )
    }

    @Test
    fun `multi-line suggestion preserves startLine`() {
        val issue = ReviewIssue(
            path = "Foo.kt", line = 45, startLine = 42, severity = Severity.MEDIUM,
            comment = "예외 처리 추가.",
            suggestion = "try {\n    foo()\n} catch (e: IOException) {\n    log.error(\"failed\", e)\n}",
        )

        val comment = CommentBuilder.build(issue, actualPath = "Foo.kt")

        assertThat(comment.startLine).isEqualTo(42)
        assertThat(comment.line).isEqualTo(45)
        assertThat(comment.body).contains("```suggestion\ntry {")
        assertThat(comment.body).contains("```")
    }

    @Test
    fun `blank suggestion is treated as no suggestion`() {
        val issue = ReviewIssue(
            path = "Foo.kt", line = 42, startLine = null, severity = Severity.MEDIUM,
            comment = "코멘트만.", suggestion = "   ",
        )

        val comment = CommentBuilder.build(issue, actualPath = "Foo.kt")

        assertThat(comment.body).isEqualTo("코멘트만.")
        assertThat(comment.body).doesNotContain("suggestion")
    }

    @Test
    fun `suggestion ending with newline does not add extra newline before fence close`() {
        val issue = ReviewIssue(
            path = "Foo.kt", line = 42, startLine = null, severity = Severity.MEDIUM,
            comment = "fix this", suggestion = "val safe = foo ?: return\n",
        )

        val comment = CommentBuilder.build(issue, actualPath = "Foo.kt")

        // suggestion이 \n으로 끝나도 닫힘 fence 앞에 빈 줄이 생기면 안 됨
        // (단, 코멘트와 suggestion 사이 \n\n 은 의도된 구분자)
        assertThat(comment.body).endsWith("val safe = foo ?: return\n```")
    }

    @Test
    fun `actualPath replaces the path from issue`() {
        val issue = ReviewIssue(
            path = "Foo.kt", line = 1, startLine = null, severity = Severity.MEDIUM,
            comment = "x", suggestion = null,
        )

        val comment = CommentBuilder.build(issue, actualPath = "src/main/kotlin/Foo.kt")

        assertThat(comment.path).isEqualTo("src/main/kotlin/Foo.kt")
    }

    @Test
    fun `fence is longer than any backtick run inside suggestion`() {
        // suggestion 본문에 ``` 가 들어있으면 우리 fence가 닫혀버리니
        // 본문 backtick run보다 길게 fence를 잡아야 함
        val issue = ReviewIssue(
            path = "Foo.kt", line = 42, startLine = null, severity = Severity.MEDIUM,
            comment = "use longer fence",
            suggestion = "val md = \"\"\"\n```kotlin\nval x = 1\n```\n\"\"\"",
        )

        val comment = CommentBuilder.build(issue, actualPath = "Foo.kt")

        // 본문에 ``` 있으니 우리는 ```` 사용해야 함
        assertThat(comment.body).contains("````suggestion")
        assertThat(comment.body).endsWith("````")
    }

    @Test
    fun `fence is even longer when suggestion contains four backticks`() {
        val issue = ReviewIssue(
            path = "Foo.kt", line = 1, startLine = null, severity = Severity.MEDIUM,
            comment = "very tricky",
            suggestion = "weird ```` text",
        )

        val comment = CommentBuilder.build(issue, actualPath = "Foo.kt")

        // 본문 max run 4 → fence 5
        assertThat(comment.body).contains("`````suggestion")
        assertThat(comment.body).endsWith("`````")
    }
}
