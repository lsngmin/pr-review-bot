package com.pbot.bot.domain.service.support

import com.pbot.bot.infrastructure.github.PullRequestFile
import com.pbot.bot.infrastructure.github.PullRequestMeta
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PrEvaluatorTest {

    private fun meta(
        title: String = "x",
        body: String = "x",
        additions: Int = 50,
        deletions: Int = 10,
        changedFiles: Int = 3,
        mergeableState: String? = "clean",
    ) = PullRequestMeta(title, body, additions, deletions, changedFiles, mergeableState)

    private fun prodFile(path: String = "src/main/kotlin/com/pbot/Foo.kt") = PullRequestFile(path, "patch")
    private fun testFile(path: String = "src/test/kotlin/com/pbot/FooTest.kt") = PullRequestFile(path, "patch")

    // --- merge state (always shown) ---

    @Test
    fun `merge clean is described positively and merge line always present`() {
        val lines = PrEvaluator.evaluate(meta(), files = listOf(prodFile(), testFile()))

        assertThat(lines).hasSize(1)
        assertThat(lines.first()).startsWith("**머지 가능**").contains("충돌 없")
    }

    @Test
    fun `merge dirty asks user to rebase`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "dirty"), files = listOf(prodFile(), testFile()))

        assertThat(lines.first()).startsWith("**충돌 있음**").contains("rebase")
    }

    @Test
    fun `merge behind suggests rebase`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "behind"), files = listOf(prodFile(), testFile()))

        assertThat(lines.first()).contains("앞서 있음").contains("rebase")
    }

    @Test
    fun `merge blocked describes protection rule`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "blocked"), files = listOf(prodFile(), testFile()))

        assertThat(lines.first()).contains("차단")
    }

    @Test
    fun `merge unstable warns about CI`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "unstable"), files = listOf(prodFile(), testFile()))

        assertThat(lines.first()).contains("CI")
    }

    @Test
    fun `merge null reports computing state`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = null), files = listOf(prodFile(), testFile()))

        assertThat(lines.first()).contains("계산 중")
    }

    // --- size ---

    @Test
    fun `large PR adds size line`() {
        val files = (1..20).map { prodFile("src/main/kotlin/F$it.kt") } + testFile()
        val lines = PrEvaluator.evaluate(meta(additions = 600, deletions = 100, changedFiles = 21), files)

        assertThat(lines).anySatisfy {
            assertThat(it).startsWith("**사이즈가 큽니다**")
        }
    }

    @Test
    fun `very large PR uses stronger wording`() {
        val files = (1..40).map { prodFile("src/main/kotlin/F$it.kt") } + testFile()
        val lines = PrEvaluator.evaluate(meta(additions = 2000, deletions = 200, changedFiles = 41), files)

        assertThat(lines).anySatisfy {
            assertThat(it).startsWith("**사이즈가 매우 큽니다**")
        }
    }

    @Test
    fun `small PR omits size line`() {
        val lines = PrEvaluator.evaluate(meta(), files = listOf(prodFile(), testFile()))

        assertThat(lines).noneSatisfy {
            assertThat(it).contains("사이즈")
        }
    }

    // --- test coverage ---

    @Test
    fun `production change without tests adds test line`() {
        val lines = PrEvaluator.evaluate(meta(), files = listOf(prodFile()))

        assertThat(lines).anySatisfy {
            assertThat(it).startsWith("**테스트 변경 없음**")
        }
    }

    @Test
    fun `production change with tests omits test line (no positive note)`() {
        val lines = PrEvaluator.evaluate(meta(), files = listOf(prodFile(), testFile()))

        assertThat(lines).noneSatisfy {
            assertThat(it).contains("테스트")
        }
    }

    @Test
    fun `doc-only PR omits test line`() {
        val lines = PrEvaluator.evaluate(meta(), files = listOf(PullRequestFile("README.md", "patch")))

        assertThat(lines).noneSatisfy {
            assertThat(it).contains("테스트")
        }
    }

    @Test
    fun `config-only PR omits test line`() {
        val lines = PrEvaluator.evaluate(meta(), files = listOf(PullRequestFile("build.gradle.kts", "patch")))

        assertThat(lines).noneSatisfy {
            assertThat(it).contains("테스트")
        }
    }

    // --- combined ---

    @Test
    fun `dirty large no-test PR returns three lines in fixed order`() {
        val files = (1..20).map { prodFile("src/main/kotlin/F$it.kt") }
        val lines = PrEvaluator.evaluate(
            meta(mergeableState = "dirty", additions = 600, deletions = 100, changedFiles = 20),
            files,
        )

        assertThat(lines).hasSize(3)
        assertThat(lines[0]).startsWith("**충돌 있음**")
        assertThat(lines[1]).startsWith("**사이즈가 큽니다**")
        assertThat(lines[2]).startsWith("**테스트 변경 없음**")
    }
}
