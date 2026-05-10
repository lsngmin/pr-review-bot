package com.pbot.bot.domain.service.support

import com.pbot.bot.infrastructure.github.PullRequestFile
import com.pbot.bot.infrastructure.github.PullRequestMeta

/**
 * 코드 외 PR 자체에 대한 결정적(LLM-free) 평가.
 *
 * Files changed 표 바로 아래에 한 블록으로 띄울 짧은 평가 라인 리스트를 만든다.
 * 각 라인은 머리에 굵게 쓴 핵심 + em-dash + 친절한 부연.
 *
 * 룰:
 * - **머지 상태**: 항상 1줄 노출. mergeable_state 에 따라 친절 메시지 분기.
 * - **사이즈**: 임계 초과 시만.
 * - **테스트 동반 여부**: production 변경 있는데 테스트 변경 없을 때만.
 */
object PrEvaluator {

    fun evaluate(meta: PullRequestMeta, files: List<PullRequestFile>): List<String> = buildList {
        add(mergeLine(meta.mergeableState))
        sizeLine(meta, files.size)?.let { add(it) }
        testCoverageLine(files)?.let { add(it) }
    }

    private fun mergeLine(state: String?): String = when (state) {
        "clean" -> "**머지 가능** — 충돌 없이 그대로 main에 병합할 수 있어요."
        "dirty" -> "**충돌 있음** — main과 충돌이 발생합니다. rebase 후 다시 시도해주세요."
        "behind" -> "**베이스가 앞서 있음** — main의 최신 커밋이 빠져 있어요. rebase 권장."
        "blocked" -> "**머지 차단됨** — 보호 룰(필수 리뷰/체크)에 막혀 있어요."
        "unstable" -> "**CI 불안정** — 일부 체크가 실패했거나 진행 중이에요. 통과 확인 후 머지 권장."
        "draft" -> "**Draft 상태** — 아직 작업 중이라 머지 대기 중입니다."
        "has_hooks" -> "**머지 가능** — 충돌 없음. 머지 후 hook이 실행될 예정이에요."
        null, "unknown" -> "**머지 가능 여부 계산 중** — GitHub가 충돌 검사를 마치면 결정됩니다."
        else -> "**머지 상태**: $state"
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
            "**테스트 함께 변경됨** — production 변경에 맞춰 테스트도 같이 갱신됐어요."
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

    private val TEST_FILENAME_PATTERN = Regex("(Test|Tests|Spec)\\.(kt|java)$|\\.(test|spec)\\.[a-z]+$")

    private val NON_PRODUCTION_PATTERN = Regex(
        "(^|/)(README|CHANGELOG|LICENSE|\\.gitignore|\\.editorconfig)" +
            "|\\.(md|txt|adoc|rst|yml|yaml|json|toml|properties|gradle|gradle\\.kts)$",
    )
}
