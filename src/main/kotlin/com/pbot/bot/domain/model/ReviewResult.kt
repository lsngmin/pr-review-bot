package com.pbot.bot.domain.model

data class ReviewIssue(
    val path: String,
    val line: Int,
    val comment: String,
)

data class ReviewResult(
    val summary: String,
    val issues: List<ReviewIssue>,
)
