package com.pawranoid.domain.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * PR별로 마지막으로 리뷰한 commit SHA를 기록한다.
 *
 * 같은 commit에 대해 `@pawranoid` 가 동시에 여러 번 트리거되어도 한 번만 실제 리뷰가 돌도록 막는 역할.
 * 새 commit이 push되면 SHA가 바뀌므로 자연스럽게 다시 리뷰가 가능해진다.
 *
 * 인메모리 ConcurrentHashMap이라 봇 재시작 시 초기화된다 — 학습 단계엔 충분하고,
 * 운영 단계에선 Redis/DB로 교체해야 한다 (재시작 후 중복 방지 못함).
 */
@Service
class ReviewHistoryService {
    private val state = ConcurrentHashMap<String, String>()

    /**
     * 해당 PR/SHA에 대해 리뷰 진행을 원자적으로 선점한다.
     *
     * `compute` 가 ConcurrentHashMap에서 lambda 실행 전체를 atomic 하게 보장하므로
     * 두 스레드가 같은 SHA로 동시에 호출해도 정확히 한 명만 true를 받는다.
     *
     * @return 이번 호출이 처음 선점한 경우 true, 이미 같은 SHA로 선점/완료된 경우 false
     */
    fun tryClaim(repo: String, number: Int, sha: String): Boolean {
        var claimed = false
        state.compute(key(repo, number)) { _, current ->
            if (current == sha) {
                current   // 이미 같은 SHA로 claim/완료 → 그대로 둠
            } else {
                claimed = true
                sha       // 다른 SHA(또는 처음) → 이번 호출이 선점
            }
        }
        return claimed
    }

    private fun key(repo: String, number: Int) = "$repo#$number"
}
