package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ReviewComment
import com.pbot.bot.domain.model.ReviewIssue

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
                append("\n\n```suggestion\n")
                append(issue.suggestion)
                if (!issue.suggestion.endsWith("\n")) append("\n")
                append("```")
            }
        }
        return ReviewComment(
            path = actualPath,
            line = issue.line,
            startLine = issue.startLine,
            body = body,
        )
    }
}
