package com.pbot.bot.domain.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * PR별로 마지막으로 리뷰한 commit SHA를 기록한다.
 *
 * 같은 commit에 대해 `/review` 가 여러 번 트리거되어도 한 번만 실제 리뷰가 돌도록 막는 역할.
 * 새 commit이 push되면 SHA가 바뀌므로 자연스럽게 다시 리뷰가 가능해진다.
 *
 * 인메모리 ConcurrentHashMap이라 봇 재시작 시 초기화된다 — 학습 단계엔 충분하고,
 * 운영 단계에선 Redis/DB로 교체해야 한다 (재시작 후 중복 방지 못함).
 */
@Service
class ReviewHistoryService {
    private val lastReviewedSha = ConcurrentHashMap<String, String>()

    /**
     * 해당 PR에 대해 [sha] commit이 이미 리뷰됐는지 확인.
     */
    fun isAlreadyReviewed(repo: String, number: Int, sha: String): Boolean {
        return lastReviewedSha[key(repo, number)] == sha
    }

    /**
     * [sha] commit으로 리뷰가 완료되었음을 기록.
     */
    fun markReviewed(repo: String, number: Int, sha: String) {
        lastReviewedSha[key(repo, number)] = sha
    }

    private fun key(repo: String, number: Int) = "$repo#$number"
}
