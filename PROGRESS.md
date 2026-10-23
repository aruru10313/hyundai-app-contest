# PROGRESS

최종 업데이트: 2026-10-23 (Step 1)

## 완료

- 저장소(`aruru10313/hyundai-app-contest`)를 클론하고 원격 상태 확인 (빈 저장소, 원격 커밋 0개).
- 문서 골격 생성: `README.md`, `PROGRESS.md`, `.gitignore`.
- CI 골격 생성: `.github/workflows/android.yml`
  - `repo-check`: 필수 문서 존재 확인.
  - `android-build`: JDK 17 + Android SDK + Gradle로 `testDebugUnitTest` → `assembleDebug` → APK 아티팩트 업로드.
  - `gradlew`가 아직 없으므로 빌드 job은 조건부로 skip되고, Step 2에서 wrapper 커밋 시 자동 활성화.
- `.gitignore`에 Android/Gradle/IDE 산출물, `local.properties`, `*.keystore`, `*.jks` 포함.
- 이 단계에서는 애플리케이션 코드를 작성하지 않음.
- Actions 검증 완료: run `39900125052` 성공 (5s, `Repository check and Android build`).
  - 1차 run `39899981347`은 job-level `if`에서 `hashFiles()`를 쓴 것이 원인으로 즉시 실패
    (`Unrecognized function: 'hashFiles'`). step-level 조건으로 옮겨 해결.

## 확인된 사실

### 저장소 / 환경

- `AGENTS.md`는 원격 저장소에 없음 (GitHub Contents API 404, 로컬에도 없음).
  - 따라서 CI 규칙은 프로젝트 브리프(메시지)의 규칙을 기준으로 작성함.
- 로컬에는 안드로이드 빌드 환경이 없음: `java`는 1.8, `gradle`/`adb` 없음.
  - `C:\Android\Sdk`에 `platforms;android-35`, `build-tools;34.0.0`/`35.0.0`, `platform-tools 37.0.1`, `cmdline-tools latest`는 존재.
  - 에뮬레이터와 시스템 이미지는 없음.
  - JDK 17은 `C:\Users\hayul\jdks\jdk17`에 준비(빌드에는 사용하지 않음, API 확인용 `javap`에만 사용).

### Pleos SDK (공식 Maven 저장소에서 아티팩트/바이트코드로 확인)

- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (토큰 불필요)
- 확인된 아티팩트:
  - `ai.pleos.playground:NaviHelper:3.0.5` (의존: `navi-data:3.0.4`)
  - `ai.pleos.playground:TextToSpeech:3.3.1`
  - `ai.pleos.playground:LLM:2.1.3.2`
  - `ai.pleos.playground:Vehicle:2.3.2`
  - `ai.pleos.playground:FusedLocation:1.1.3`
- `ADAS` 아티팩트는 저장소 검색 결과에 없음.

### ETA (NaviHelper 3.0.5)

- `NaviHelper(Context)` → `initialize()` / `release()` / `addListener(NaviHelperEventListener)`
- `NaviHelperEventListener.onDrivingInfo(DrivingInfo)`
  - `DrivingInfo.destination: DrivingState`, `DrivingInfo.waypoints: List<DrivingState>`
  - `DrivingState`: `name`, `duration`, `distance`, `remainDestinationChargingValue`, `maxChargingPower`, `targetSoc`, `chargerCount`, `chargingPlan`
- `NaviHelperEventListener.onRouteStarted(RouteStartInfo)`
  - `RouteStartInfo`: `duration`, `distance`, `tollFare`, `name`, `remainDestinationChargingValue`
- `NaviHelper.getRouteStateInfo()`의 `RouteStateInfo`에는 잔여 시간/거리 필드가 없음
  (`termsAgreement`, `naviMode`, `isRouting`, `routeOption`, `remainDestinationChargingInt`, `remainWaypointChargingInt`, `trafficStatus`).
- `getDestinationInfo()` → `DestinationInfo`에 `remainTime`, `remainDistance` 존재.
- 필요한 권한(공식 샘플 기준): `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP`, `NAVI_CUSTOM_ROUTE`, `NAVI_CUSTOM_ETC`.

### TTS / LLM

- `ai.pleos.playground.tts.TextToSpeech(context, Mode)` — `Mode`: `SERVER`, `ON_DEVICE`, `HYBRID`
  - `initialize()`, `speak(text: String, flag: Boolean)`, `stop()`, `setLocale(Locale)`, `setAudioZoneId(Int)`, `release()`
  - `addEventListener(EventListener)`: `onStart`, `onReady`, `onDone`, `onError`, `onStop`, `onUpdatedRms`
  - AAR manifest: CAAS 서비스 `4.1.29` 이상 필요, `SPEECH_START` intent query.
- `ai.pleos.playground.llm.LLM(context, Mode)`
  - `initialize()`, `generateContent(prompt: String, ResultListener)`, `generateContentStream(...)`, `release()`
  - `ResultListener.onResponse(String, Boolean)`, `onError(String)`

### 빌드 스택 (공식 샘플 기준)

- AGP 8.8.x, Gradle 8.10.2, JDK 17, `compileSdk 35`, `minSdk 23`, Compose + Material3.

## UNVERIFIED

- `DrivingState.duration` / `distance`, `DestinationInfo.remainTime` / `remainDistance`의 단위(초/미터 추정) — 실제 기기·에뮬레이터 응답으로 확인 필요.
- 일반 AAOS 에뮬레이터에서 Pleos 서비스(NaviHelper/TTS/LLM)가 동작하는지 여부.
- Pleos 디자인 가이드의 구체적 UI 규격 — 문서 미열람. 공식 샘플과 동일하게 Compose/Material3 사용 예정.
- `TextToSpeech.speak(text, Boolean)` 두 번째 인자의 의미.
- TTS/LLM `SERVER` 모드의 `registerApp(...)` 인자 구성과 인증 필요 여부.
- `ADAS` SDK 사용 가능 여부.

## 막힌 점

- `AGENTS.md`가 저장소에 없어 CI 규칙 원문을 확인할 수 없음 → 브리프 기준으로 작성. 파일이 추가되면 대조 필요.
- Gradle 프로젝트가 아직 없어 실제 컴파일 검증 불가. Step 2에서 wrapper/앱 골격 커밋 후 CI 빌드로 검증 예정.
- 로컬 빌드 금지 규칙에 따라 컴파일 오류 확인은 push 후 Actions 로그로만 가능.
