package com.pbot.bot.domain.service.support

import com.pbot.bot.infrastructure.github.PullRequestFile
import com.pbot.bot.infrastructure.github.PullRequestMeta
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PrEvaluatorTest {

    // 기본값은 모든 메타 룰을 통과하는 깨끗한 PR. 개별 테스트가 필요한 인자만 override.
    private fun meta(
        title: String = "Add OAuth refresh-token rotation flow",
        body: String = "이 PR은 기존 JWT 인증을 OAuth로 교체합니다. 테스트는 OAuthFlowTest 참고.",
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

        assertThat(lines).anySatisfy {
            assertThat(it).startsWith("**병합 가능**").contains("충돌 없")
        }
    }

    @Test
    fun `merge dirty asks user to rebase`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "dirty"), files = listOf(prodFile(), testFile()))

        assertThat(lines).anySatisfy {
            assertThat(it).startsWith("**충돌 있음**").contains("rebase")
        }
    }

    @Test
    fun `merge behind suggests rebase`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "behind"), files = listOf(prodFile(), testFile()))

        assertThat(lines).anySatisfy {
            assertThat(it).contains("앞서 있음").contains("rebase")
        }
    }

    @Test
    fun `merge blocked describes protection rule`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "blocked"), files = listOf(prodFile(), testFile()))

        assertThat(lines).anySatisfy { assertThat(it).contains("차단") }
    }

    @Test
    fun `merge unstable warns about CI`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = "unstable"), files = listOf(prodFile(), testFile()))

        assertThat(lines).anySatisfy { assertThat(it).contains("CI") }
    }

    @Test
    fun `merge null reports computing state`() {
        val lines = PrEvaluator.evaluate(meta(mergeableState = null), files = listOf(prodFile(), testFile()))

        assertThat(lines).anySatisfy { assertThat(it).contains("계산 중") }
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
    fun `production change with tests adds positive test-included line`() {
        val lines = PrEvaluator.evaluate(meta(), files = listOf(prodFile(), testFile()))

        assertThat(lines).anySatisfy {
            assertThat(it).startsWith("**테스트 반영됨**")
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

    // --- meta line (combined title/description/commit) ---

    @Test
    fun `meta line shows positive headline when all clean`() {
        val lines = PrEvaluator.evaluate(
            meta(),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("feat: add OAuth refresh-token rotation flow"),
        )

        assertThat(lines.first()).startsWith("**PR 메타 깔끔**")
    }

    @Test
    fun `meta line flags vague title only`() {
        val lines = PrEvaluator.evaluate(
            meta(title = "wip"),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("feat: real change"),
        )

        assertThat(lines.first()).startsWith("**PR 메타 다듬기**").contains("제목").doesNotContain("설명").doesNotContain("커밋 메시지")
    }

    @Test
    fun `meta line flags short description only`() {
        val lines = PrEvaluator.evaluate(
            meta(body = ""),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("feat: real change"),
        )

        assertThat(lines.first()).contains("설명").doesNotContain("제목").doesNotContain("커밋 메시지")
    }

    @Test
    fun `meta line flags vague commit messages only`() {
        val lines = PrEvaluator.evaluate(
            meta(),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("wip", "fix"),
        )

        assertThat(lines.first()).contains("커밋 메시지").doesNotContain("제목").doesNotContain("설명")
    }

    @Test
    fun `meta line combines all three when all are flagged`() {
        val lines = PrEvaluator.evaluate(
            meta(title = "update", body = ""),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("wip"),
        )

        val first = lines.first()
        assertThat(first).startsWith("**PR 메타 다듬기**")
        assertThat(first).contains("제목").contains("설명").contains("커밋 메시지")
    }

    @Test
    fun `meta line appears above merge line when present`() {
        val lines = PrEvaluator.evaluate(
            meta(title = "wip"),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("feat: real change"),
        )

        val metaIdx = lines.indexOfFirst { it.startsWith("**PR 메타") }
        val mergeIdx = lines.indexOfFirst { it.startsWith("**병합") || it.startsWith("**충돌") || it.startsWith("**Draft") }
        assertThat(metaIdx).isGreaterThanOrEqualTo(0)
        assertThat(metaIdx).isLessThan(mergeIdx)
    }

    // --- combined ---

    @Test
    fun `dirty large no-test PR with vague meta returns four lines in fixed order`() {
        val files = (1..20).map { prodFile("src/main/kotlin/F$it.kt") }
        val lines = PrEvaluator.evaluate(
            meta(title = "wip", body = "", mergeableState = "dirty", additions = 600, deletions = 100, changedFiles = 20),
            files,
            commitMessages = listOf("wip"),
        )

        assertThat(lines).hasSize(4)
        assertThat(lines[0]).startsWith("**PR 메타 다듬기**")
        assertThat(lines[1]).startsWith("**충돌 있음**")
        assertThat(lines[2]).startsWith("**사이즈가 큽니다**")
        assertThat(lines[3]).startsWith("**테스트 변경 없음**")
    }
}
