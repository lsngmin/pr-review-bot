package com.pbot.bot.infrastructure.github

import com.fasterxml.jackson.databind.JsonNode
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

@Service
class GitHubAuthService(
    @Value("\${github.app.id}") val appId: String,
    @Value("\${github.app.installation-id}") val installationId: String,
    @Value("\${github.app.private-key-path}") val privateKeyPath: String,
) {
    private val rest = RestClient.create()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Instant = Instant.EPOCH

    /**
     * 외부에서 호출하는 메서드.
     * 캐시된 토큰이 유효하면 그대로, 만료 임박이면 갱신해서 반환.
     */
    fun getInstallationToken(): String {
        if (Instant.now().isAfter(tokenExpiresAt.minusSeconds(60))) {
            refreshToken()
        }
        return cachedToken!!
    }

    /**
     * JWT로 GitHub에 요청해서 새 Installation Token 받아 캐싱.
     */
    private fun refreshToken() {
        val jwt = createAppJwt()
        val response = rest.post()
            .uri("https://api.github.com/app/installations/$installationId/access_tokens")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .retrieve()
            .body(JsonNode::class.java)!!

        cachedToken = response["token"].asText()
        tokenExpiresAt = Instant.parse(response["expires_at"].asText())
    }

    /**
     * App ID + private key로 JWT 만들기.
     * GitHub 요구사항: RS256, iss = App ID, exp 최대 10분.
     */
    private fun createAppJwt(): String {
        val now = Instant.now()
        return Jwts.builder()
            .issuedAt(Date.from(now.minusSeconds(60)))    // -60s: 시계 어긋남 보정
            .expiration(Date.from(now.plusSeconds(540)))  // 9분 (최대 10분)
            .issuer(appId)
            .signWith(loadPrivateKey(), Jwts.SIG.RS256)
            .compact()
    }

    /**
     * .pem 파일 읽어서 PrivateKey 객체로 변환.
     * (PKCS#8 변환된 파일이라고 가정)
     */
    private fun loadPrivateKey(): PrivateKey {
        val pem = Files.readString(Paths.get(privateKeyPath))
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(cleaned)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }
}