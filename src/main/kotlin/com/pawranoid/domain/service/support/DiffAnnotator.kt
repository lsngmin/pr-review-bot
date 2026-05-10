package com.pawranoid.domain.service.support

import com.pawranoid.infrastructure.github.PullRequestFile

/**
 * unified diff (GitHub `patch` 필드)을 LLM이 읽기 쉬운 형식으로 가공한다.
 *
 * 각 줄에 새 파일 기준 라인 번호를 prefix로 붙여 LLM이 직접 카운트하다 어긋나는 일을 방지하고,
 * 인라인 코멘트 검증에 사용할 유효 라인 번호 집합을 산출한다.
 *
 * 순수 함수 모음이라 별도 상태 없음. 테스트 직접 가능.
 */
object DiffAnnotator {

    /**
     * patch에 라인 번호 prefix를 붙여 가독성 좋은 형식으로 출력한다.
     *
     * 출력 예:
     * ```
     * === src/main/kotlin/Foo.kt ===
     * @@ -10,5 +14,8 @@
     * L14     class Foo(
     * L15 [+]     @Async
     * L16     fun bar() {
     * L--   [-]     val old = ...
     * ```
     */
    fun annotatePatch(file: PullRequestFile): String {
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
                    val match = HUNK_HEADER_NEW_START.find(raw) ?: continue
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
     * patch에서 인라인 코멘트 가능한 라인 번호 집합을 추출한다.
     * 추가된 라인(+)과 컨텍스트 라인 모두 포함. 삭제된 라인(-)은 제외.
     */
    fun lineNumbersInDiff(patch: String): Set<Int> {
        val lines = mutableSetOf<Int>()
        var newLine = 0
        for (raw in patch.lines()) {
            when {
                raw.startsWith("@@") -> {
                    val match = HUNK_HEADER_NEW_START.find(raw) ?: continue
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
                // 빈 줄/알 수 없는 prefix는 카운터 증가시키지 않음 (phantom line 방지)
            }
        }
        return lines
    }

    private val HUNK_HEADER_NEW_START = Regex("""\+(\d+)""")
}
