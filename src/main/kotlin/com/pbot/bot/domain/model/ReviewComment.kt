package com.pbot.bot.domain.model

/**
 * 인라인 코멘트의 도메인 표현. PR Review로 게시될 라인별 피드백.
 *
 * GitHub API의 `side`, `position` 같은 디테일은 인프라 계층이 변환 시점에 채운다.
 * 도메인은 "어느 파일의 어느 라인에 무슨 말을 적을지"만 안다.
 *
 * @property path 코멘트 대상 파일 경로
 * @property line 새 파일 기준 라인 번호
 * @property body 한국어 피드백 본문
 */
data class ReviewComment(
    val path: String,
    val line: Int,
    val body: String,
)
