# 프로젝트: "도착시간 맞춤 AI 동화" 차량용 앱
현대자동차그룹 차량용 앱 개발 공모전 출품작. Pleos Connect(Android Automotive OS 기반)용 앱.
공모전: https://appdevchallenge.auto-hmg.io/
개발자 문서: https://pleos.ai/playground (SDK: Vehicle / NaviHelper / ADAS / Gleo AI / Fused Location)

## 컨셉
내비게이션의 남은 주행 시간(ETA)에 맞춰, 뒷좌석 아이를 위한 AI 동화/퀴즈가
도착 시점에 정확히 끝나도록 길이를 조절해 음성(TTS)으로 들려준다.
운전자는 화면을 보지 않고, 최소한의 음성/큰 버튼 조작만 한다.

## 절대 규칙
1. Pleos SDK/API는 추측 금지. 공식 문서를 먼저 읽고, 확인된 API만 사용한다.
   확인 못 한 부분은 코드에 "UNVERIFIED" 주석을 달고 나에게 보고한다.
2. 외부 의존(ETA, TTS, LLM, 차량 상태)은 인터페이스로 분리하고
   실제 구현(Pleos)과 Mock/Fallback 구현을 둘 다 만든다. 설정 하나로 전환 가능해야 한다.
3. "시연 가능"이 최우선. 완성도보다 end-to-end 데모가 먼저다.
4. 운전 안전: 주행 중 운전자 화면 조작 최소화, 큰 글씨, 음성 또는 버튼 1~2개.
   콘텐츠는 전연령 안전 콘텐츠만(폭력/공포/선정 금지).
5. 한 번에 한 단계만 진행하고, 끝나면 PROGRESS.md에 "완료/미확인/막힌 점"을 기록한 뒤 멈춰서 확인받는다.
6. 비밀값(Client ID/Secret, 토큰)은 코드·워크플로에 직접 쓰지 않는다. GitHub Secrets 또는 local.properties(gitignore)만 사용.
7. 커밋은 단계 단위로 작게.

## 빌드/CI 규칙 (GitHub Actions)
- 로컬 안드로이드 빌드 환경 없음. 모든 빌드는 .github/workflows/android.yml 로 한다.
- gradle wrapper(gradlew, gradle/wrapper/*)는 반드시 커밋한다.
- 푸시 후 `gh run list --limit 3` → `gh run view <run-id> --log-failed` 로 결과를 확인하고,
  실패하면 로그를 읽고 고쳐서 다시 푸시한다. 같은 원인으로 3번 연속 실패하면 멈추고 보고한다.
- 핵심 로직(StoryPlanner 등)은 순수 Kotlin으로 작성해 JVM 유닛 테스트로 검증한다.

## 기술 방향
- 언어: Kotlin, 빌드: Gradle. UI 방식은 Pleos 디자인 가이드를 문서에서 확인해 따른다.
- 구조: ui / domain(StoryPlanner) / data(EtaProvider, TtsProvider, StoryGenerator)
