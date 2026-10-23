package io.github.aruru10313.arrivaltale.domain

/**
 * ETA 에 맞춰 이야기 분량을 나누는 순수 Kotlin 로직 (JVM 테스트 대상).
 *
 * UNVERIFIED: Pleos NaviHelper 의 duration/remainTime 단위는 공식 문서에 없다.
 * 현재는 "초"로 가정하며, 실기 검증 후 변환 계층에서 보정한다.
 */
class StoryPlanner {

    /**
     * 전체 시간을 목표 조각 길이로 나눈 조각 수.
     * - 0 이하이면 0
     * - 나머지가 있으면 올림
     */
    fun segmentCount(totalSeconds: Int, targetSegmentSeconds: Int): Int {
        require(targetSegmentSeconds > 0) { "targetSegmentSeconds must be > 0" }
        if (totalSeconds <= 0) return 0
        return (totalSeconds + targetSegmentSeconds - 1) / targetSegmentSeconds
    }
}
