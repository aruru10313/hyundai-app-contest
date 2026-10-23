# PROGRESS

최종 업데이트: 2026-10-23 (Step 3: Mock 기반 MVP)

> 이 문서는 "확인된 사실"과 "확인하지 못한 것"을 구분해서 기록한다.
> 추측은 쓰지 않고, 확인 못 한 항목은 `UNVERIFIED`로 표시한다.

---

## 0. 조사 출처

- Pleos Playground: https://pleos.ai/playground
- 공식 문서: https://document.pleos.ai (한국어)
- 문서 전체 URL 목록: https://document.pleos.ai/sitemap.xml (4,314개)
- `AGENTS.md`: **이번에도 없음** (로컬 없음, 원격 GitHub Contents API 404)

조사에 사용한 원문 페이지는 아래 각 항목에 URL로 표기했다.

---

## 1. Pleos Connect SDK 설치 방법

### 확인됨

- SDK 구성 모듈은 5개다. (LLM은 공식 모듈 목록에 없음)
  - Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation
  - 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
  - `settings.gradle(.kts)`의 `dependencyResolutionManagement.repositories`에 추가.
  - 문서상 **저장소 접근 토큰/인증 절차는 없음** (공개 저장소).
  - 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk
- 문서에 명시된 의존성 좌표:
  - `implementation("ai.pleos.playground:NaviHelper:3.0.5")`
  - `implementation("ai.pleos.playground:TextToSpeech:3.3.1")`
  - `implementation("ai.pleos.playground:SpeechToText:3.3.1")`
  - (Vehicle / FusedLocation은 문서에 "Importing" 페이지가 없고, Maven 아티팩트로 존재 확인:
    `Vehicle:2.3.2`, `FusedLocation:1.1.3`)
- 개발 환경 요구사항 (공식):
  - Minimum Android SDK: **API 28 이상** (minSdk 28. 사이버 보안 요건 때문이라고 명시)
  - Gradle **8.0 이상**
  - 개발 언어: Java, Kotlin
  - Pleos Connect Emulator **2.1.0**
  - 출처: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- 앱 등록(자격 증명)이 필요한 경우:
  - 서버 기반 TTS/STT(HYBRID, SERVER 모드)는 Pleos Playground `My Project`의
    **Client ID / Client Secret**으로 `registerApp()` 호출 필요.
  - ON_DEVICE 모드는 앱 등록 불필요.
  - 출처: .../TextToSpeech/app-registration, .../TextToSpeech/intro
- 프로젝트 생성 시 `프로젝트 ID(Key)`를 발급하며, 앱 심사 제출 때 App Market Console에 입력해야 한다.
  - 출처: https://document.pleos.ai/docs/pleos-only/create-my-project

### 중요: SDK 사용 신청 상태 (현재 문서 기준)

- `Vehicle State`와 `NaviHelper`는 **신청 없이 바로 사용 가능**.
- `FusedLocation`, `TextToSpeech`, `SpeechToText`, `Vehicle Control`은 **"준비 중(Coming Soon)"** 상태로
  **신청 버튼 자체가 없음**.
  - 출처: https://document.pleos.ai/docs/pleos-only/create-my-project
- 즉, Maven 아티팩트는 받을 수 있지만 **현재 공식 절차로는 TTS 사용 승인을 받을 수 없다.**
  이 프로젝트의 핵심(TTS)에 직접 영향을 주는 리스크다. (아래 "막힌 점" 참조)

### 확인 못 함 (UNVERIFIED)

- `compileSdk` 요구/허용 범위. 문서는 에뮬레이터 API 34만 언급하고 앱의 compileSdk는 명시하지 않음.
- Vehicle / FusedLocation의 공식 Gradle 좌표 표기(문서 페이지 부재). Maven 아티팩트 존재만 확인.
- Maven 저장소가 정말 인증 없이 접근 가능한지는 문서 기준이며, CI에서 실제 다운로드로 검증 필요.
- `registerApp()`의 정확한 시그니처/인자.
- LLM SDK의 설치/인증 방법 (공식 문서에 없음).

---

## 2. 에뮬레이터 실행 환경 요구사항

### 확인됨

- Android Studio **최신 버전** 필요.
- AVD 구성:
  - Form Factor: **Automotive**
  - System image: **Pleos Connect v2.1.0 (API 34)**
  - **RAM 4 GB**
  - **Cold boot**
  - 실행은 Android Studio Device Manager에서.
- Pleos Connect Emulator 시스템 이미지 URL (공개, 로그인 불필요):
  - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
  - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  - Android Studio > SDK Manager > SDK Update Sites에 추가하는 방식.
- Virtual Console:
  - 에뮬레이터의 차량 상태/이벤트를 도메인별로 바꿔볼 수 있는 **로컬 웹 콘솔**.
  - Emulator 2.1.0 이상 필요.
- 폼 팩터 테스트:
  - `Host Display Bridge`(Go 1.22 이상) + `FormFactorConsole` 앱으로 가상 디스플레이 생성.
  - 8095/8096 포트 사용. `127.0.0.1:8096`에서 로컬로 동작.
  - 12.9" Portrait, 14.6" Landscape 등 가상 디스플레이 지원.
  - 에뮬레이터를 **별도 창**으로 실행해야 함(Headless/내장 창 불가).
  - 출처: https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide
- Pleos 디스플레이 타입: Connect-S(17", 14.6", 12.9"), Connect-W(Wide/Extended), Connect-WF(Waterfall).
  - 출처: https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout

### 확인 못 함 (UNVERIFIED)

- Pleos 문서 차원의 **호스트 OS/CPU/메모리 최소 사양**은 없다.
  "Android Emulator를 실행할 수 있는 시스템 요구 사항"을 충족하라고만 하고,
  세부 사양은 Android Studio/Android Emulator 공식 요구사항으로 위임한다.
  (Pleos 문서에 Windows/macOS/Linux 버전, 코어 수, RAM 용량 표 없음)
- **클라우드/웹 테스트 환경(원격 디바이스 팜)은 문서에서 확인되지 않음.**
  Virtual Console과 FormFactorConsole은 모두 로컬 에뮬레이터에 붙는 도구다.
- 실제 차량(양산차) 테스트 절차 및 신청 방법.
- 에뮬레이터에서 TTS/STT의 서버 모드가 실제로 동작하는지 (문서상 "준비 중").

---

## 3. NaviHelper SDK: 남은 주행 시간/거리/목적지

### 확인됨

- 진입점: `NaviHelper(Context)`, `initialize()`, `release()`, `addListener(...)`
- 잔여 시간/거리를 얻는 경로 (v3.0.5):
  1. `NaviHelperEventListener.onDrivingInfo(DrivingInfo)`
     - `DrivingInfo.destination: DrivingState`
     - `DrivingState`: `name`, `duration`, `distance`, `remainDestinationChargingValue`, `maxChargingPower`, `targetSoc`, `chargerCount`, `chargingPlan`
  2. `getDestinationInfo()` → `DestinationInfo`
     - `remainTime`, `remainDistance`, `name`, `address`, `latitude/longitude` 등
     - 값은 직접 반환되지 않고 `onDestinationInfo` 콜백으로 수신.
  3. v3.0.5에서 `onTripInfo` 콜백 추가.
- 목적지 설정/확인/취소: `requestRoute(RouteInfo)`, `getDestinationInfo()`, `cancelRoute()`
  - 결과는 `onRouteStarted`, `onRoutingCompleted`, `onDestinationChanged`, `onError` 콜백으로 확인.
  - 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations
- 내비 상태: `NavigationStatus` = `INACTIVE`, `INITIALIZING`, `ROUTE_DRIVE`, `FREE_DRIVE`
  - `onNavigationStatus(NavigationStatus)` (v3.0.5에서 타입 변경)
- v3.0.4 → 3.0.5 변경점:
  - `DestinationInfo`/`WaypointInfo`의 `totalDistance/totalTime` → **`remainDistance/remainTime`** 로 필드명 변경.
  - 좌표 생성자 인자 순서 변경(`longitude, latitude` → `latitude, longitude`).
  - 출처: https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015
- 권한(공식 샘플 기준): `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`,
  `NAVI_CUSTOM_MAP`, `NAVI_CUSTOM_ROUTE`, `NAVI_CUSTOM_ETC`
- 전제: **Pleos 기본 내비가 경로 안내 중이어야** 잔여 시간/거리가 나온다.
  (NaviHelper는 Pleos 내비 서비스와 연동하는 API이지, 독립 ETA 계산 API가 아님)

### 확인 못 함 (UNVERIFIED)

- `duration`, `distance`, `remainTime`, `remainDistance`의 **단위**(초/미터로 추정)와
  갱신 주기. API Reference 페이지에는 필드 이름만 있고 설명/단위가 없음.
  - 예: .../driving-state/duration, .../destination-info/remain-time (본문이 제목 한 줄뿐)
- `DrivingState`가 "목적지까지"인지 "다음 경유지까지"인지에 대한 문서 설명.
- `onTripInfo`의 `TripInfo` 필드 구성.
- 내비 미사용(FREE_DRIVE) 상태에서 잔여 시간 필드가 null인지 0인지.
- 에뮬레이터에서 NaviHelper가 실제 값를 주는지 (실기 검증 필요).

---

## 4. Gleo AI SDK: TTS / STT / LLM

### 먼저 용어 정리 (확인됨)

- **Gleo Interface**는 TTS/LLM API가 아니다.
  앱이 정의한 function call을 LLM이 생성하고 앱이 응답하는 **음성 명령 연동 스키마(function calling)** 이다.
  OpenAI function calling과 유사하며, 앱은 JSON `App Schema`를 등록해야 한다.
  - 출처: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts
  - 문서 스스로 "현재 가이드는 개발이 진행됨에 따라 지속적으로 개선 예정"이라고 표시.

### TTS (확인됨)

- `ai.pleos.playground.tts.TextToSpeech(context, Mode)`
- 모드 3종:
  - `HYBRID` (기본): 서버 우선, 네트워크 실패 시 On-device 자동 전환. 앱 등록 필요.
  - `SERVER`: 서버만. 네트워크 + 앱 등록 필요. 실패 시 오류.
  - `ON_DEVICE`: 차량 On-device 엔진. **네트워크 불필요, 앱 등록 불필요.**
- 호출 순서: 생성 → `initialize()` → `addEventListener()` → (필요 시 `setLocale()`) →
  (서버 모드면 앱 등록 + `OnServerConnectionListener.onConnected()`) → `speak()` → `stop()` → `release()`
- `speak(text, isFinal)`:
  - `isFinal = true`(기본): 텍스트를 하나의 완결된 요청으로 처리.
  - `isFinal = false`: 스트리밍 조각. 마지막 조각만 `true`로 보내 문장을 완성.
- `stop()` 호출 시 `onStop()` 콜백.
- 이벤트: `onReady`, `onStart`, `onUpdatedRms(rms)`, `onDone`, `onStop`, `onError(ErrorType)`
- 오류 타입: `AUDIO_ERROR`, `INVALID_REQUEST_ERROR`, `MIC_ERROR`, `NETWORK_ERROR`,
  `NOT_SUPPORTED_API`, `ON_DEVICE_ENGINE_ERROR`, `PERMISSION_DENIED`, `SERVER_INTERNAL_ERROR`,
  `TIMEOUT`, `UNAUTHENTICATED`, `UNAVAILABLE_LANGUAGE`, `UNKNOWN`
- 권한: `pleos.car.permission.TTS_SERVICE`
- 출처: .../TextToSpeech/intro, .../TextToSpeech/start-and-stop, .../TextToSpeech/event-listeners,
  .../TextToSpeech/error-code, .../TextToSpeech/declaring-permissions

### STT (확인됨)

- `ai.pleos.playground.stt.SpeechToText(context, Mode)` — 모드 3종(HYBRID/SERVER/ON_DEVICE) 동일.
- 순서: 생성 → `initialize()` → `addListener(ResultListener)` → `setLocale()` → (서버 모드 앱 등록) →
  `request()` → `stop()` → `removeListener()` → `release()`
- STT는 텍스트 변환만 하며, 실제 동작 실행은 Gleo Interface가 담당.
- 권한: `pleos.car.permission.STT_SERVICE`
- 출처: .../SpeechToText/intro, .../SpeechToText/declaring-permissions

### LLM (확인 못 함 — 사용 비권장)

- 공식 SDK 모듈 목록(Overview)과 릴리즈 노트의 SDK 버전 표에 **LLM이 없다.**
  릴리즈 노트 버전 표: Vehicle 2.3.2, NaviHelper 3.0.5, TTS 3.3.1, STT 3.3.1, FusedLocation 1.1.3.
- 문서에는 `LLM` API Reference 페이지만 존재:
  `LLM/entry`, `LLM/.../generate-content`, `LLM/.../register-app` — **본문이 제목 한 줄뿐**.
- Maven에는 `ai.pleos.playground:LLM:2.1.3.2`가 존재하고, 바이트코드상
  `LLM(context, Mode)`, `initialize()`, `generateContent(...)`, `registerApp(...)`가 있음.
- 즉 **비공식/미출시 모듈**로 판단. 이번 출품작의 핵심 의존성으로 쓰면 안 된다.

### 제약 (길이/호출 횟수/오프라인)

- 확인됨: `ON_DEVICE` 모드는 **오프라인 동작 가능**(네트워크 불필요).
- 확인 못 함: 입력 텍스트 **최대 길이**, 호출 횟수/쿼터, 속도 제한, 스트리밍 청크 크기 제한,
  동시 요청 수. 문서 어디에도 수치가 없다.
- 확인 못 함: `speak()` 1회 최대 문자 수, 장문을 나눠 보낼 때의 권장 크기.

### 현재 사용 가능 여부 (중요)

- TTS/STT는 My Project에서 **"준비 중"** → 사용 신청 불가 (2026-10-23 문서 기준).
- 따라서 "TTS로 동화를 들려준다"는 핵심 기능은 **현재 공식 경로로는 승인 불가**.
  - 대안 1: Android 표준 `TextToSpeech` 엔진 (Pleos TTS 아님, 품질/차량 오디오 정책 미확인)
  - 대안 2: 미리 생성한 오디오 파일 재생 (네트워크/승인 불필요, 데모 안정성 높음)
  - 대안 3: Pleos TTS가 "사용 가능"으로 전환되기를 기다림 (일정 불확실)

---

## 5. Vehicle SDK: 앱이 읽을 수 있는 차량 상태

### 확인됨

- 도메인 목록(문서 기준):
  `Brake`, `CarInfo`, `Display`, `Door`, `DrivingMode`, `EvBattery`, `Fuel`, `HVAC`,
  `Light`, `Odometer`, `Safety`, `Seat`, `SideMirror`, `Steeringwheel`, `Tire`, `Toll`,
  `TurnSignal`, `Window`, `Wiper`
  - 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- `DrivingMode`: 드라이브 모드 상태, 크루즈 컨트롤 상태/목표 속도 조회
  (`getCurrentDrivingModeState`, `getCruiseControlState`, `getCruiseControlTargetSpeed`, register/unregister)
- `Odometer`: 총 주행거리, 트립 주행거리/시간 조회
- `CarInfo`: VIN 조회 (`getVINInfo`), 기존 `getCRNInfo`는 deprecated
- 조회는 비동기(`onComplete`/`onFailed`)이며 `initialize()`/`release()` 필요.
- 권한 예: `CAR_DRIVE`(DrivingMode, Brake, Odometer), `CAR_INFO`, `CAR_ENERGY`, `CAR_SAFETY` 등.
  - 접두사 규칙: 조회 `CAR_*`, 제어 `CONTROL_CAR_*`
  - 출처: .../Vehicle/vehicle-permission
- **속도(Vehicle Speed)는 사용할 수 없다.**
  - 릴리즈 노트 v2.3.2 Shutdown API에 `VehicleSpeedListener`와 `onSpeedUpdated` **삭제**가 명시됨.
  - 출처: https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015
- **기어(P/R/N/D) 조회 API는 문서에 없다.**
  - Virtual Console의 Motion > Gear Selection은 **테스트용 상태 주입** 기능이지 앱 API가 아니다.
  - SDK 바이트코드에 Gear 관련 클래스가 있었으나 문서화된 도메인 목록에는 없음 → 사용 비권장.

### 주행/정차 상태를 판단할 수 있는 공식 수단

- **CarUxRestrictions (AAOS 표준)**
  - `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged()`
  - `isRequiresDistractionOptimization()` + `getActiveRestrictions()` 플래그로 판단.
  - Pleos 문서가 앱에 이 연동을 **의무적으로** 요구.
  - 출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- **NaviHelper `NavigationStatus`** (`ROUTE_DRIVE` 여부) — 내비 주행 상태 판단 보조.
- Vehicle SDK의 시동/기어/속도로 "주행 중"을 직접 판별하는 API는 확인되지 않음.

### 확인 못 함 (UNVERIFIED)

- `checkXXXCapabilities`가 에뮬레이터에서 각 도메인에 대해 어떤 값을 주는지.
- `CAR_DRIVE` 권한 승인 절차(App Market 사전 협의) 실제 소요 기간.
- `Safety` 도메인의 경고(Warning) 항목 전체 목록.
- `DrivingMode`의 `DrivingModeType` 값이 실제 차량에서 어떻게 매핑되는지.

---

## 6. 앱 디자인 가이드 / 주행 중 UI 제한

### 확인됨

- 카테고리: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business,
  Music/Audio, **Kids**, Social/Communication
  - 우리 앱은 `Kids` 또는 `Entertainment`/`Music/Audio` 성격.
  - 출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- **DO(Distraction Optimization) 미선언 앱은 주행 중 사용 불가.**
  - Parking: 사용 가능 / Drive: 사용 불가
  - 출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- 주행 규제는 `CarUxRestrictions`로 수신하고, 앱이 기능별로 직접 UX 제한을 적용해야 한다.
  - 예: `UX_RESTRICTIONS_NO_VIDEO`면 동영상 재생 중지.
  - 지역/기능별 허용 범위는 플랫폼이 내려주는 `ActiveRestrictions`를 그대로 반영.
  - UX 규제 피그마 사양서는 Pleos Playground에 비공개.
- 디자인 원칙:
  - 시선 분산 최소화, 1~2초 안에 이해 가능한 정보만 노출.
  - **한 손 조작** 가능해야 하며 양손 조작 유도 금지.
  - 시스템 응답은 **0.25초 이내**, 지연 시 진행 상태 표시.
  - 주행 중 동영상/애니메이션/자동 스크롤 등 시선 유도 요소 금지.
  - 앱은 **Landscape / Portrait 둘 다** 대응해야 함(가로 전용 게임 등 예외 제외).
  - 최소 폰트 **12sp**, 본문은 1.5배 행간, 실제 픽셀 최소 높이 18px 이상 권장.
  - 출처: .../driver-distraction-guideline, .../docs-design/Introduction/intro,
    .../docs-design/getting-started/layout, .../docs-design/foundations/typography
- 색상은 Pleos 색상 토큰(Grayscale/Alpha/Static/System) 사용 권장.
  - 출처: .../docs-design/foundations/colors

### 확인 못 함 (UNVERIFIED)

- **최소 터치 타깃 크기(dp)** — 기본 버튼 문서에는 상태/속성만 있고 크기 규격이 없음.
  - 출처: .../docs-design/components/actions/basic-button
- Kids 카테고리 앱의 주행 중 허용 범위(오디오만 허용인지, 정지 화면 허용인지).
  - 카테고리 문서는 Music/Audio, Maps/Navigation, Mobility Services 예시만 제공.
- 한국 지역의 기능별 제한 표 전체(문서에 북미/유럽/그외 지역 표가 있으나,
  표의 열이 텍스트 추출 시 유실됨). 앱 심사 전 원문 표 재확인 필요.
- DO 선언의 구체적 방법(매니페스트/메타데이터 키) — 문서는 "선언"만 언급.
- HUD/클러스터/OSD 영역 사용 규격.

---

## 7. 앱 마켓 등록 / 패키징 요구사항

### 확인됨

- 심사는 **Pleos App Market Console**에서 신청.
- 패키지 파일: **APK 형식, 최대 600 MB** (AAB 언급 없음).
- 대상 플랫폼: **Connect** 또는 **InfoLINK** 선택.
- 필수 제출 항목:
  - 앱 관리명, 버전명, 출시 국가/지역, 카테고리, 연령 등급
  - 개인정보 처리, 위치정보 활용, 앱 접근 권한, SDK 추가 권한(프로젝트 ID)
  - 광고성 정보 여부
  - 언어별 앱 이름/간단 설명/자세한 설명, 개인정보처리방침 URL
  - 앱 아이콘: PNG/JPEG, **512×512 px, 최대 1 MB**
  - IVI 스크린샷: PNG/JPEG, 장당 8 MB, 최대 8개, 규격 2560×1380 / 1700×1212 / 840×121
  - 출시 노트
  - 고객 지원 정보(웹사이트/이메일/전화번호)
  - 심사 노트(선택, 최대 60 MB 첨부)
- 연령 등급은 **IARC** 심의를 거쳐야 하며 증빙 제출 필요.
- 앱 버전은 **제출/배포 때마다 반드시 변경**해야 하며, 미변경 시 심사 반려 가능.
- 보안 요구사항(위반 시 심사 거부 가능):
  - 민감정보 하드코딩 금지, Android Keystore 사용 권장
  - 저장 데이터 암호화, 로그/캐시 민감정보 금지
  - HTTPS + TLS 1.2 이상(1.3 권장)
  - 불필요 권한/컴포넌트 노출 최소화
  - **배포용 서명 인증서** 사용, 디버깅/WebView 원격 디버깅 비활성화
  - 공식 허용 API만 사용
- Vehicle SDK 퍼미션은 **Pleos App Market과 사전 협의/승인된 범위**에서만 사용 가능.
  미승인 퍼미션 선언/호출 시 심사 통과 불가.
  - 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- 출처(등록/보안/버전):
  - https://document.pleos.ai/docs/pleos-only/distribute/app-submission-guide
  - https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide
  - https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-version-management-policy

### 확인 못 함 (UNVERIFIED)

- `targetSdk` 요구 버전.
- APK 서명 방식(v2/v3), 난독화(R8) 요구 여부.
- 앱 아이콘 외 런처 아이콘/배너 규격.
- 심사 소요 기간, 수수료, 수익 배분 정책.
- Kids 카테고리 추가 심의 요건.
- InfoLINK 대상 앱의 추가 패키징 요구사항.

---

## 8. 이번 조사로 바뀐 설계 전제

- `minSdk`는 **23 → 28**로 변경해야 한다 (공식 요구사항).
- `compileSdk 35`는 문서에 근거가 없다. 에뮬레이터는 API 34. (빌드는 가능할 수 있으나 미확인)
- LLM SDK 의존은 **제거**하고, 동화 생성은 “규칙 기반 템플릿 + 선택적 외부 LLM(설정)”으로 간다.
- TTS는 **Pleos TTS 의존을 기본값으로 두지 않는다.**
  `TtsProvider` 인터페이스 아래 3개 구현을 두고 설정으로 전환:
  1. `PleosTtsProvider` (승인 가능해지면 사용)
  2. `AndroidTtsProvider` (데모 폴백)
  3. `PrebakedAudioProvider` (완전 오프라인 폴백)
- "주행 중" 판단은 속도/기어가 아니라 **CarUxRestrictions**를 1차 신호로 사용.
- UI는 Landscape/Portrait 둘 다, 텍스트 12sp 이상, 주행 중 애니메이션 최소화.

---

## 9. 기존 기록 (Step 1)

- 저장소(`aruru10313/hyundai-app-contest`) 클론, 문서 골격 생성: `README.md`, `PROGRESS.md`, `.gitignore`.
- CI 골격 생성: `.github/workflows/android.yml`
  - `repo-check`: 필수 문서 존재 확인.
  - `android-build`: JDK 17 + Android SDK + Gradle로 `testDebugUnitTest` → `assembleDebug` → APK 업로드.
  - `gradlew`가 없으므로 빌드 job은 조건부 skip. Step 2에서 wrapper 커밋 시 활성화.
- `.gitignore`에 Android/Gradle/IDE 산출물, `local.properties`, `*.keystore`, `*.jks` 포함.
- Actions 검증 완료: run `39900125052` 성공.
  - 1차 run `39899981347`은 job-level `if`에서 `hashFiles()` 사용으로 실패
    (`Unrecognized function: 'hashFiles'`). step-level 조건으로 옮겨 해결.
- 이 단계에서는 애플리케이션 코드를 작성하지 않음.

### 저장소 / 로컬 환경

- `AGENTS.md`는 원격/로컬 모두 없음 (2026-10-23 재확인, GitHub Contents API 404).
  - 따라서 CI 규칙은 프로젝트 브리프 기준으로 작성함.
- 로컬에는 안드로이드 빌드 환경이 없음: `java` 1.8, `gradle`/`adb` 없음.
  - `C:\Android\Sdk`에 `platforms;android-35`, `build-tools;34.0.0`/`35.0.0`,
    `platform-tools 37.0.1`, `cmdline-tools latest` 존재.
  - 에뮬레이터/시스템 이미지 없음.
  - JDK 17은 `C:\Users\hayul\jdks\jdk17` (API 확인용 `javap`에만 사용).
- Git: `main`, 원격 `aruru10313/hyundai-app-contest`, 최신 커밋 `79ecc21`, 작업 트리 clean.

---

## 10. UNVERIFIED (통합)

1. NaviHelper `duration`/`distance`/`remainTime`/`remainDistance`의 단위와 갱신 주기.
2. 에뮬레이터/실차에서 NaviHelper가 실제 값을 주는지.
3. TTS/STT의 텍스트 길이·호출 횟수·쿼터 제한.
4. TTS/STT "준비 중"이 언제 "사용 가능"으로 바뀌는지.
5. `registerApp()` 시그니처와 자격 증명 전달 방식.
6. LLM SDK의 공식 지원 여부(문서/릴리즈 노트에 없음).
7. Vehicle SDK의 속도/기어/시동 조회 가능 여부(속도 리스너는 삭제됨, 기어는 문서 없음).
8. 최소 터치 타깃 크기, Kids 카테고리 주행 중 허용 범위.
9. 한국 지역 주행 규제 표의 정확한 기능별 허용/제한.
10. DO 선언의 구체적 구현 방법.
11. `targetSdk`, 서명/난독화 요구사항.
12. `compileSdk 35` 사용 가능 여부.
13. Pleos Maven이 CI에서 인증 없이 접근 가능한지.
14. 클라우드/원격 테스트 환경 제공 여부(문서상 없음).

---

## 11. 막힌 점

- **TTS/STT가 "준비 중"**: 핵심 기능인 TTS를 Pleos SDK로 시연할 수 없다.
  → 폴백(Android TTS 또는 사전 생성 오디오) 없이는 "시연 가능" 요구를 충족할 수 없다.
  → 출품 전 Pleos Playground 계정에서 실제 상태를 직접 확인해야 한다(문서와 계정 상태가 다를 수 있음).
- **AGENTS.md 부재**: CI 규칙 원문 대조 불가.
- **로컬 빌드 불가**: 컴파일 오류는 push 후 GitHub Actions 로그로만 확인 가능.
- **에뮬레이터/시스템 이미지 미설치**: 로컬에 AVD가 없어 실제 동작 검증 불가.
- **LLM SDK 문서 부재**: 동화 생성을 LLM에 의존할 수 없음.

---

## 12. 다음 단계 (실행 보류)

사용자 확인 후 진행:

1. Step 2: Gradle wrapper + 앱 골격 + `StoryPlanner` 순수 Kotlin TDD.
   - `minSdk 28`, `compileSdk 34`(또는 35) 결정 필요.
   - CI에서 Pleos Maven 접근 검증.
2. TTS 전략 결정:
   - (A) Pleos TTS 코드를 작성하되 기본은 사전 생성 오디오로 데모
   - (B) Android 표준 TTS로 데모
   - (C) Pleos TTS 승인 상태를 먼저 확인
3. Pleos Playground 계정에서 TTS/STT 실제 신청 상태 확인 (사용자만 가능).
4. 에뮬레이터 설치 여부 결정 (로컬 검증을 할지, CI 빌드만 할지).

---

## 13. Step 3: 인터페이스와 Mock 기반 MVP

### 완료

- 저장소에 제공된 프로젝트 지침을 `AGENTS.md`로 추가.
- `EtaProvider` 인터페이스와 초 단위 카운트다운 `MockEtaProvider` 구현.
- `TtsProvider` 인터페이스와 Android 기본 한국어 TTS 구현.
- `StoryGenerator` 인터페이스와 `assets/stories.json` 기반 동화 로더 구현.
- 모험/동물/우주 주제의 전연령 동화 3편 추가 (각 5개 챕터, 추정 5분 분량).
- `StoryPlanner`가 ETA 버짓에 맞춰 일반/압축 챕터를 고르고 결말을 마지막에 배치하도록 확장.
- 큰 버튼으로 주제를 선택하고 ETA/재생 챕터를 보는 기본 Compose 화면 구현.
- 10분/5분/ETA 단축 시나리오를 포함한 순수 Kotlin 단위 테스트 추가.
- 로컬 JDK 17 + Android SDK에서 `testDebugUnitTest assembleDebug` 통과.
- JSON 파싱 및 동화 개수/챕터 분량 확인 완료.
- GitHub Actions run `39904083638`에서 JVM 테스트, APK 빌드 및 `app-debug-apk` 업로드 성공.

### 확인된 사실

- Pleos Maven 저장소에서 NaviHelper 3.0.5와 TextToSpeech 3.3.1 아티팩트를 내려받아 빌드할 수 있음.
- Pleos TTS API의 클래스/리스너 시그니처는 로컬 SDK 아티팩트에서 확인 가능.
- 현재 기본 시연 경로는 Mock ETA + Android TTS이며 앱 등록 자격 증명은 사용하지 않음.

### UNVERIFIED

- NaviHelper `remainTime` / `duration`의 단위가 초인지 여부. 따라서 SDK ETA는 `Flow<Int>` 초 값으로 연결하지 않음.
- 실제 Pleos 에뮬레이터/차량에서 NaviHelper 값 수신 및 Android 한국어 TTS 음성 설치 여부.
- Pleos TTS/STT 사용 신청이 현재도 준비 중인지, 그리고 해당 권한이 승인될 수 있는지.
- 챕터별 `estimatedSeconds`는 기획 추정치이며 실차 TTS로 발화 시간을 측정하지 않음.
- 연령대 선택은 인터페이스에 반영했으나 현재 JSON 이야기의 연령별 문안 차이는 없음.
- 공식 LLM SDK 및 호출 제약을 확인하지 못해 LLM 생성기는 추가하지 않음.

### 막힌 점

- 프로젝트 ID만으로 Pleos TTS/NaviHelper 접근 권한을 부여받는 것은 확인되지 않음. Project ID는 앱 프로젝트 식별 정보로만 취급하며 코드에 하드코딩하지 않음.
- Pleos TTS SDK를 기본 시연 경로로 활성화하려면 사용 가능 상태와 `TTS_SERVICE` 권한/앱 등록 절차를 확인해야 함.
- 정확한 ETA 맞춤 재생을 검증하려면 NaviHelper 시간 단위와 에뮬레이터/실차 테스트가 필요함.
- 본 Step 3을 마친 뒤 다음 단계(실제 Pleos SDK 연동)로 넘어가기 전 확인이 필요함..

---

## 14. 차량용 동화 UI 시제품

### 완료

- 기존 재생 기능과 ETA 표시를 유지하면서 앱 화면을 어두운 별밤 색상과 큰 글씨의 동화책 스타일로 재구성.
- 주제별 장면을 Canvas로 제작: 숲의 등불, 숲속 동물, 입체감 있는 행성.
- 3개 주제 선택과 하나의 큰 재생/일시정지 버튼으로 조작 단계를 단순화.
- 화면 비율을 확인해 landscape에서는 장면과 조작부를 좌우로, portrait에서는 위아래로 배치.
- README에 UI 시제품 방식과 범위를 명시.
- `testDebugUnitTest assembleDebug` 성공.
- ADB 연결 기기(SM-F721N, Android 16)에 설치 후 화면, 주제 전환, 재생/일시정지, ETA 감소를 확인. 강제 종료 및 앱 크래시는 관찰되지 않음.

### 확인된 사실

- 앱 장면은 Compose Canvas에서 그리는 2D 벡터 일러스트이며, 색상 그라데이션·겹친 언덕·빛 번짐으로 입체적인 그림책 분위기를 낸다.
- 현재 앱에 Unity/Sceneform 등 실시간 3D 엔진이나 3D 모델은 포함하지 않았다.
- 연결 기기 검증은 일반 스마트폰에서 수행했으며 Pleos 차량 UI 검증은 아니다.

### UNVERIFIED

- 실제 Pleos Connect 디스플레이의 해상도/화면 비율 및 차량 시스템 바와 함께 표시될 때의 최종 레이아웃.
- 주행 중 Kids/Entertainment 앱에서 장면 표시가 허용되는지와 지역별 승인 조건.
- Android TTS 소리가 차량의 올바른 오디오 존으로 출력되는지.

### 막힌 점

- Pleos 에뮬레이터 또는 차량이 현재 ADB 검증 대상에 연결되어 있지 않아 플랫폼별 UX 규제와 오디오 출력을 확인하지 못함.
- 시제품의 장면은 실시간 3D가 아니다. 추후 실시간 3D가 필요하다면 차량 성능·화면 정책·실제 수상작 제출 요구를 확인하고 그 단계에서 엔진/에셋 사용 범위를 결정해야 함..
