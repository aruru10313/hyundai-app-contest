# 드라이브 케어 (Drive Care)

순정 내비와 차량 상태를 읽어, 운전자가 먼저 묻기 전에 필요한 것을 알려주는 Pleos Connect(AAOS) 헤드유닛 앱입니다.
현대자동차그룹 차량용 앱 개발 공모전 출품작.

- **내 차 홈**: 회전 가능한 3D 차량(Compose Canvas, 외부 엔진/브랜드 에셋 없음)에 문 열림·전조등·창문 상태를 표시. 주행 가능 거리, 퀵 컨트롤(문 잠금·공조·전조등·창문), 실내 온도 조절, 차량 상태, 마지막 주차 위치.
- **원터치 루틴(전원 버튼)**: 주차 중 "출발 준비" = 공조 22℃ · 창문 닫기 · 문 잠금 · 전조등. 도착 후 "하차 모드" = 창문 · 전조등 · 공조 정리 + 잠기지 않은 문 안내. 이미 된 항목은 건너뜀.
- **음성 명령**: "출발 준비", "에어컨 켜 줘", "온도 22도", "창문 닫아", "얼마나 남았어" 등. 주행 중에는 문 열기 명령을 거부.
- **휴식 코치**: 연속 운전 시간(Odometer)과 도착 예정 시간(NaviHelper)을 비교해 "2시간마다 15분" 휴식 시점을 음성으로 안내. 도착이 먼저면 휴식 안내를 하지 않습니다.
- **주행 가능 거리 체크**: 주행 가능 거리 vs 남은 경로 + 안전 여유(최소 30km 또는 경로의 15%)를 비교해 "충분 / 빠듯 / 부족"을 판단. 연료·EV 배터리 잔량 15% 이하도 경고.
- **하차 체크**: 주차/도착 시 잠기지 않은 문과 켜진 전조등을 알려줍니다.
- **음성 브리핑**: 경로 안내가 시작되면 자동으로, 이후에는 버튼 한 번으로 남은 시간·휴식·주행 가능 거리를 읽어줍니다. 같은 경고는 한 번만, 더 심각해질 때만 다시 말합니다.

주행 중에는 핵심 문장 1개, 숫자 2개, 큰 버튼 1개만 보여주고(스크롤·애니메이션 없음), 주차 중에만 상세 대시보드를 보여줍니다.

## 사용하는 Pleos Connect SDK

| SDK | 용도 | 폴백 |
|---|---|---|
| Vehicle | 주행 시간·주행 가능 거리·연료/배터리·문·전조등·공조·창문 조회 및 제어 | DemoDrive 시연 데이터 |
| NaviHelper | 남은 시간/거리, 목적지, 도착, 경로 안내 상태 | DemoDrive |
| TextToSpeech | 브리핑·경고·루틴 결과 음성 안내 (ON_DEVICE) | Android TTS |
| SpeechToText | 음성 명령 (ON_DEVICE) | Android SpeechRecognizer |
| FusedLocation | 주차 위치 기록 | 시연 좌표 |

## 시연

Pleos 차량 서비스가 없는 기기(일반 Android, Automotive 아닌 환경)에서는 자동으로 시연 모드가 켜집니다.
주차 화면의 **"시연: 강릉 가는 길 주행"** 을 누르면 30배속 가상 주행(약 80초)이 재생됩니다:
연료 14% → 주행 가능 거리 빠듯 경고 → 2시간 휴식 임박 안내 → 도착 → 뒷문 열림·전조등 켜짐 하차 경고.
Pleos 헤드유닛에서도 강제로 시연하려면 `ProviderConfig.forceDemoDrive = true`.

## 빌드 방법 (GitHub Actions)

1. 브랜치에 푸시하거나 PR을 엽니다 (`main` 푸시와 PR에서 실행).
2. GitHub **Actions → Android CI** 워크플로가 JVM 유닛 테스트 → `assembleDebug`를 실행합니다.
3. 성공 시 **Artifacts → `app-debug-apk`** 에서 디버그 APK를 내려받습니다.

```bash
gh run list --limit 3
gh run view <run-id> --log-failed
```

## 구조

```text
MainActivity.kt          주행 화면(DrivingView) / 주차 대시보드(ParkedDashboard)
domain/DriveCare.kt      RestCoach · RangeCheck · DriveCareAdvisor · AnnouncementGate — 순수 Kotlin + JVM 테스트
data/                    VehicleStateProvider · NavigationStateProvider · UxRestrictionProvider · TtsProvider
                         (각각 Pleos/AAOS 구현 + DemoDrive 기반 Mock)
```
