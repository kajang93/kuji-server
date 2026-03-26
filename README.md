# 🧱 Kuji Server

> **Kuji App**의 비즈니스 로직과 데이터 관리를 담당하는 백엔드 서버입니다.  
> 모바일 하이브리드 배포(Capacitor)와 실제 상용화(PG 결제, 스토어 심사)를 고려하여, 
> 경량화, 유지보수성, 타입 안정성을 최우선으로 설계되었습니다.

---

## 📌 기술 스택 (Tech Stack)

| 구분 | 기술 | 상세 내용 |
| :--- | :--- | :--- |
| **Language** | **Java 21** | Virtual Thread 및 Record 등 최신 문법 활용 |
| **Framework** | **Spring Boot 3.5.x** | 최신 스프링 생태계 및 성능 최적화 |
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
├─ build.gradle          # Gradle 빌드 및 의존성 관리 (Querydsl, MapStruct)
├─ settings.gradle       
├─ docs/                 # 문서 및 이미지 보관
└─ src/
   ├─ main/
   │  ├─ java/com/kuji/backend/
   │  │  ├─ global/      # 공통 설정 (Security, Exception, BaseEntity)
   │  │  └─ domain/      # 도메인별 패키지 (member, kuji, payment, support)
   │  └─ resources/
   │     └─ application.yml # 환경 설정 (DB, Port, ddl-auto)
   └─ test/              # 단위 및 통합 테스트 코드
```

---

## 🗄️ 데이터베이스 설계 (ERD)

총 **13개의 테이블**로 구성되어 있으며, 주요 관계는 다음과 같습니다.

* **회원 도메인:** `member` ↔ `business_info` (1:1 식별 관계, OAuth 정보 포함)
* **쿠지 도메인:** `kujiboard` ↔ `kujiitem` (1:N 관계, 다중 이미지 지원)
* **결제 도메인:** `payment` ➡️ `pointhistory` ➡️ `drawhistory` (결제 및 적립 흐름)

---

## 💰 운영 및 배포 전략 (Operations & Deployment)

### 1. 하이브리드 앱 배포 (Capacitor)
기존 크롬앱(PWA/웹) 기반의 코드를 모바일 네이티브 앱으로 확장합니다.
* **접근법:** 웹 코드(HTML/JS)를 유지하며 `Capacitor`를 통해 네이티브 기능(카메라, 결제 브릿지) 연동.
* **출시 전략:** 안드로이드(Play Store) 우선 심사 진행 후, 네이티브 기능 보강하여 iOS(App Store) 출시.

### 2. 마켓 심사 및 규정 준수 (Compliance)
* **확률형 아이템 공시:** 모든 쿠지 판 및 등급별 당첨 확률을 앱 내에 상시 투명하게 노출 (스토어 필수 규정).
* **카테고리 최적화:** '게임' 카테고리를 피하고 **'쇼핑'** 또는 **'엔터테인먼트'** 카테고리로 등록.
* **환불 정책:** 랜덤 상품 특성에 따른 명확한 약관 명시 및 사전 고지.

### 3. 예상 고정 지출 (Estimated Costs)
| 항목 | 예상 비용 | 주기 | 비고 |
| :--- | :--- | :--- | :--- |
| **Apple Developer** | 129,000원 | 매년 | iOS 배포 유지 필수 |
| **Google Developer** | 약 30,000원 ($25) | 최초 1회 | 안드로이드 배포 |
| **PG사 가입비** | 220,000원 | 최초 1회 | 프로모션 활용 시 면제 가능 |
| **보증보험/통신판매업**| 약 5~10만 원 | 매년 | 결제 연동 및 전자상거래 필수 |
| **도메인 / 서버** | 약 3~5만 원 | 매월 | 초기 트래픽 기준 유지비 |

---

## 📈 단계별 개발 로드맵 (Milestones)

### ✅ Phase 1: 인프라 기반 구축 (완료)
- [x] DB 설계 및 PostgreSQL 13개 테이블 수동 생성 완료
- [x] Spring Boot 프로젝트 초기화 및 DB 연결 (`ddl-auto: validate`)
- [x] 소셜 로그인(OAuth) 대응을 위한 `member` 테이블 확장 (`social_type`, `social_id`)

### ⏳ Phase 2: 코어 비즈니스 개발 (진행 중)
- [ ] **환경 세팅:** Querydsl 및 MapStruct 설정 (build.gradle)
- [ ] **엔티티 모델링:** `BaseTimeEntity`를 활용한 도메인 엔티티(Entity) 및 매핑 연동
- [ ] **핵심 로직:** 회원가입, 쿠지 상품 등록/조회, 포인트 결제 API 및 재고 차감 로직 구현

### 🚀 Phase 3: 고도화 및 프로덕션 배포
- [ ] **보안 및 예외:** Spring Security + JWT 적용 및 전역 예외 처리(`@RestControllerAdvice`)
- [ ] **결제 연동:** PG사 연동 모듈 통합 및 실결제 테스트
- [ ] **배포 자동화:** Railway CI/CD 파이프라인 구축 및 DB 마이그레이션(Flyway) 도입

---

## 🧑‍💻 개발자 (Developer)

**KyungAh Jang** Full Stack Developer
* 📧 Email: stars_ka@naver.com
* 🐙 GitHub: https://github.com/kajang93