package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ProcessNote
import com.pbot.bot.domain.model.Walkthrough

/**
 * [Walkthrough] 도메인 객체를 PR 메인 conversation 탭에 게시할 markdown으로 변환한다.
 *
 * 형식:
 * ```
 * ## 🐶 Pawranoid Walkthrough
 *
 * ### 📝 What changed
 * <intent>
 *
 * ### 📂 Files changed
 * | File | Type | Summary |
 * | Foo.kt | New | ... |
 *
 * ### 📋 Process notes      (notes가 있을 때만)
 * - **MEDIUM** — ...
 *
 * ### ⚠️ Risk highlights    (risks가 있을 때만)
 * - 🔴 HIGH — ...
 * ```
 *
 * 표 폭을 줄이기 위해 File 컬럼은 디렉토리를 떼고 파일명만 표시.
 * 같은 파일명이 다른 디렉토리에 있는 경우는 (이 프로젝트엔 거의 없으므로) 우선 무시.
 *
 * Process notes는 PR 자체(사이즈/제목/커밋/테스트)에 대한 결정적 평가로,
 * "Files changed" 다음에 위치 — 변경 목록을 본 직후 "이 PR 자체가 잘 만들어졌나"
 * 를 함께 보게 하기 위함.
 */
object WalkthroughBuilder {

    fun build(
        walkthrough: Walkthrough,
        processNotes: List<ProcessNote> = emptyList(),
    ): String = buildString {
        appendLine("## 🐶 Pawranoid Walkthrough")
        appendLine()

        appendLine("### 📝 What changed")
        appendLine(walkthrough.intent)
        appendLine()

        appendLine("### 📂 Files changed")
        appendLine("| File | Type | Summary |")
        appendLine("|------|------|---------|")
        walkthrough.files.forEach { file ->
            val name = file.path.substringAfterLast('/')
            appendLine("| `$name` | ${file.type.label} | ${file.summary} |")
        }
        appendLine()

        if (processNotes.isNotEmpty()) {
            appendLine("### 📋 Process notes")
            processNotes
                .sortedBy { it.severity.ordinal } // HIGH 먼저
                .forEach { note ->
                    appendLine("- **${note.severity.name}** — ${note.message}")
                }
            appendLine()
        }

        if (walkthrough.risks.isNotEmpty()) {
            appendLine("### ⚠️ Risk highlights")
            walkthrough.risks.forEach { risk ->
                val location = risk.location?.let { " (`$it`)" } ?: ""
                appendLine("- ${risk.severity.emoji} **${risk.severity.name}** — ${risk.description}$location")
            }
        }
    }
}
