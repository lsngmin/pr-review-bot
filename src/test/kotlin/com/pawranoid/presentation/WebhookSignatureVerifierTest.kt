package com.pawranoid.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookSignatureVerifierTest {

    private val secret = "test-secret-12345"
    private val body = """{"action":"opened"}"""

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
        val tamperedBody = """{"action":"closed"}"""

        val result = WebhookSignatureVerifier.verify(tamperedBody, signature, secret)

        assertThat(result).isFalse()
    }

    @Test
    fun `verify is sensitive to secret mismatch`() {
        val signature = "sha256=" + computeHmacHex(body, secret)

        val result = WebhookSignatureVerifier.verify(body, signature, "different-secret")

        assertThat(result).isFalse()
    }

    private fun computeHmacHex(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
