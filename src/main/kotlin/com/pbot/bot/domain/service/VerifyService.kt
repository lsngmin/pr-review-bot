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
 * 1. 봇이 인라인 리뷰 작성 → 사용자가 답글에 `@pawranoid verify`
 * 2. webhook → 이 서비스가 비동기로 처리
 * 3. 원본 코멘트 + 주변 코드(±[CONTEXT_RADIUS]줄)를 다른 LLM(Claude)에게 보여주고 의견 받음
 * 4. 자연스러운 한국어 답글로 게시 (라벨/배지 없이)
 *
 * 사용 모델:
 * - 주 리뷰는 OpenAI(GptClient, @Primary)
 * - 검증은 Claude(@Qualifier로 강제 주입) — 자기 출력 옹호 위험 최소화
 */
@Service
class VerifyService(
    private val gitHubClient: GitHubClient,
    @Qualifier("claudeClient") private val verifierLlm: LlmPort,
    private val history: VerifyHistoryService,
    @Value("\${github.bot.mention}") private val botMention: String,
) {
    private val log = LoggerFactory.getLogger(VerifyService::class.java)
    private val botLogin = "$botMention[bot]"

    @Async
    fun verifyReviewComment(repo: String, prNumber: Int, parentCommentId: Long) {
        if (!history.tryClaim(parentCommentId)) {
            log.info("Skip verify: comment={} already in flight", parentCommentId)
            return
        }
        try {
            runVerify(repo, prNumber, parentCommentId)
        } finally {
            history.release(parentCommentId)
        }
    }

    private fun runVerify(repo: String, prNumber: Int, parentCommentId: Long) {
        log.info("Verifying review comment: repo={} pr=#{} commentId={}", repo, prNumber, parentCommentId)
        val original = gitHubClient.fetchReviewComment(repo, parentCommentId)

        // 봇이 단 코멘트가 아니면 검증 의미 없음
        val author = original["user"]["login"].asText()
        if (author != botLogin) {
            log.info("Skip verify: parent comment is not by bot (author={})", author)
            return
        }

        val replyBody = runCatching { runVerification(repo, original) }
            .getOrElse { e ->
                log.error("Verification LLM call failed for comment={}", parentCommentId, e)
                "Verifier 호출이 실패해 판단하지 못했습니다. (`${e.javaClass.simpleName}`)"
            }
        gitHubClient.replyToReviewComment(repo, prNumber, parentCommentId, replyBody)
        log.info("Verification reply posted to comment={}", parentCommentId)
    }

    private fun runVerification(repo: String, original: JsonNode): String {
        val path = original["path"].asText()
        val line = original["line"]?.asInt() ?: original["original_line"]?.asInt() ?: -1
        val originalBody = original["body"].asText()
        val diffHunk = original["diff_hunk"]?.asText() ?: "(no diff hunk available)"
        val commitId = original["commit_id"]?.asText()
            ?: original["original_commit_id"]?.asText()

        val surrounding = commitId?.let { fetchSurrounding(repo, path, it, line) }
            ?: "(surrounding code unavailable)"

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
            appendLine("=== Surrounding code (±$CONTEXT_RADIUS lines, → marks the commented line) ===")
            appendLine(surrounding)
            appendLine()
            appendLine("=== Reviewer's comment ===")
            appendLine(originalBody)
            appendLine()
            appendLine("Reply in summary field as a natural Korean conversation, 2-3 sentences.")
            appendLine("Do NOT use rigid prefixes like 'AGREE:', 'DISAGREE:', 'PARTIAL:', or any")
            appendLine("badges/headers. Just talk like you're discussing with a colleague — state")
            appendLine("whether you agree, partially agree, or disagree, and explain why concisely.")
            appendLine("issues should be empty list.")
        }

        // 우리 LlmPort.review는 ReviewResult를 반환. summary 필드에 답글 본문이 들어옴.
        val result = verifierLlm.review(prompt)
        log.info("Verify reply produced for path={} line={}", path, line)
        return result.summary
    }

    /**
     * 코멘트가 달린 라인 ±[CONTEXT_RADIUS] 줄을 줄번호와 함께 추출.
     * 대상 라인은 화살표(→)로 강조해 verifier가 어디에 집중할지 명시.
     */
    private fun fetchSurrounding(repo: String, path: String, ref: String, line: Int): String? {
        if (line <= 0) return null
        val full = runCatching { gitHubClient.fetchFileContent(repo, path, ref) }
            .getOrElse {
                log.warn("Failed to fetch file content for verify context: path={} ref={}", path, ref, it)
                return null
            }
        val lines = full.lines()
        if (lines.isEmpty()) return null
        val from = maxOf(1, line - CONTEXT_RADIUS)
        val to = minOf(lines.size, line + CONTEXT_RADIUS)
        return (from..to).joinToString("\n") { ln ->
            val mark = if (ln == line) "→" else " "
            "%s%4d: %s".format(mark, ln, lines[ln - 1])
        }
    }

    private companion object {
        const val CONTEXT_RADIUS = 20
    }
}
