package com.pawranoid.domain.service.support

import com.pawranoid.infrastructure.github.PullRequestFile
import com.pawranoid.infrastructure.github.PullRequestMeta

/**
 * 코드 외 PR 자체에 대한 결정적(LLM-free) 평가.
 *
 * 평가 라인 리스트를 만든다. 각 라인은 머리에 굵게 쓴 핵심 + em-dash + 친절한 부연.
 *
 * 룰 (출력 순서):
 * - **PR 메타 다듬기**: 제목/설명/커밋 메시지 중 모호한 게 있을 때만 한 줄로 묶어 안내.
 * - **머지 상태**: 항상 1줄 노출. mergeable_state 에 따라 친절 메시지 분기.
 * - **사이즈**: 임계 초과 시만.
 * - **테스트 동반 여부**: production 변경에 따라.
 */
object PrEvaluator {

    fun evaluate(
        meta: PullRequestMeta,
        files: List<PullRequestFile>,
        commitMessages: List<String> = emptyList(),
    ): List<String> = buildList {
        add(metaLine(meta, commitMessages))
        add(mergeLine(meta.mergeableState))
        sizeLine(meta, files.size)?.let { add(it) }
        testCoverageLine(files)?.let { add(it) }
    }

    /**
     * 제목·설명·커밋 메시지 품질을 한 줄로 합쳐 안내.
     * 셋 다 깔끔하면 긍정 라인, 하나라도 모호하면 다듬기 안내.
     */
    private fun metaLine(meta: PullRequestMeta, commitMessages: List<String>): String {
        val flagged = mutableListOf<String>()
        if (isVagueTitle(meta.title)) flagged += "제목"
        if (isShortDescription(meta.body)) flagged += "설명"
        if (commitMessages.any(::isVagueCommit)) flagged += "커밋 메시지"
        return if (flagged.isEmpty()) {
            "**PR 메타 깔끔** — 제목, 설명, 커밋 메시지 모두 의도가 잘 드러나요."
        } else {
            val items = flagged.joinToString(", ")
            "**PR 메타 다듬기** — $items 가 충분히 구체적이지 않아요. 변경 의도를 한 줄 더 풀어 적어주시면 좋겠습니다."
        }
    }

    private fun isVagueTitle(title: String): Boolean {
        val n = title.trim().lowercase()
        return n.length < MIN_TITLE_LEN || VAGUE_TITLE_PATTERN.matches(n)
    }

    private fun isShortDescription(body: String): Boolean = body.trim().length < MIN_DESCRIPTION_LEN

    private fun isVagueCommit(message: String): Boolean {
        val first = message.lines().firstOrNull()?.trim()?.lowercase() ?: return false
        return first.length < 5 || VAGUE_COMMIT_PATTERN.matches(first)
    }

    private fun mergeLine(state: String?): String = when (state) {
        "clean" -> "**병합 가능** — 충돌 없이 그대로 main에 병합할 수 있어요."
        "dirty" -> "**충돌 있음** — main과 충돌이 발생합니다. rebase 후 다시 시도해주세요."
        "behind" -> "**베이스가 앞서 있음** — main의 최신 커밋이 빠져 있어요. rebase 권장."
        "blocked" -> "**병합 차단됨** — 보호 룰(필수 리뷰/체크)에 막혀 있어요."
        "unstable" -> "**CI 불안정** — 일부 체크가 실패했거나 진행 중이에요. 통과 확인 후 병합 권장."
        "draft" -> "**Draft 상태** — 아직 작업 중이라 병합 대기 중입니다."
        "has_hooks" -> "**병합 가능** — 충돌 없음. 병합 후 hook이 실행될 예정이에요."
        null, "unknown" -> "**병합 가능 여부 계산 중** — GitHub가 충돌 검사를 마치면 결정됩니다."
        else -> "**병합 상태**: $state"
    }

    private fun sizeLine(meta: PullRequestMeta, fileCount: Int): String? {
        val totalLines = meta.additions + meta.deletions
        return when {
            fileCount > 30 || totalLines > 1500 ->
                "**사이즈가 매우 큽니다** ($fileCount files, ${meta.additions}+/${meta.deletions}- lines) — " +
                    "작은 단위로 분리하면 리뷰 시간이 크게 줄어듭니다."
            fileCount > 15 || totalLines > 500 ->
                "**사이즈가 큽니다** ($fileCount files, ${meta.additions}+/${meta.deletions}- lines) — " +
                    "가능하면 작게 쪼개면 리뷰가 훨씬 수월해집니다."
            else -> null
        }
    }

    private fun testCoverageLine(files: List<PullRequestFile>): String? {
        val production = files.filter { isProductionFile(it.path) }
        if (production.isEmpty()) return null
        return if (files.any { isTestFile(it.path) }) {
            "**테스트 반영됨** — production 변경에 맞춰 테스트도 같이 갱신됐어요."
        } else {
            "**테스트 변경 없음** — production 코드 ${production.size}개가 바뀌었는데 테스트는 그대로예요. " +
                "회귀 방지용으로 테스트 추가를 권장합니다."
        }
    }

    private fun isTestFile(path: String): Boolean =
        path.contains("/test/") ||
            path.contains("/tests/") ||
            path.contains("/__tests__/") ||
            TEST_FILENAME_PATTERN.containsMatchIn(path.substringAfterLast('/'))

    private fun isProductionFile(path: String): Boolean {
        if (isTestFile(path)) return false
        if (NON_PRODUCTION_PATTERN.containsMatchIn(path)) return false
        return true
    }

    private const val MIN_TITLE_LEN = 10
    private const val MIN_DESCRIPTION_LEN = 30

    private val VAGUE_TITLE_PATTERN = Regex("^(wip|fix|update|updates|test|tmp|temp|misc|chore|init|todo|.{1,2})\\.?$")
    private val VAGUE_COMMIT_PATTERN = Regex("^(wip|fix|update|updates|test|tmp|temp|asdf|aaa+|\\.+)\\.?$")

    private val TEST_FILENAME_PATTERN = Regex("(Test|Tests|Spec)\\.(kt|java)$|\\.(test|spec)\\.[a-z]+$")

    private val NON_PRODUCTION_PATTERN = Regex(
        "(^|/)(README|CHANGELOG|LICENSE|\\.gitignore|\\.editorconfig)" +
            "|\\.(md|txt|adoc|rst|yml|yaml|json|toml|properties|gradle|gradle\\.kts)$",
    )
}
