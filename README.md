# 드라이브 케어 (Drive Care)

> 차에 타는 순간부터 내릴 때까지, 운전자가 묻기 전에 먼저 챙겨주는 Pleos Connect 차량용 앱

현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Connect (Android Automotive OS) 헤드유닛 앱

## 앱 소개

| 순간 | 기능 |
|---|---|
| 타기 전 (주차) | **내 차 홈**: 실사형 3D 차량(드래그로 회전, 세단/SUV·색상 선택)과 문·창문·전조등 상태, 주행 가능 거리, 퀵 컨트롤(문 잠금·공조·전조등·창문), 실내 온도 조절 |
| 출발 | **원터치 "출발 준비"**: 공조 22℃ · 창문 닫기 · 문 잠금 · 전조등을 한 번에. 이미 된 항목은 건너뜀 |
| 주행 중 | **휴식 코치**: 연속 운전 2시간 전에 휴게소 안내 (도착이 먼저면 생략)<br>**휴게소 경유지 추가**: 쉴 때가 되면 진행 방향 앞의 휴게소·졸음쉼터를 찾아 "10km 앞 대관령휴게소를 경유지로 추가할까요?"라고 묻고, "응" 또는 버튼 한 번으로 순정 내비 경로에 추가<br>**주행 가능 거리 체크**: 남은 경로 + 안전 여유와 비교해 충분 / 빠듯 / 부족 판단<br>**음성 브리핑·음성 명령**: "얼마나 남았어", "에어컨 켜 줘", "온도 22도" |
| 도착 | **하차 모드**: 창문·전조등·공조 정리, 잠기지 않은 문 안내, 주차 위치 기록 |

주행 중 화면은 핵심 문장 1개, 숫자 2개, 버튼 2개(브리핑·음성)만 표시하며 스크롤과 애니메이션이 없습니다. 상세 화면과 차량 제어는 주차 중에만 열립니다. 주행 여부는 `CarUxRestrictions`(Distraction Optimization)로 판단합니다.

## 사용한 Pleos Connect SDK

| SDK | 버전 | 사용 기능 |
|---|---|---|
| Vehicle | 2.3.2 | Odometer(트립 운전 시간·주행 가능 거리·누적거리), Fuel / EvBattery(잔량), Door(잠금 조회·제어), Light(전조등 조회·제어), HVAC(전원·온도), Window(위치·닫기) |
| NaviHelper | 3.0.5 | 경로 안내 상태, 남은 시간·거리, 목적지 이름·좌표, 현재 위치·진행 방향, 도착 이벤트, 경유지 추가(addWaypoint) |
| TextToSpeech | 3.3.1 | 브리핑·경고·루틴 결과 음성 안내 (ON_DEVICE) |
| SpeechToText | 3.3.1 | 음성 명령 (ON_DEVICE) |
| FusedLocation | 1.1.3 | 주차 위치 기록 |

각 SDK는 인터페이스 뒤에 분리되어 있고, Pleos 서비스가 없을 때는 Android 기본 TTS·음성 인식과 시연 데이터로 자동 전환됩니다.

## 실행 방법

### APK 설치

```bash
adb install apk/<팀명>_DriveCare_release.apk
```

### 시연 순서 (Pleos 서비스가 없는 기기에서는 자동으로 시연 모드)

1. **내 차** 탭에서 **출발 준비** 버튼 → 공조·창문·잠금·전조등이 출발 상태로 바뀌고 음성 안내
2. **드라이브 케어** 탭 → **시연: 강릉 가는 길 주행** → 30배속 가상 주행(약 80초)
   - 주행 화면 전환 → 주행 가능 거리 빠듯 경고 → 휴식 임박 안내 → 도착
3. 도착 후 하차 경고(뒷문 열림·전조등·연료) 확인 → **내 차** 탭의 **하차 모드**
4. 마이크 버튼 → "에어컨 꺼", "온도 23도", "얼마나 남았어" 등 음성 명령

Pleos 헤드유닛에서도 시연 모드로 보려면 `ProviderConfig.forceDemoDrive = true`로 빌드합니다.

### 소스에서 빌드

```bash
./gradlew testDebugUnitTest assembleDebug
```

릴리스(서명) 빌드는 저장소 루트에 `keystore.properties`(Git 제외)를 두거나 `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` 환경 변수를 설정한 뒤 실행합니다.

```bash
./gradlew exportContestApk -PteamName=<팀명>
```

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 대상 플랫폼 | Pleos Connect (Android Automotive OS, API 34 이미지) |
| 최소 / 타깃 SDK | minSdk 28 / targetSdk 34 / compileSdk 35 |
| 빌드 | JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, Jetpack Compose |
| 확인한 기기 | Galaxy Z Flip4 (SM-F721N, Android 16), 시연 모드 |
| CI | GitHub Actions: JVM 유닛 테스트 → 디버그 APK 빌드 |
| 권한 | Vehicle: `CAR_DRIVE`, `CAR_ENERGY`, `CAR_DOORS`, `CAR_LIGHTS`, `CAR_CLIMATE`, `CAR_WINDOWS`, `CONTROL_CAR_*` · Navi: `NAVI_ROUTE` · `TTS_SERVICE`, `STT_SERVICE`, `FUSED_LOCATION` · `CAR_DRIVING_STATE_3P` · 일반 기기 음성 인식용 `RECORD_AUDIO` |

- Pleos Connect Emulator에서는 기본 내비로 목적지 경로 안내를 시작하면 자동 음성 브리핑이 시작됩니다.
- Vehicle Control, TTS, STT, FusedLocation은 Playground에서 "준비 중"인 모듈이라 승인 전에는 호출이 거부될 수 있습니다. 이 경우 화면에 안내가 표시되고 폴백 동작을 사용합니다.

## APK 위치

`apk/<팀명>_DriveCare_release.apk` (서명된 release 빌드, 디버깅 비활성화)

## 구조

```text
MainActivity.kt          탭(내 차 / 드라이브 케어), 주행 화면, 음성·루틴 연결
ui/                      VehicleHome(차량 홈), CarModelView(glTF 3D 차량), Car3D(대체용 Canvas 차량), Theme(Pleos 디자인 토큰)
assets/models/           SUV·세단 × 화이트/그레이/블루 GLB
tools/export_car.py      3D 모델 분리·재색상화 스크립트
domain/                  DriveCare(휴식·주행 가능 거리·경고), RestStop(진행 방향 앞 휴게소 찾기), CarRoutines(출발/하차 루틴), VoiceCommand — 순수 Kotlin + JVM 테스트
data/                    Vehicle / Navigation / UxRestriction / TTS / VoiceInput / ParkingLocation Provider
                         (Pleos·AAOS 구현 + 폴백, DemoDrive 시연 데이터)
```

## 오픈소스 및 외부 구성 요소

| 구성 요소 | 라이선스 |
|---|---|
| Pleos Connect SDK (Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation) | Pleos Playground 이용 조건 |
| AndroidX Core / Activity / Lifecycle, Jetpack Compose (UI, Material 3, Material Icons Extended) | Apache License 2.0 |
| Kotlin 표준 라이브러리, kotlin-reflect, kotlinx.coroutines | Apache License 2.0 |
| JUnit 4 (테스트) | Eclipse Public License 1.0 |

| SceneView 2.3.0 (Filament 기반 glTF 렌더러) | Apache License 2.0 |
| 3D 차량 모델 "Generic passenger car pack" | CC BY 4.0 (아래 출처 표기) |
| 휴게소·졸음쉼터 위치 데이터 (`assets/rest_areas.json`) | © OpenStreetMap contributors, ODbL 1.0 |

### 휴게소 위치 데이터 출처 (ODbL)

휴게소·졸음쉼터 위치는 © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors 데이터(Open Database License 1.0)를 사용합니다. 2026-10-27 Overpass API로 대한민국 내 `highway=services`, `highway=rest_area` 중 이름에 "휴게소"·"쉼터"가 들어간 1,011곳(휴게소 768, 졸음쉼터 243)을 추출해 이름과 좌표만 앱에 넣었습니다.

### 3D 차량 모델 출처 (CC BY 4.0)

This work is based on "[Generic passenger car pack](https://sketchfab.com/3d-models/generic-passenger-car-pack-20f9af9b8a404d5cb022ac6fe87f21f5)" by [Comrade1280](https://sketchfab.com/comrade1280) licensed under [CC-BY-4.0](http://creativecommons.org/licenses/by/4.0/).

변경 사항: 팩에서 SUV·세단만 분리, 실제 크기로 축척·정렬, 도장 텍스처를 화이트/그레이/블루로 재색상화(`tools/export_car.py`). 원작자 설명에 따르면 실제 차량과 유사성이 없는 범용 모델이며, 현대자동차그룹의 로고·서체·브랜드 에셋은 사용하지 않습니다. 앱 안의 드라이브 케어 탭 하단에도 출처를 표시합니다.
