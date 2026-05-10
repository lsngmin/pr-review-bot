package com.pbot.bot.domain.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * `@pawranoid verify` 트리거 중복 방어. 같은 review comment에 대해 동시 실행을 막는다.
 *
 * SHA 키가 아닌 comment ID 키 — review comment는 immutable 하므로 SHA 추적이 무의미.
 * 진행 완료 후 [release]로 풀어줘서 재시도(예: 사용자가 다시 결과를 요청)는 허용한다.
 *
 * 인메모리라 봇 재시작 시 초기화. 학습 단계엔 충분.
 */
@Service
class VerifyHistoryService {
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    fun tryClaim(commentId: Long): Boolean = inFlight.add(commentId)

    fun release(commentId: Long) {
        inFlight.remove(commentId)
    }
}
