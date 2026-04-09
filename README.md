# 🧱 Kuji Server

> **Kuji App**의 비즈니스 로직과 데이터 관리를 담당하는 백엔드 서버입니다.  
> 모바일 하이브리드 배포(Capacitor)와 실제 상용화(PG 결제, 스토어 심사)를 고려하여, 
> 경량화, 유지보수성, 타입 안정성을 최우선으로 설계되었습니다.

---

## 📌 기술 스택 (Tech Stack)

| 구분 | 기술 | 상세 내용 |
| :--- | :--- | :--- |
| **Language** | **Java 25** | Virtual Thread 및 Record 등 최신 문법 활용 |
| **Framework** | **Spring Boot 3.3.x** | 최신 스프링 생태계 및 성능 최적화 |
| **ORM / DB** | **Spring Data JPA / PostgreSQL 14+** | 객체 지향 데이터 관리 및 JSONB/ENUM 지원 |
| **Query** | **Querydsl** | 동적 쿼리 처리 및 컴파일 타임 타입 안정성 확보 |
| **Mapping** | **MapStruct** | 고성능 객체 매핑 (Entity ↔ DTO) |
| **Auth** | **Spring Security + JWT** | 소셜 로그인(OAuth 2.0) 및 Stateless 인증 구조 |
| **CI/CD** | **Railway** | 빠르고 간편한 클라우드 배포 환경 |

---

## 🗺️ 시스템 아키텍처 / 흐름도

![Architecture Flow](./docs/images/architecture_flow.png)

---

## 📂 프로젝트 구조 (Project Structure)

```bash
kuji-server/
├─ build.gradle          # Gradle 빌드 및 의존성 관리
├─ docs/                 # 문서 및 이미지 보관
└─ src/
   ├─ main/
   │  ├─ java/com/kuji/backend/
   │  │  ├─ global/      # 공통 설정 (Security, Exception, JWT, BaseEntity)
   │  │  └─ domain/      # 도메인별 패키지 (member, business, kuji, payment)
   │  └─ resources/
   │     └─ application.yml # 환경 설정 (DB, JWT Secret, Port)
   └─ test/              # 단위 및 통합 테스트 코드
```

---

## 📐 코딩 컨벤션 (Coding Standards)

혼자서 풀스택을 개발하는 환경에서 컨텍스트 스위칭 비용을 줄이기 위해 명확한 계층 분리를 원칙으로 합니다.

* **DTO 사용 원칙:** * Entity는 절대 클라이언트(프론트엔드)로 직접 노출하지 않습니다.
    * 요청(Request)은 `Record`를 활용하여 불변 객체로 관리하고, `toEntity()` 메서드를 내부에 둡니다.
    * 응답(Response)은 `from(Entity)` 형태의 정적 팩토리 메서드를 사용하여 변환합니다.
* **API 응답 규격화:** * 모든 Controller의 반환형은 `ResponseEntity<T>`로 통일하여 HTTP 상태 코드를 명확히 제어합니다.
* **예외 처리 (Exception Handling):**
    * 비즈니스 로직(Service)에서 발생하는 모든 예외는 `IllegalArgumentException` 또는 커스텀 예외로 던집니다(`throw`).
    * 에러 포장과 프론트엔드 반환은 `GlobalExceptionHandler(@RestControllerAdvice)`가 전담합니다.

---

## 📊 로깅 전략 (Logging Strategy)

장애 발생 시 즉각적인 원인 파악을 위해 로그 레벨을 엄격하게 분리하여 사용합니다. (`@Slf4j` 활용)

| 레벨 | 사용 기준 | 예시 |
| :--- | :--- | :--- |
| **ERROR** | 시스템에 치명적인 문제나 즉각적인 수정이 필요한 버그 (슬랙 알림 연동 대상) | DB 접속 실패, 외부 PG사 결제 모듈 통신 장애 |
| **WARN** | 시스템 에러는 아니지만, 비정상적인 흐름이나 공격 시도가 의심되는 경우 | 동일 IP의 잦은 로그인 실패, 토큰 위조 시도(`403`) |
| **INFO** | 주요 비즈니스 로직의 상태 변경 및 성공 기록 (운영 데이터 추적용) | 신규 사업자 승인 완료, 사용자 포인트 충전 완료 |
| **DEBUG** | 개발 과정에서 흐름을 파악하기 위한 상세 데이터 (운영 서버에서는 숨김) | API 요청 Body 상세 내용, JPA가 생성한 SQL 쿼리 |

---

## 📈 단계별 개발 로드맵 (Milestones)

현재 인프라와 보안의 기틀은 모두 마련되었습니다.
이제 핵심 비즈니스 로직인 **'쿠지 엔진'**과 **'경제 시스템'**, 그리고 **'커뮤니티'** 확장을 진행합니다.

### ✅ Phase 1: 인프라 및 보안/인증 코어 (완료)
- [x] DB 설계 및 PostgreSQL 13개 테이블 수동 생성 및 매핑 (`ddl-auto: validate`)
- [x] Spring Security + BCrypt 암호화 및 JWT Stateless 인증 필터 구현
- [x] `@RestControllerAdvice` 전역 예외 처리 및 공통 응답 규격화
- [x] 엔티티 공통 필드 매핑 (`BaseTimeEntity`)

### 🚧 Phase 2: 핵심 비즈니스 및 경제 시스템 (진행 중)

#### 2.1 회원 및 인증 확장 (완료)
- [x] 사업자 정보(`BusinessInfo`) 식별 관계 매핑 및 등록 API
- [x] 카카오 OAuth 2.0 연동 및 가입 시 약관 동의 데이터 수집
- [x] 일반 로그인 및 JWT 토큰 발급 로직 고도화

#### 2.2 쿠지(Kuji) 엔진 빌드 (Active 🎯)
- [ ] **엔티티 설계**: `KujiBoard`(판), `KujiItem`(상품), `KujiGrade`(등급) 매핑 (ERD 반영)
- [ ] **조회 API**: 쿠지 판 전체 리스트 조회 및 상세 정보 조회 API
- [ ] **확률 엔진**: 남은 상품 수량(`remain_qty`) 기반 무작위 뽑기 알고리즘
- [ ] **관리자 도메인**: 사업자의 쿠지 판/상품 일괄 등록 로직 구현

#### 2.3 경제 시스템 및 동시성 제어
- [ ] **포인트 관리**: 사용자 포인트 충전/사용/소멸 이력(`PointHistory`) 관리
- [ ] **재고 잠금**: 뽑기 시 발생할 수 있는 데이터 경합을 **비관적 락(Pessimistic Lock)**으로 해결
- [ ] **결제 연동**: 포트원(PortOne) API 연동을 통한 실결제 처리

### 🏗️ Phase 3: 서비스 확장 및 UX 고도화

#### 3.1 커뮤니티 및 소통
- [ ] **커뮤니티**: 자유 게시판, 인증 샷 게시판 엔티티 및 API 구현
- [ ] **댓글/좋아요**: 게시글 및 쿠지 판에 대한 사용자 반응 기능

#### 3.2 미디어 및 인프라 강화
- [ ] **이미지 스토리지**: AWS S3를 활용한 회원 프로필 및 쿠지 이미지 저장
- [ ] **푸시 알림**: FCM(Firebase Cloud Messaging) 연동 및 알림 설정 화면 구성

### 🚀 Phase 4: 프로덕션 운영 및 출시
- [ ] **배포 자동화**: Railway CI/CD 파이프라인 구축 및 무중단 배포
- [ ] **하이브리드 앱**: Capacitor 빌드 및 스토어(Android/iOS) 최종 심사

---

## 🏁 다음 작업 가이드 (Next Steps)

한 번에 하나씩, 가장 중요한 것부터 진행합니다.

1.  **Step 1. 엔티티 구축**: `domain/kuji` 폴더를 만들고, ERD를 바탕으로 `KujiBoard`, `KujiItem` 엔티티를 작성합니다.
2.  **Step 2. 리스트 조회**: 저장된 데이터를 화면에 뿌려줄 수 있도록 리스트/상세 조회 API를 만듭니다.
3.  **Step 3. 뽑기 엔진**: 실제로 포인트가 차감되고 랜덤하게 상품이 선택되는 핵심 로직을 완성합니다.

---

## 🧑‍💻 개발자 (Developer)

**KyungAh Jang** | Full Stack Developer
* 📧 Email: stars_ka@naver.com
* 🐙 GitHub: https://github.com/kajang93