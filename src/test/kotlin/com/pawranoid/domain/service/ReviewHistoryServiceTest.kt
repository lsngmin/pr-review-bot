package com.pawranoid.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewHistoryServiceTest {

    @Test
    fun `tryClaim succeeds on first call`() {
        val history = ReviewHistoryService()

        val claimed = history.tryClaim("owner/repo", 1, "sha-abc")

        assertThat(claimed).isTrue()
    }

    @Test
    fun `tryClaim returns false when same SHA already claimed`() {
        val history = ReviewHistoryService()
        history.tryClaim("owner/repo", 1, "sha-abc")

        val secondAttempt = history.tryClaim("owner/repo", 1, "sha-abc")

        assertThat(secondAttempt).isFalse()
    }

    @Test
    fun `tryClaim succeeds on different SHA for same PR (new commit pushed)`() {
        val history = ReviewHistoryService()
        history.tryClaim("owner/repo", 1, "sha-abc")

        val newSha = history.tryClaim("owner/repo", 1, "sha-def")

        assertThat(newSha).isTrue()
    }

    @Test
    fun `tryClaim is independent across different PRs`() {
        val history = ReviewHistoryService()
        history.tryClaim("owner/repo", 1, "sha-abc")

        val otherPr = history.tryClaim("owner/repo", 2, "sha-abc")

        assertThat(otherPr).isTrue()
    }

    @Test
    fun `tryClaim is independent across different repos`() {
        val history = ReviewHistoryService()
        history.tryClaim("owner/repo-a", 1, "sha-abc")

        val otherRepo = history.tryClaim("owner/repo-b", 1, "sha-abc")

        assertThat(otherRepo).isTrue()
    }

    @Test
    fun `after new SHA replaces old, old SHA can no longer be claimed`() {
        val history = ReviewHistoryService()
        history.tryClaim("owner/repo", 1, "sha-old")
        history.tryClaim("owner/repo", 1, "sha-new")

        // 옛 SHA를 다시 시도해도 false면 안 됨 — 옛 SHA는 더 이상 추적 안 함, 새로 처리 가능해야 함
        val tryOldAgain = history.tryClaim("owner/repo", 1, "sha-old")

        assertThat(tryOldAgain).isTrue()
    }
}
