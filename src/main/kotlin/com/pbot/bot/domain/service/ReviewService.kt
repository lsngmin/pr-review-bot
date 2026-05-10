package com.pbot.bot.domain.service

import com.pbot.bot.domain.model.ReviewIssue
import com.pbot.bot.domain.port.LlmPort
import com.pbot.bot.domain.service.support.CommentBuilder
import com.pbot.bot.domain.service.support.DiffAnnotator
import com.pbot.bot.domain.service.support.PathMatcher
import com.pbot.bot.domain.service.support.PrEvaluator
import com.pbot.bot.domain.service.support.WalkthroughBuilder
import com.pbot.bot.infrastructure.github.GitHubClient
import com.pbot.bot.infrastructure.github.PullRequestFile
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ReviewService(
    private val gitHubClient: GitHubClient,
    private val llmPort: LlmPort,
    private val history: ReviewHistoryService,

    @Value("\${review.max-file-lines}") private val maxFileLines: Int,
    @Value("\${review.max-files}") private val maxFiles: Int,
) {
    private val log = LoggerFactory.getLogger(ReviewService::class.java)

    @Async
    fun reviewPullRequest(repo: String, number: Int) {
        log.info("Starting review for {}#{}", repo, number)
        val headSha = gitHubClient.fetchPullRequestHeadSha(repo, number)

        if (!history.tryClaim(repo, number, headSha)) {
            log.info("Skipping duplicate review for {}#{} (sha {} already claimed)", repo, number, headSha)
            return
        }

        val allFiles = gitHubClient.fetchFiles(repo, number)
        val filesForLlm = allFiles.take(maxFiles)
        if (allFiles.size > maxFiles) {
            log.warn("PR {}#{} has {} files, only first {} sent to LLM", repo, number, allFiles.size, maxFiles)
        }

        // PR 자체에 대한 결정적 평가 (메타 다듬기/머지/사이즈/테스트). LLM 실패와 무관하게
        // 사용자에게 도달하도록 본 리뷰 흐름과 분리해 미리 계산. 실패 시 빈 리스트 fallback.
        val prEvaluation = runCatching {
            PrEvaluator.evaluate(
                meta = gitHubClient.fetchPullRequest(repo, number),
                files = allFiles,
                commitMessages = gitHubClient.fetchCommitMessages(repo, number),
            )
        }.getOrElse {
            log.warn("PR evaluation failed for {}#{}", repo, number, it)
            emptyList()
        }

        val context = filesForLlm.joinToString("\n\n") { file ->
            buildFileContext(repo, headSha, file)
        }
        val result = llmPort.review(context)

        // 검증은 LLM이 실제로 본 파일만 — maxFiles 밖 파일 언급은 hallucination으로 간주.
        val (validIssues, droppedIssues) = result.issues.partition { isIssueInDiff(it, filesForLlm) }
        val comments = validIssues.map { issue ->
            val actualPath = PathMatcher.match(issue.path, filesForLlm)?.path ?: issue.path
            CommentBuilder.build(issue, actualPath)
        }

        // walkthrough + 검토 통계 + 평가 한 markdown 으로 합쳐 PR Review body 로 게시.
        val combinedBody = WalkthroughBuilder.build(
            walkthrough = result.walkthrough,
            evaluation = prEvaluation,
            reviewedFileCount = filesForLlm.size,
            totalFileCount = allFiles.size,
            inlineCommentCount = comments.size,
            droppedCommentCount = droppedIssues.size,
        )
        gitHubClient.postReview(repo, number, combinedBody, comments)
        log.info(
            "Review posted for {}#{}: combined body({} eval lines) + {} inline comments, {} dropped",
            repo, number, prEvaluation.size, comments.size, droppedIssues.size,
        )
    }

    /**
     * 파일 하나에 대한 LLM 입력을 만든다.
     * 파일이 작으면 전체 내용 + diff, 크면 diff만 보내서 토큰을 아낀다.
     *
     * FULL FILE 섹션에는 줄 번호를 prefix로 붙여서 LLM이 CHANGES의 L-prefix와
     * 정확히 매칭할 수 있게 한다 (LLM이 직접 카운트하다 어긋나는 일 방지).
     */
    private fun buildFileContext(repo: String, sha: String, file: PullRequestFile): String {
        val annotated = DiffAnnotator.annotatePatch(file)
        val full = runCatching { gitHubClient.fetchFileContent(repo, file.path, sha) }
            .getOrNull()
            ?: return annotated

        if (full.lines().size > maxFileLines) return annotated

        val numberedFull = full.lines()
            .mapIndexed { index, line -> "%4d: %s".format(index + 1, line) }
            .joinToString("\n")

        return buildString {
            appendLine("=== FULL FILE: ${file.path} ===")
            appendLine(numberedFull)
            appendLine()
            appendLine("=== CHANGES IN ${file.path} ===")
            append(annotated)
        }
    }

    /**
     * issue가 가리키는 라인(들)이 diff hunk 안에 있는지 검증.
     * 다중 라인이면 startLine..line 범위의 **모든 라인**이 hunk 안에 있어야 한다 —
     * GitHub multi-line review comment는 연속된 diff 범위를 요구하므로 중간이 비면 422.
     */
    private fun isIssueInDiff(issue: ReviewIssue, files: List<PullRequestFile>): Boolean {
        val file = PathMatcher.match(issue.path, files) ?: return false
        val patch = file.patch ?: return false
        val validLines = DiffAnnotator.lineNumbersInDiff(patch)
        if (issue.line !in validLines) return false
        if (issue.startLine != null) {
            if (issue.startLine > issue.line) return false
            if ((issue.startLine..issue.line).any { it !in validLines }) return false
        }
        return true
    }
}
