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

현재 MVP는 Mock ETA, Android 기본 한국어 TTS, 로컬 JSON 동화 3편으로 동작합니다.
동화 선택 화면은 별도 3D 엔진 없이 Compose Canvas의 명암·그라데이션을 사용한 입체감 있는 장면과 큰 차량용 조작부로 구성했습니다.
NaviHelper의 ETA 단위와 Pleos TTS 사용 승인을 확인하기 전까지는 실제 SDK 연동을 기본 경로로 사용하지 않습니다.

최근 확인된 빌드: commit `29cb05c`, GitHub Actions run `39904083638` 성공.
성공한 run의 Artifacts에 `app-debug-apk`가 업로드됩니다.

## 구조

```text
app/src/main/java/.../MainActivity.kt  큰 버튼 기반 운전자 화면
domain/                                StoryPlanner — ETA에 맞춘 챕터 계획 + JVM 테스트
data/                                  EtaProvider / TtsProvider / StoryGenerator
app/src/main/assets/stories.json      오프라인 동화 라이브러리
```
