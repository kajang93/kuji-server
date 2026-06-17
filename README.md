# ⚙️ 이치방쿠지 (一番くじ) 백엔드 시스템

> **"행운의 데이터를 실시간으로 제어하는 강력한 코어"**  
> 본 프로젝트는 '이치방쿠지' 웹 서비스의 핵심 비즈니스 로직을 담당하는 Spring Boot 기반의 백엔드 서버입니다.  
> 확률 기반 추첨 엔진, 동시성 제어, 그리고 복잡한 물류 시스템을 안정적으로 처리합니다.

## 🎯 프로젝트 목표
- 웹 기반의 쿠지(뽑기) 서비스를 모바일 하이브리드 앱으로 전환하고, 사용자와 관리자 모두에게 최적화된 경험을 제공합니다.
- 토스페이먼츠(Toss Payments)를 통한 포인트 충전 및 결제 시스템을 구현합니다.
- 관리자 및 사업자 전용 기능을 강화하여 효율적인 운영과 정산을 지원합니다.

### 🏢 현재 비즈니스(운영) 진행 상황 및 보완 작업 (Pending Tasks)
- **사업자 등록 및 카카오 채널**: 사업자 등록 신청 후 심사 대기 중이며, 공식 카카오톡 채널 개설 진행 중
- **실제 SMS 인증 연동 및 어뷰징 방어 (완료)**: Aligo API 연동, DB 기반 하루 발송 횟수 제한(3회), 30일 경과 로그 자동 폐기 스케줄러 적용 및 비즈니스 로그(Slack) 알림 체계 구축 완료
- **소셜 로그인 (OAuth) (완료)**: 카카오, 네이버, 구글 로그인 연동 완벽 적용 및 예외 처리(랜덤 닉네임, 약관 동의) 완료
- **결제 및 포인트 시스템 (완료)**: Toss Payments 연동을 통한 실시간 카드 결제 및 쿠지 뽑기용 포인트 충전 시스템 완료
- **커뮤니티 및 어드민 기능 (완료)**: 유저 간 소통을 위한 커뮤니티, 1:1 고객센터 문의, 그리고 사업자/관리자 전용 대시보드(상품 관리, 공지사항) 구현 완료
- **클라우드 서버 배포 (완료)**: AWS S3 스토리지 전환 및 GitHub Actions를 통한 자동 배포 파이프라인(CI/CD) 구축 완료
- **AI 협업 컨벤션 (완료)**: 프로젝트 최상단 `.cursorrules` 도입으로 코드 스타일 가이드 및 한글 커밋 룰 시스템화 완료

#### 🚧 기술 부채 및 추후 고도화 예정 작업
- **JWT Refresh Token 도입**: 현재 사용자 편의성을 위해 Access Token 만료일을 임시 완화(1주일)해 두었으나, 보안 강화를 위해 Refresh Token을 도입하고 Access Token 수명을 단축할 예정
- **실시간 알림 (WebSocket) (완료)**: 기존 1분 주기 폴링(Polling) 방식에서 STOMP 기반 실시간 Push 방식으로 고도화 완료. 고급 상품(A~C상, Last One) 당첨 시 접속 중인 전체 유저에게 0.1초 만에 당첨 티커 알림 발송.

---

## 🛠️ 기술 스택 (Tech Stack)

| 항목 | 기술 | 상세 내용 |
|------|------|------|
| **Framework** | Spring Boot 3.x | 효율적인 REST API 및 의존성 관리 |
| **Language** | Java 25 | 최신 자바 문법 및 안정적인 런타임 |
| **Database** | PostgreSQL | 관계형 데이터 설계 및 JSONB/Enum 지원 |
| **Security** | Spring Security | JWT 기반의 무상태(Stateless) 인증 시스템 |
| **ORM** | Spring Data JPA | 객체 지향적 DB 조작 및 성능 최적화 |
| **Build Tool** | Gradle | 유연한 빌드 및 멀티 프로젝트 관리 지원 |

---

## ✨ 핵심 기능 (Core Features)

### 1. 실시간 확률 기반 뽑기 엔진 (Draw Engine)
- **확률 추첨**: 남은 수량 기반의 공정한 무작위 추첨 알고리즘 구현.
- **동시성 제어**: 다수의 사용자가 동시에 뽑을 때 발생하는 재고 문제를 방지하기 위한 데이터베이스 락(Lock) 전략 적용.
- **포인트 연동**: 뽑기 성공 시 실시간 포인트 차감 및 당첨 보상 적립.

### 2. 스마트 물류 및 배송 시스템 (Logistics)
- **보관함 시스템**: 당첨된 상품을 즉시 배송하지 않고 가상 보관함에 저장하여 합배송 지원.
- **배송 상태 관리**: `PREPARING` -> `SHIPPING` -> `DELIVERED`로 이어지는 상세 상태 추적.
- **자동화 기반 마련**: 택배사 API 연동을 고려한 운송장 번호 관리 시스템 구축.

### 3. 보안 및 사용자 인증 (Auth)
- **JWT 인증**: 모든 요청에 대한 토큰 검증 및 보안 필터 적용. (현재 사용자 편의성을 위해 Access Token 만료일을 **임시 완화(1주일)** 적용)
- **카카오 OAuth 2.0**: 소셜 로그인 연동을 통한 간편 가입 및 로그인.
- **Role-Based Access**: 일반 사용자, 사업자, 관리자별 상세 권한 제어.
- **계정 찾기 및 SMS 인증**: 휴대폰 번호 기반의 본인 인증(Aligo API 연동)을 통한 아이디 찾기 및 임시 비밀번호 발급 (실서버 연동 완료).

---

## 🚀 API 엔드포인트 목록 (API Specs)

### 🔐 보안 및 사용자 인증 (Auth)
| Method | Endpoint | Description |
|:---:|:---|:---|
| POST | `/api/members/signup` | 일반 회원가입 (이메일, 비밀번호, 닉네임, 휴대폰 등) |
| GET | `/api/members/check-email` | 회원가입 시 이메일 중복 확인 |
| POST | `/api/members/login` | 일반 이메일 로그인 (JWT 발급) |
| POST | `/api/members/send-sms` | 휴대폰 본인인증을 위한 SMS 인증번호 발송 |
| POST | `/api/members/find-id` | 인증번호 검증 후 가입된 이메일(아이디) 찾기 및 마스킹 반환 |
| POST | `/api/members/reset-password` | 가입 정보(이메일+휴대폰) 확인 후 임시 비밀번호로 초기화 |
| GET | `/api/admin/members` | (관리자 전용) 전체 가입 회원 리스트 조회 |

### 🛒 쿠지 및 뽑기 관련
| Method | Endpoint | Description |
|:---:|:---|:---|
| GET | `/api/kuji` | 운영 중인 쿠지 판 리스트 조회 |
| GET | `/api/kuji/{id}` | 특정 쿠지 판의 상품 상세 및 남은 수량 조회 |
| POST | `/api/kuji/{id}/draw` | 실시간 쿠지 뽑기 실행 (포인트 차감/당첨 기록) |
| GET | `/api/kuji/draw-history/me` | 나의 당첨 내역(보관함) 조회 |

### 🚚 배송 및 물류 관련
| Method | Endpoint | Description |
|:---:|:---|:---|
| POST | `/api/shipping` | 보관함 상품 선택 및 배송 신청 |
| GET | `/api/shipping/me` | 나의 배송 현황 및 이력 조회 |
| GET | `/api/shipping/admin` | (관리자) 전체 배송 요청 리스트 조회 |
| GET | `/api/shipping/seller` | (사업자) 소유한 쿠지 판의 배송 요청 리스트 조회 |
| PATCH | `/api/shipping/{id}/tracking` | (사업자) 운송장 등록 및 배송 시작 처리 (DrawHistory 상태도 SHIPPING으로 동기화) |
| GET | `/api/shipping/{id}/tracking` | 실시간 배송 추적 현황 조회 (SweetTracker 등 택배 API 연동) |
| PATCH | `/api/shipping/{id}/complete` | (구매자) 수령 및 배송 확정 처리 (운송장 등록 필수 유효성 검사 적용) |

### 🔔 알림 및 수신 설정 관련
| Method | Endpoint | Description |
|:---:|:---|:---|
| POST | `/api/notifications/token` | FCM 기기 토큰 등록 및 갱신 |
| DELETE | `/api/notifications/token/{deviceId}` | FCM 기기 토큰 삭제 (로그아웃/해제) |
| GET | `/api/notifications` | 내 인앱 알림 목록 조회 (페이징) |
| PATCH | `/api/notifications/{id}/read` | 특정 알림 읽음 처리 |
| PATCH | `/api/notifications/read-all` | 모든 알림 읽음 처리 |
| GET | `/api/notifications/settings` | 내 알림 수신 설정 조회 |
| PATCH | `/api/notifications/settings` | 알림 수신 설정 수정 |
### 💳 결제 및 포인트 충전 관련
| Method | Endpoint | Description |
|:---:|:---|:---|
| POST | `/api/kuji/{id}/payment/prepare` | 쿠지 뽑기 결제 준비 (세션 생성 및 orderId 발급) |
| POST | `/api/kuji/{id}/draw` | 쿠지 뽑기 실행 (결제 승인 + 추첨) |
| POST | `/api/points/charge/prepare` | 포인트 충전 준비 (결제 세션 생성 및 orderId 발급) |
| POST | `/api/points/charge/confirm` | 포인트 충전 승인 (토스 결제 확인 + 포인트 지급) |

### 📈 통계 및 대시보드 관련
| Method | Endpoint | Description |
|:---:|:---|:---|
| GET | `/api/statistics/admin/summary` | (관리자) 플랫폼 전체 요약 통계 조회 (총 가입자, 거래액 등) |
| GET | `/api/statistics/admin/daily-sales` | (관리자) 최근 N일간 일별 매출 차트 데이터 조회 |
| GET | `/api/statistics/seller/summary` | (사업자) 내 쿠지 판 요약 통계 조회 (진행 중인 쿠지, 누적 판매액 등) |
| GET | `/api/statistics/seller/daily-sales` | (사업자) 최근 N일간 내 쿠지 판 일별 매출 차트 데이터 조회 |

### 💬 커뮤니티 및 문의 관련
| Method | Endpoint | Description |
|:---:|:---|:---|
| GET/POST | `/api/posts` | 커뮤니티 게시글(자유/당첨인증) 목록 및 작성 |
| POST | `/api/inquiries` | 1:1 문의글 작성 |
| GET | `/api/admin/inquiries` | (관리자) 전체 1:1 문의 목록 조회 및 답변 작성 |

---

## 📅 프로젝트 진행 현황 (Progress)

### ✅ Phase 1: 인프라 및 보안 (완료)
- [x] DB 스키마 설계 및 JPA 엔티티 매핑 완료
- [x] JWT + Spring Security 인증 아키텍처 구축
- [x] 전역 예외 처리(`GlobalExceptionHandler`) 및 공통 응답 규격화

### ✅ Phase 2: 비즈니스 로직 고도화 (완료 ✨)
- [x] 실시간 재고 차감 및 뽑기 엔진 연동
- [x] 보관함(DrawHistory) 및 배송(Shipping) 시스템 구축
- [x] 카카오 소셜 로그인 연동 완료

### ✅ Phase 3: 커뮤니티 및 문의 시스템 (완료 ✨)
- [x] **커뮤니티(게시판)**: 당첨 인증, 자유 게시판 (CRUD 완료)
- [x] **실시간 티커**: 메인 페이지 당첨 내역 흐름 알림 API 완료
- [x] **1:1 문의 시스템**: 등록, 상세 조회 및 관리자 답변 기능 완료
- [x] **찜 목록(Wishlist)**: 쿠지판 찜하기 토글 및 나의 찜 목록 조회
- [x] **게시판 고도화**: AWS S3 연동을 통한 사진 업로드
- [x] **게시판 상호작용**: 게시글 좋아요, 찜하기, 댓글 달기 기능 구현

### 🏃 Phase 4: 배송 및 알림 시스템 고도화 (완료 ✅)
- [x] **실시간 알림**: FCM(Firebase Cloud Messaging) 연동 및 배송 단계/당첨/댓글/문의 답변 푸시 발송 트리거 연동 완료
- [x] **사업자 전용 기능**: 사업자용 배송 관리 대시보드 API (목록 조회 및 배송 시작/완료 처리) 완료
- [x] **알림 수신 설정**: 알림 전체 및 유형별 세부 차단 설정, 야간 푸시 수신 제한(22:00~08:00) 로직 구현 완료
- [x] **결제 시스템 (포인트 충전)**: 토스페이먼츠 연동을 통한 지갑(Wallet) 포인트 충전 구현 완료
  - [x] 특정 금액(3만/5만/10만) 이상 충전 시 보너스 포인트 자동 지급 기능 연동 완료
- [x] **배송 추적 시스템**: 외부 택배 API(SweetTracker 등) 연동을 통한 실시간 배송 현황 조회
- [x] **운영 도구**: 정산 관리 및 통계 대시보드 고도화
- [x] **어뷰징 방어 (보안)**: 알리고 SMS 매크로 발송 공격을 막기 위한 DB 기반 1일 3회 제한 로직 및 30일 경과 로그 자동 폐기 스케줄러 개발 완료
- [x] **이미지 최적화**: 로컬 파일 시스템에서 AWS S3 저장소 전면 전환 (압축 및 동반 삭제 로직 추가)

### 🚀 Phase 4: 클라우드 인프라 구축 및 배포 자동화 (완료 ✨)
- [x] **AWS S3 스토리지 연동**: 로컬 기반 이미지 스토리지를 AWS S3로 전면 전환 완료 (기존 더미 파일 청소 로직 포함)
- [x] **업로드 성능 최적화**: 프론트엔드 단 이미지 1차 압축(1MB 제한) 및 백엔드 Thumbnailator 2차 압축 이중화 적용
- [x] **배포 자동화 (CI/CD)**: GitHub Actions를 활용한 백엔드 및 프론트엔드 실서버 자동 배포 스크립트 작성 및 연동 완료

### 🔭 Phase 5: 앱 패키징 및 하이브리드 앱 전환 (진행 중)
- [x] **앱 껍데기(App Shell) 구축**: Capacitor를 이용한 하이브리드 모바일 앱 뼈대 및 빌드 세팅 완료
- [x] **네이티브 기능 연동 1**: iOS 대응 CORS 설정, ATS 허용 조치 및 터치 딜레이 버그 수정
- [x] **권한 설정 완료**: 사진첩 및 카메라 등 iOS/Android 네이티브 권한(`Info.plist`) 매핑 완료
- [ ] **자동화 베타 테스트**: Firebase Test Lab (Robo Test)을 활용한 AI 기반 무작위 UI 크래시 테스트 (런칭 직전)
- [ ] **스토어 배포 준비**: iOS(App Store) 및 Android(Play Store) 심사용 최종 패키징

### 🔮 Phase 6: 옵저버빌리티 및 운영 지능화 (예정)
- [ ] **구조화 로그 시스템**: Logback + MDC 기반 요청 추적 로그 설계 및 JSON 포맷 표준화
  - 결제 플로우, 뽑기 추첨, 배송 상태 변경 등 비즈니스 이벤트 감사 로그(Audit Log) 분리 적용
- [ ] **메트릭 수집 (Prometheus)**: Spring Boot Actuator + Micrometer 연동
  - API 응답 시간, 뽑기 TPS, 결제 성공/실패율, JVM 힙 사용량 등 핵심 지표 수집
- [ ] **모니터링 대시보드 (Grafana)**: Prometheus 데이터 소스 기반 실시간 시각화 대시보드 구축
  - 서버 상태, 트랜잭션 현황, 알림 발송 성공률 패널 구성
- [ ] **AI 기반 상태 보고**: 수집된 메트릭 및 로그 데이터를 AI(LLM)로 분석하여 운영 이상 징후 탐지 및 자동 보고서 생성
  - 이상 트래픽 감지, 결제 실패 패턴 분석, 주간 서비스 상태 요약 리포트 자동화

---

## 🧑‍💻 개발자 (Developer)

**KyungAh Jang** | Full Stack Developer  
* 📧 Email: stars_ka@naver.com  
* 🐙 GitHub: [GitHub](https://github.com/kajang93)