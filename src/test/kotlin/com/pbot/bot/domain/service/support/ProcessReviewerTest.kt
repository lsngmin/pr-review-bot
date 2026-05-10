package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.Severity
import com.pbot.bot.infrastructure.github.PullRequestFile
import com.pbot.bot.infrastructure.github.PullRequestMeta
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessReviewerTest {

    private fun meta(
        title: String = "Add OAuth login flow with refresh-token rotation",
        body: String = "이 PR은 기존 JWT 인증을 OAuth로 교체합니다. 테스트는 OAuthFlowTest 참고.",
        additions: Int = 50,
        deletions: Int = 10,
        changedFiles: Int = 3,
    ) = PullRequestMeta(title, body, additions, deletions, changedFiles)

    private fun prodFile(path: String = "src/main/kotlin/com/pbot/Foo.kt") = PullRequestFile(path, "patch")
    private fun testFile(path: String = "src/test/kotlin/com/pbot/FooTest.kt") = PullRequestFile(path, "patch")

    @Test
    fun `clean PR with everything good produces only the positive test note`() {
        val notes = ProcessReviewer.review(
            meta(),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("feat: add OAuth login flow with refresh-token rotation"),
        )

        assertThat(notes).singleElement().satisfies({
            assertThat(it.severity).isEqualTo(Severity.LOW)
            assertThat(it.message).contains("테스트가 함께 변경됨")
        })
    }

    // --- size ---

    @Test
    fun `flags HIGH on very large PR`() {
        val files = (1..40).map { prodFile("src/main/kotlin/F$it.kt") } + testFile()
        val notes = ProcessReviewer.review(
            meta(additions = 2000, deletions = 200, changedFiles = 41),
            files = files,
            commitMessages = listOf("feat: large refactor"),
        )

        assertThat(notes).anySatisfy {
            assertThat(it.severity).isEqualTo(Severity.HIGH)
            assertThat(it.message).contains("PR이 너무 큼")
        }
    }

    @Test
    fun `flags MEDIUM on moderately large PR`() {
        val files = (1..20).map { prodFile("src/main/kotlin/F$it.kt") } + testFile()
        val notes = ProcessReviewer.review(
            meta(additions = 600, deletions = 100, changedFiles = 21),
            files = files,
            commitMessages = listOf("feat: moderate refactor"),
        )

        assertThat(notes).anySatisfy {
            assertThat(it.severity).isEqualTo(Severity.MEDIUM)
            assertThat(it.message).contains("PR 사이즈가 큼")
        }
    }

    // --- title ---

    @Test
    fun `flags vague title`() {
        val notes = ProcessReviewer.review(
            meta(title = "update"),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("update"),
        )

        assertThat(notes).anySatisfy {
            assertThat(it.message).contains("추상적임")
        }
    }

    @Test
    fun `flags too-short title with the same vague message`() {
        // 짧은 제목과 추상적 단어("fix"/"update")는 사실상 같은 결함 — 단일 메시지로 통합.
        val notes = ProcessReviewer.review(
            meta(title = "fix bug"),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("fix bug"),
        )

        assertThat(notes).anySatisfy {
            assertThat(it.message).contains("추상적")
        }
    }

    // --- description ---

    @Test
    fun `flags empty PR body`() {
        val notes = ProcessReviewer.review(
            meta(body = ""),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("feat: x"),
        )

        assertThat(notes).anySatisfy {
            assertThat(it.message).contains("PR 설명이 비어")
        }
    }

    @Test
    fun `does not flag well-described PR`() {
        val notes = ProcessReviewer.review(
            meta(),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("feat: well-described change with reasoning"),
        )

        assertThat(notes).noneSatisfy {
            assertThat(it.message).contains("PR 설명")
        }
    }

    // --- commit messages ---

    @Test
    fun `flags vague commit messages`() {
        val notes = ProcessReviewer.review(
            meta(),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf("wip", "fix", "feat: real one"),
        )

        assertThat(notes).anySatisfy {
            assertThat(it.message).contains("커밋 메시지")
            assertThat(it.message).contains("2/3")
        }
    }

    @Test
    fun `does not flag good commit messages`() {
        val notes = ProcessReviewer.review(
            meta(),
            files = listOf(prodFile(), testFile()),
            commitMessages = listOf(
                "feat: add OAuth refresh flow",
                "test: cover refresh-token rotation cases",
            ),
        )

        assertThat(notes).noneSatisfy {
            assertThat(it.message).contains("커밋 메시지")
        }
    }

    // --- test coverage ---

    @Test
    fun `flags missing tests for production change`() {
        val notes = ProcessReviewer.review(
            meta(),
            files = listOf(prodFile()),
            commitMessages = listOf("feat: add Foo logic"),
        )

        assertThat(notes).anySatisfy {
            assertThat(it.severity).isEqualTo(Severity.MEDIUM)
            assertThat(it.message).contains("테스트 변경 없음")
        }
    }

    @Test
    fun `does not require tests for doc-only PR`() {
        val notes = ProcessReviewer.review(
            meta(title = "Update README with new section about auth"),
            files = listOf(PullRequestFile("README.md", "patch")),
            commitMessages = listOf("docs: clarify auth section"),
        )

        assertThat(notes).noneSatisfy {
            assertThat(it.message).contains("테스트")
        }
    }

    @Test
    fun `does not require tests for config-only PR`() {
        val notes = ProcessReviewer.review(
            meta(title = "Bump Spring Boot to 3.5.1 for CVE patch"),
            files = listOf(PullRequestFile("build.gradle.kts", "patch")),
            commitMessages = listOf("build: bump spring-boot 3.5.1"),
        )

        assertThat(notes).noneSatisfy {
            assertThat(it.severity == Severity.MEDIUM && it.message.contains("테스트 변경 없음"))
        }
    }

    @Test
    fun `recognizes multiple test path conventions`() {
        // /test/, /__tests__/, *Test.kt, *.test.ts 모두 테스트로 인식
        val files = listOf(
            prodFile("src/main/kotlin/Foo.kt"),
            PullRequestFile("src/test/kotlin/FooTest.kt", "patch"),
        )
        val notes = ProcessReviewer.review(meta(), files, listOf("feat: add Foo with tests"))

        assertThat(notes).noneSatisfy {
            assertThat(it.message).contains("테스트 변경 없음")
        }
    }
}
