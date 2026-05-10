package com.pawranoid.presentation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * GitHub webhook의 HMAC-SHA-256 서명을 검증한다.
 *
 * 검증은 timing attack을 막기 위해 [MessageDigest.isEqual] 로 상수 시간 비교를 사용한다.
 *
 * 본문은 raw byte 로 받는다 — String 으로 받으면 Spring 의 `StringHttpMessageConverter`
 * 가 Content-Type 의 charset 부재 시 ISO-8859-1 로 디코딩하기도 해서, GitHub 이 UTF-8
 * 로 보낸 멀티바이트(한글/일본어 등) 본문에서 HMAC 가 깨질 수 있다.
 */
object WebhookSignatureVerifier {

    /**
     * @param body  raw 요청 본문 바이트 (HTTP 바디 그대로)
     * @param signatureHeader `X-Hub-Signature-256` 헤더 값 (예: `"sha256=<hex>"`).
     *                        null이면 검증 실패.
     * @param secret webhook 등록 시 입력한 비밀값
     * @return 서명이 일치하면 true, 아니면 false
     */
    fun verify(body: ByteArray, signatureHeader: String?, secret: String): Boolean {
        if (signatureHeader == null) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val computed = "sha256=" + mac.doFinal(body)
            .joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(
            signatureHeader.toByteArray(StandardCharsets.UTF_8),
            computed.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
