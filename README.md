# 도착시간 맞춤 AI 동화 (Arrival Tale)

내비게이션 ETA에 맞춰 뒷좌석 아이를 위한 AI 동화·퀴즈를 도착 시점에 맞춰 들려주는 Pleos Connect(AAOS) 차량용 앱입니다.
현대자동차그룹 차량용 앱 개발 공모전 출품작.

## 빌드 방법 (GitHub Actions)

로컬 안드로이드 빌드 환경을 사용하지 않습니다. 모든 빌드는 GitHub Actions에서 수행합니다.

1. `main` 브랜치에 푸시합니다.
2. GitHub **Actions → Android CI** 워크플로가 자동 실행됩니다.
3. 성공 시 **Artifacts → `app-debug-apk`** 에서 디버그 APK를 내려받습니다.
4. 수동 실행은 **Actions → Android CI → Run workflow** 를 사용합니다.

```bash
gh run list --limit 3
gh run view <run-id> --log-failed
```

> 현재는 Step 1(문서/CI 골격)이라 Gradle 프로젝트가 없습니다.
> `gradlew`가 커밋되는 Step 2부터 빌드 job이 자동으로 활성화됩니다.

## 구조 (예정)

```text
ui/      운전자 화면 (큰 글씨, 버튼 1~2개)
domain/  StoryPlanner — ETA에 맞춘 동화 길이 배분 (순수 Kotlin + JVM 테스트)
data/    EtaProvider / TtsProvider / StoryGenerator (Pleos 구현 + Mock/Fallback)
```
