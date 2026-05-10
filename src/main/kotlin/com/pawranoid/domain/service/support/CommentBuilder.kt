package com.pawranoid.domain.service.support

import com.pawranoid.domain.model.ReviewComment
import com.pawranoid.domain.model.ReviewIssue

/**
 * [ReviewIssue] 를 GitHub에 게시할 [ReviewComment] 로 변환한다.
 * suggestion 이 있으면 마크다운 ` ```suggestion ... ``` ` 블록을 코멘트 본문에 합쳐서
 * GitHub의 "Apply suggestion" UX가 동작하도록 한다.
 */
object CommentBuilder {

    fun build(issue: ReviewIssue, actualPath: String): ReviewComment {
        val body = buildString {
            append(issue.comment)
            if (!issue.suggestion.isNullOrBlank()) {
                val fence = chooseFence(issue.suggestion)
                append("\n\n").append(fence).append("suggestion\n")
                append(issue.suggestion)
                if (!issue.suggestion.endsWith("\n")) append("\n")
                append(fence)
            }
        }
        return ReviewComment(
            path = actualPath,
            line = issue.line,
            startLine = issue.startLine,
            body = body,
        )
    }

    /**
     * suggestion 본문에 ``` 시퀀스가 있으면 우리가 감싸는 fence가 깨진다.
     * CommonMark 규칙: 닫힘 fence는 여는 fence와 같거나 더 적은 backtick으로는 닫지 못함.
     * 본문에서 가장 긴 backtick 시퀀스보다 1개 더 긴 fence를 사용한다 (최소 3개).
     */
    private fun chooseFence(content: String): String {
        val longestBacktickRun = Regex("`+").findAll(content)
            .maxOfOrNull { it.value.length } ?: 0
        val length = maxOf(3, longestBacktickRun + 1)
        return "`".repeat(length)
    }
}
