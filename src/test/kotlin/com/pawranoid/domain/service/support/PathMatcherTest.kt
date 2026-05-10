package com.pawranoid.domain.service.support

import com.pawranoid.infrastructure.github.PullRequestFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PathMatcherTest {

    private val files = listOf(
        PullRequestFile(path = "src/main/kotlin/com/pbot/Foo.kt", patch = "..."),
        PullRequestFile(path = "src/main/kotlin/com/pbot/Bar.kt", patch = "..."),
        PullRequestFile(path = "README.md", patch = "..."),
    )

    @Test
    fun `match returns file on exact path match`() {
        val result = PathMatcher.match("src/main/kotlin/com/pbot/Foo.kt", files)

        assertThat(result?.path).isEqualTo("src/main/kotlin/com/pbot/Foo.kt")
    }

    @Test
    fun `match returns file when LLM gives short suffix`() {
        // LLM이 종종 짧게 줌 → 디렉토리 경계 기준 suffix 매칭
        val result = PathMatcher.match("Foo.kt", files)

        assertThat(result?.path).isEqualTo("src/main/kotlin/com/pbot/Foo.kt")
    }

    @Test
    fun `match strips leading slash before comparing`() {
        val result = PathMatcher.match("/src/main/kotlin/com/pbot/Foo.kt", files)

        assertThat(result?.path).isEqualTo("src/main/kotlin/com/pbot/Foo.kt")
    }

    @Test
    fun `match trims surrounding whitespace`() {
        val result = PathMatcher.match("  README.md  ", files)

        assertThat(result?.path).isEqualTo("README.md")
    }

    @Test
    fun `match returns null when no file matches`() {
        val result = PathMatcher.match("does/not/exist.kt", files)

        assertThat(result).isNull()
    }

    @Test
    fun `match returns null for empty path`() {
        val result = PathMatcher.match("", files)

        assertThat(result).isNull()
    }

    @Test
    fun `match returns null for whitespace-only path`() {
        val result = PathMatcher.match("   ", files)

        assertThat(result).isNull()
    }

    @Test
    fun `match does not partial-match within filename (must align with directory boundary)`() {
        val result = PathMatcher.match("oo.kt", files)

        // "oo.kt" 는 "Foo.kt" 의 일부지만 디렉토리 경계가 아니라서 매칭 안 됨
        assertThat(result).isNull()
    }
}
