package com.pbot.bot.domain.service

import com.fasterxml.jackson.databind.JsonNode
import com.pbot.bot.domain.port.LlmPort
import com.pbot.bot.infrastructure.github.GitHubClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * 봇이 자신이 단 인라인 리뷰의 정당성을 별도 LLM으로 cross-verify 한다.
 *
 * 사용자 흐름:
 * 1. 봇이 인라인 리뷰 작성 → 사용자가 답글에 `/verify` 또는 `@pawranoid verify`
 * 2. webhook → 이 서비스가 비동기로 처리
 * 3. 원본 코멘트 + 현재 코드를 다른 LLM(Claude)에게 보여주고 의견 받음
 * 4. 그 결과를 답글로 게시
 *
 * 사용 모델:
 * - 주 리뷰는 OpenAI(GptClient, @Primary)
 * - 검증은 Claude(@Qualifier로 강제 주입) — 자기 출력 옹호 위험 최소화
 */
@Service
class VerifyService(
    private val gitHubClient: GitHubClient,
    @Qualifier("claudeClient") private val verifierLlm: LlmPort,
    @Value("\${github.bot.mention}") private val botMention: String,
) {
    private val log = LoggerFactory.getLogger(VerifyService::class.java)
    private val botLogin = "$botMention[bot]"

    @Async
    fun verifyReviewComment(repo: String, prNumber: Int, parentCommentId: Long) {
        log.info("Verifying review comment: repo={} pr=#{} commentId={}", repo, prNumber, parentCommentId)
        val original = gitHubClient.fetchReviewComment(repo, parentCommentId)

        // 봇이 단 코멘트가 아니면 검증 의미 없음
        val author = original["user"]["login"].asText()
        if (author != botLogin) {
            log.info("Skip verify: parent comment is not by bot (author={})", author)
            return
        }

        val verdict = runVerification(original)
        gitHubClient.replyToReviewComment(repo, prNumber, parentCommentId, verdict)
        log.info("Verification reply posted to comment={}", parentCommentId)
    }

    private fun runVerification(original: JsonNode): String {
        val path = original["path"].asText()
        val line = original["line"]?.asInt() ?: original["original_line"]?.asInt() ?: -1
        val originalBody = original["body"].asText()
        val diffHunk = original["diff_hunk"]?.asText() ?: "(no diff hunk available)"

        // verifier에게 보낼 입력 — 짧고 비판적 시각 유도
        val prompt = buildString {
            appendLine("You are an INDEPENDENT senior reviewer auditing another reviewer's comment.")
            appendLine("Your job: verify whether the comment identifies a REAL issue, or is a false positive.")
            appendLine("Be willing to disagree if the original is wrong.")
            appendLine()
            appendLine("=== File ===")
            appendLine(path)
            appendLine()
            appendLine("=== Code at L$line (diff hunk) ===")
            appendLine(diffHunk)
            appendLine()
            appendLine("=== Reviewer's comment ===")
            appendLine(originalBody)
            appendLine()
            appendLine("Output strictly as JSON in summary field with one of: AGREE, DISAGREE, PARTIAL.")
            appendLine("Examples:")
            appendLine("  - 'AGREE: 실제로 IOException 발생 가능. 처리 추가 권장.'")
            appendLine("  - 'DISAGREE: foo() 정의에 throws 없음. 원래 코멘트 false positive.'")
            appendLine("  - 'PARTIAL: 의도는 맞으나 라인이 어긋남. 실제론 L20에서 발생.'")
            appendLine("Reply summary in Korean, 2-3 sentences. issues should be empty list.")
        }

        // 우리 LlmPort.review는 ReviewResult를 반환. summary 필드에 verdict 텍스트 들어옴.
        val result = verifierLlm.review(prompt)
        val verdict = result.summary
        return formatVerdict(verdict)
    }

    private fun formatVerdict(verdict: String): String {
        val emoji = when {
            verdict.startsWith("AGREE", ignoreCase = true) -> "✅"
            verdict.startsWith("DISAGREE", ignoreCase = true) -> "⚠️"
            verdict.startsWith("PARTIAL", ignoreCase = true) -> "🟡"
            else -> "🤖"
        }
        return buildString {
            appendLine("**$emoji Cross-verify (Claude Sonnet)**")
            appendLine()
            append(verdict)
        }
    }
}
