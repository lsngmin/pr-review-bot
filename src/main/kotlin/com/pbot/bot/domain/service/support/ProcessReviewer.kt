package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ProcessNote
import com.pbot.bot.domain.model.Severity
import com.pbot.bot.infrastructure.github.PullRequestFile
import com.pbot.bot.infrastructure.github.PullRequestMeta

/**
 * PR 자체에 대한 결정적(LLM-free) 평가.
 *
 * "코드가 좋은가" 가 아닌 "PR을 잘 만들었나" 를 본다 — PR 사이즈, 제목/설명 품질,
 * 커밋 메시지, 테스트 동반 여부. 시니어 리뷰어가 PR 본문 보지 않고도 "이 PR
 * 이상한데" 라고 느끼는 첫인상을 룰화한 것.
 *
 * 모든 룰은 순수 함수. 토큰 사용 0.
 */
object ProcessReviewer {

    fun review(
        meta: PullRequestMeta,
        files: List<PullRequestFile>,
        commitMessages: List<String>,
    ): List<ProcessNote> = buildList {
        sizeNote(meta, files.size)?.let { add(it) }
        titleNote(meta.title)?.let { add(it) }
        descriptionNote(meta.body)?.let { add(it) }
        commitMessageNote(commitMessages)?.let { add(it) }
        testCoverageNote(files)?.let { add(it) }
    }

    /**
     * 사이즈 임계값. 리뷰 어려움이 급격히 올라가는 지점을 보수적으로 잡음.
     * 사용자가 일반적인 feature PR을 보냈을 때 false positive 안 나오도록.
     */
    private fun sizeNote(meta: PullRequestMeta, fileCount: Int): ProcessNote? {
        val totalLines = meta.additions + meta.deletions
        return when {
            fileCount > 30 || totalLines > 1500 -> ProcessNote(
                Severity.HIGH,
                "PR이 너무 큼 ($fileCount files, ${meta.additions}+/${meta.deletions}- lines). 분리 권장.",
            )
            fileCount > 15 || totalLines > 500 -> ProcessNote(
                Severity.MEDIUM,
                "PR 사이즈가 큼 ($fileCount files, ${meta.additions}+/${meta.deletions}- lines). 가능하면 분리.",
            )
            else -> null
        }
    }

    private fun titleNote(title: String): ProcessNote? {
        val normalized = title.trim().lowercase()
        if (normalized.length < MIN_TITLE_LEN || VAGUE_TITLE_PATTERN.matches(normalized)) {
            return ProcessNote(Severity.MEDIUM, "PR 제목이 추상적임 (\"${title.trim()}\"). 변경 의도를 구체적으로.")
        }
        return null
    }

    private fun descriptionNote(body: String): ProcessNote? {
        val trimmed = body.trim()
        if (trimmed.length < MIN_DESCRIPTION_LEN) {
            return ProcessNote(Severity.MEDIUM, "PR 설명이 비어 있거나 너무 짧음. 변경 의도와 테스트 방법을 적어주세요.")
        }
        return null
    }

    private fun commitMessageNote(messages: List<String>): ProcessNote? {
        if (messages.isEmpty()) return null
        val vague = messages.filter { isVagueCommit(it) }
        if (vague.isEmpty()) return null
        return ProcessNote(
            Severity.MEDIUM,
            "커밋 메시지 ${vague.size}/${messages.size}개가 의미 없음 (예: \"${vague.first().lines().first().trim()}\"). 의미 있는 메시지로.",
        )
    }

    private fun isVagueCommit(message: String): Boolean {
        val first = message.lines().firstOrNull()?.trim()?.lowercase() ?: return false
        return first.length < 5 || VAGUE_COMMIT_PATTERN.matches(first)
    }

    /**
     * production 코드가 추가/수정됐는데 테스트 변경이 0인 경우 경고.
     * 단순 doc/config-only PR은 제외(추가된 production 파일이 없으면 경고 없음).
     */
    private fun testCoverageNote(files: List<PullRequestFile>): ProcessNote? {
        val production = files.filter { isProductionFile(it.path) }
        if (production.isEmpty()) return null
        val testTouched = files.any { isTestFile(it.path) }
        if (testTouched) {
            return ProcessNote(Severity.LOW, "테스트가 함께 변경됨.")
        }
        return ProcessNote(
            Severity.MEDIUM,
            "production 코드 ${production.size}개 변경되었는데 테스트 변경 없음. 회귀 방지용 테스트 추가 권장.",
        )
    }

    private fun isTestFile(path: String): Boolean =
        path.contains("/test/") ||
            path.contains("/tests/") ||
            path.contains("/__tests__/") ||
            TEST_FILENAME_PATTERN.containsMatchIn(path.substringAfterLast('/'))

    private fun isProductionFile(path: String): Boolean {
        if (isTestFile(path)) return false
        // 명시적으로 코드 외 파일 제외 — 문서/설정만 바꾼 PR엔 테스트 노트 안 띄움.
        if (NON_PRODUCTION_PATTERN.containsMatchIn(path)) return false
        return true
    }

    private const val MIN_TITLE_LEN = 10
    private const val MIN_DESCRIPTION_LEN = 30

    // "wip", "fix", "update", "test", "tmp", "misc", "chore", "init", "."
    private val VAGUE_TITLE_PATTERN = Regex("^(wip|fix|update|updates|test|tmp|temp|misc|chore|init|todo|.{1,2})\\.?$")

    private val VAGUE_COMMIT_PATTERN = Regex("^(wip|fix|update|updates|test|tmp|temp|asdf|aaa+|\\.+)\\.?$")

    // {Foo}Test.kt, {Foo}Tests.kt, {foo}.test.{ts,tsx,js}, {foo}.spec.{ts,tsx,js}
    private val TEST_FILENAME_PATTERN = Regex("(Test|Tests|Spec)\\.(kt|java)$|\\.(test|spec)\\.[a-z]+$")

    private val NON_PRODUCTION_PATTERN = Regex(
        "(^|/)(README|CHANGELOG|LICENSE|\\.gitignore|\\.editorconfig)" +
            "|\\.(md|txt|adoc|rst|yml|yaml|json|toml|properties|gradle|gradle\\.kts)$",
    )
}
