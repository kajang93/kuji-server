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

### ✅ Phase 1: 인프라 및 보안/인증 코어 (완료)
- [x] DB 설계 및 PostgreSQL 13개 테이블 수동 생성 및 매핑 (`ddl-auto: validate`)
- [x] Spring Security + BCrypt 적용 비밀번호 암호화
- [x] JWT 기반 Stateless 인증 필터(`JwtAuthenticationFilter`) 및 마스터키 발급 구현
- [x] `@RestControllerAdvice`를 활용한 전역 예외 처리 규격화

### ✅ Phase 2: 코어 비즈니스 개발 (진행 중)
- [x] **사업자 도메인:** `BusinessInfo` 식별 관계 매핑 및 사업자 등록/이미지 URL 연동 API
- [x] **소셜 로그인:** 카카오 OAuth 2.0 연동 및 JWT 발급 고도화
- [x] **일반 로그인:** OAuth를 사용하지 않는 일반 로그인 기능 구현
- [x] **회원 관리:** 신규 사용자의 경우 약관 동의 데이터 수집 및 조건부 가입 로직 구현
- [ ] **쿠지 도메인:** 쿠지 판 생성, 다중 이미지 업로드, 상품 등급별 확률 등록 API
- [ ] **결제 도메인:** 포인트 결제 및 재고 차감 비관적 락(Pessimistic Lock) 동시성 제어

### 🚀 Phase 3: 고도화 및 프로덕션 배포
- [ ] **결제 연동:** PG사 모듈(포트원 등) 통합 및 실결제 검증
- [ ] **배포 자동화:** Railway 배포 파이프라인 구축
- [ ] **하이브리드 패키징:** Capacitor를 활용한 앱 빌드 및 스토어 심사 준비

---

## 🧑‍💻 개발자 (Developer)

**KyungAh Jang** | Full Stack Developer
* 📧 Email: stars_ka@naver.com
* 🐙 GitHub: https://github.com/kajang93