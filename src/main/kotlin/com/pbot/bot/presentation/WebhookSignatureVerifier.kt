package com.pbot.bot.presentation

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * GitHub webhook의 HMAC-SHA-256 서명을 검증한다.
 *
 * 검증은 timing attack을 막기 위해 [MessageDigest.isEqual] 로 상수 시간 비교를 사용한다.
 */
object WebhookSignatureVerifier {

    /**
     * @param body  raw 요청 본문 문자열
     * @param signatureHeader `X-Hub-Signature-256` 헤더 값 (예: `"sha256=<hex>"`).
     *                        null이면 검증 실패.
     * @param secret webhook 등록 시 입력한 비밀값
     * @return 서명이 일치하면 true, 아니면 false
     */
    fun verify(body: String, signatureHeader: String?, secret: String): Boolean {
        if (signatureHeader == null) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val computed = "sha256=" + mac.doFinal(body.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(
            signatureHeader.toByteArray(),
            computed.toByteArray()
        )
    }
}
