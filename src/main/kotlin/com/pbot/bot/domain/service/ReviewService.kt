package com.pbot.bot.domain.service

import com.pbot.bot.domain.model.ReviewComment
import com.pbot.bot.domain.model.ReviewIssue
import com.pbot.bot.domain.port.LlmPort
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

        val context = filesForLlm.joinToString("\n\n") { file ->
            buildFileContext(repo, headSha, file)
        }
        val result = llmPort.review(context)

        // 검증은 LLM이 실제로 본 파일만 — maxFiles 밖 파일 언급은 hallucination으로 간주.
        val (validIssues, droppedIssues) = result.issues.partition { isLineInDiff(it, filesForLlm) }
        val comments = validIssues.map { issue ->
            val actualPath = matchFile(issue.path, filesForLlm)?.path ?: issue.path
            ReviewComment(path = actualPath, line = issue.line, body = issue.comment)
        }
        val summary = mergeDroppedIntoSummary(result.summary, droppedIssues)

        gitHubClient.postReview(repo, number, summary, comments)
        log.info("Review posted for {}#{}: {} inline comments, {} dropped", repo, number, comments.size, droppedIssues.size)
    }

    /**
     * 파일 하나에 대한 LLM 입력을 만든다.
     * 파일이 작으면 전체 내용 + diff, 크면 diff만 보내서 토큰을 아낀다.
     *
     * FULL FILE 섹션에는 줄 번호를 prefix로 붙여서 LLM이 CHANGES의 L-prefix와
     * 정확히 매칭할 수 있게 한다 (LLM이 직접 카운트하다 어긋나는 일 방지).
     */
    private fun buildFileContext(repo: String, sha: String, file: PullRequestFile): String {
        val annotated = annotatePatch(file)
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
     * GPT가 준 path와 PR 변경 파일 중에서 매칭되는 걸 찾는다.
     * 정확 일치 → suffix match 순서로 시도.
     * 정규화: 앞뒤 공백 제거, 선행 슬래시 제거.
     */
    private fun matchFile(path: String, files: List<PullRequestFile>): PullRequestFile? {
        val normalized = path.trim().trimStart('/')
        if (normalized.isEmpty()) return null
        return files.find {
            val filePath = it.path.trim().trimStart('/')
            filePath == normalized || filePath.endsWith("/$normalized")
        }
    }

    private fun isLineInDiff(issue: ReviewIssue, files: List<PullRequestFile>): Boolean {
        val file = matchFile(issue.path, files) ?: return false
        val patch = file.patch ?: return false
        return lineNumbersInDiff(patch).contains(issue.line)
    }

    /**
     * 인라인으로 못 단 의견을 summary 본문에 합쳐서 잃지 않게 한다.
     * GitHub의 hunk 바깥 라인엔 인라인을 못 달기 때문에 GPT의 진짜 인사이트가 사라지는 걸 방지.
     */
    private fun mergeDroppedIntoSummary(original: String, dropped: List<ReviewIssue>): String {
        if (dropped.isEmpty()) return original
        return buildString {
            append(original)
            append("\n\n**추가 의견 (인라인 위치 매칭 실패):**\n")
            dropped.forEach { append("- `${it.path}:${it.line}` ${it.comment}\n") }
        }
    }

    /**
     * patch에 라인 번호를 붙여서 LLM이 정확한 라인을 짚을 수 있게 한다.
     *
     * 출력 예:
     * ```
     * === src/main/kotlin/Foo.kt ===
     * @@ -10,5 +14,8 @@
     * L14     class Foo(
     * L15 [+]     @Async
     * L16     fun bar() {
     * L-  [-]     val old = ...
     * ```
     */
    private fun annotatePatch(file: PullRequestFile): String {
        val sb = StringBuilder()
        sb.appendLine("=== ${file.path} ===")
        val patch = file.patch ?: run {
            sb.appendLine("(binary or no patch)")
            return sb.toString()
        }
        var newLine = 0
        for (raw in patch.lines()) {
            when {
                raw.startsWith("@@") -> {
                    val match = Regex("""\+(\d+)""").find(raw) ?: continue
                    newLine = match.groupValues[1].toInt()
                    sb.appendLine(raw)
                }
                raw.startsWith("+++") -> {}
                raw.startsWith("---") -> {}
                raw.startsWith("+") -> {
                    sb.appendLine("L%-4d [+] %s".format(newLine, raw.substring(1)))
                    newLine++
                }
                raw.startsWith("-") -> {
                    sb.appendLine("L--   [-] %s".format(raw.substring(1)))
                }
                raw.startsWith(" ") -> {
                    sb.appendLine("L%-4d     %s".format(newLine, raw.substring(1)))
                    newLine++
                }
                // 빈 줄/알 수 없는 prefix는 무시 (trailing newline 등)
            }
        }
        return sb.toString()
    }

    /**
     * patch 텍스트를 파싱해서 diff hunk 안에 있는 새 파일 라인 번호 집합을 만든다.
     * 추가된 라인(+) 뿐 아니라 컨텍스트 라인(변경 안 된 주변 라인)도 인라인 코멘트 가능.
     */
    private fun lineNumbersInDiff(patch: String): Set<Int> {
        val lines = mutableSetOf<Int>()
        var newLine = 0
        for (raw in patch.lines()) {
            when {
                raw.startsWith("@@") -> {
                    val match = Regex("""\+(\d+)""").find(raw) ?: continue
                    newLine = match.groupValues[1].toInt()
                }
                raw.startsWith("+++") -> {}
                raw.startsWith("---") -> {}
                raw.startsWith("+") -> {
                    lines.add(newLine)
                    newLine++
                }
                raw.startsWith("-") -> {}
                raw.startsWith(" ") -> {
                    lines.add(newLine)
                    newLine++
                }
                // 빈 줄/알 수 없는 prefix는 카운트 증가시키지 않음 (phantom line 방지)
            }
        }
        return lines
    }
}
