package com.pawranoid.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookSignatureVerifierTest {

    private val secret = "test-secret-12345"
    private val body = """{"action":"opened"}""".toByteArray(StandardCharsets.UTF_8)

    @Test
    fun `verify returns false when signature header is missing`() {
        val result = WebhookSignatureVerifier.verify(body, null, secret)

        assertThat(result).isFalse()
    }

    @Test
    fun `verify returns false on incorrect signature`() {
        val result = WebhookSignatureVerifier.verify(body, "sha256=deadbeef", secret)

        assertThat(result).isFalse()
    }

    @Test
    fun `verify returns true when signature matches HMAC-SHA-256 of body`() {
        val signature = "sha256=" + computeHmacHex(body, secret)

        val result = WebhookSignatureVerifier.verify(body, signature, secret)

        assertThat(result).isTrue()
    }

    @Test
    fun `verify returns false when signature is missing the sha256 prefix`() {
        val rawHex = computeHmacHex(body, secret)

        val result = WebhookSignatureVerifier.verify(body, rawHex, secret)

        assertThat(result).isFalse()
    }

    @Test
    fun `verify is sensitive to body modifications`() {
        val signature = "sha256=" + computeHmacHex(body, secret)
        val tamperedBody = """{"action":"closed"}""".toByteArray(StandardCharsets.UTF_8)

        val result = WebhookSignatureVerifier.verify(tamperedBody, signature, secret)

        assertThat(result).isFalse()
    }

    @Test
    fun `verify is sensitive to secret mismatch`() {
        val signature = "sha256=" + computeHmacHex(body, secret)

        val result = WebhookSignatureVerifier.verify(body, signature, "different-secret")

        assertThat(result).isFalse()
    }

    /**
     * 한글이 포함된 본문은 UTF-8 바이트 그대로 HMAC 이 계산되어야 한다.
     * (이전 String 기반 구현은 JVM 기본 charset / Spring StringHttpMessageConverter
     * 의 ISO-8859-1 폴백에 따라 다르게 인코딩될 수 있어 회귀 테스트로 고정.)
     */
    @Test
    fun `verify handles multi-byte UTF-8 body correctly`() {
        val koreanBody = """{"title":"버그 수정","body":"한글 본문"}""".toByteArray(StandardCharsets.UTF_8)
        val signature = "sha256=" + computeHmacHex(koreanBody, secret)

        val result = WebhookSignatureVerifier.verify(koreanBody, signature, secret)

        assertThat(result).isTrue()
    }

    /**
     * 시크릿에 한글 등 멀티바이트가 들어가도 UTF-8 로 일관되게 인코딩되어야 한다.
     */
    @Test
    fun `verify uses UTF-8 for secret encoding`() {
        val multiByteSecret = "비밀-키-한글"
        val signature = "sha256=" + computeHmacHex(body, multiByteSecret)

        val result = WebhookSignatureVerifier.verify(body, signature, multiByteSecret)

        assertThat(result).isTrue()
    }

    private fun computeHmacHex(body: ByteArray, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body).joinToString("") { "%02x".format(it) }
    }
}
