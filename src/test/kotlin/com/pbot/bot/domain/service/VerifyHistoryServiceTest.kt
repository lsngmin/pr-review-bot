package com.pbot.bot.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerifyHistoryServiceTest {

    @Test
    fun `first claim succeeds`() {
        val s = VerifyHistoryService()

        assertThat(s.tryClaim(1L)).isTrue()
    }

    @Test
    fun `second concurrent claim on same comment fails`() {
        val s = VerifyHistoryService()
        s.tryClaim(1L)

        assertThat(s.tryClaim(1L)).isFalse()
    }

    @Test
    fun `claim succeeds again after release`() {
        val s = VerifyHistoryService()
        s.tryClaim(1L)
        s.release(1L)

        assertThat(s.tryClaim(1L)).isTrue()
    }

    @Test
    fun `different comments are independent`() {
        val s = VerifyHistoryService()

        assertThat(s.tryClaim(1L)).isTrue()
        assertThat(s.tryClaim(2L)).isTrue()
    }

    @Test
    fun `release on un-claimed id is a no-op`() {
        val s = VerifyHistoryService()

        s.release(99L)
        assertThat(s.tryClaim(99L)).isTrue()
    }
}
