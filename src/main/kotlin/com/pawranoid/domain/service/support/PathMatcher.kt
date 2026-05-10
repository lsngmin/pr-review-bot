package com.pawranoid.domain.service.support

import com.pawranoid.infrastructure.github.PullRequestFile

/**
 * LLM이 응답한 path를 PR 변경 파일 목록과 매칭한다.
 *
 * LLM이 종종 path를 짧게(예: `Foo.kt`) 또는 정규화 안 된 형태로 주는데,
 * 정확 일치 → suffix 일치 순으로 시도해 hallucination이 아닌 실수만 보정한다.
 */
object PathMatcher {

    /**
     * [path] 와 매칭되는 파일을 [files] 에서 찾는다. 없으면 null.
     *
     * 정규화 규칙:
     * - 앞뒤 공백 제거
     * - 선행 슬래시 제거
     *
     * 매칭 우선순위:
     * 1. 정확 일치
     * 2. 디렉토리 경계까지 끝부분 일치 (예: "Foo.kt" → "src/main/kotlin/Foo.kt")
     */
    fun match(path: String, files: List<PullRequestFile>): PullRequestFile? {
        val normalized = path.trim().trimStart('/')
        if (normalized.isEmpty()) return null
        return files.find {
            val filePath = it.path.trim().trimStart('/')
            filePath == normalized || filePath.endsWith("/$normalized")
        }
    }
}
