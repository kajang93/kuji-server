# ⚙️ 이치방쿠지 (一番くじ) 백엔드 시스템

> **"행운의 데이터를 실시간으로 제어하는 강력한 코어"**  
> 본 프로젝트는 '이치방쿠지' 웹 서비스의 핵심 비즈니스 로직을 담당하는 Spring Boot 기반의 백엔드 서버입니다.  
> 확률 기반 추첨 엔진, 동시성 제어, 그리고 복잡한 물류 시스템을 안정적으로 처리합니다.

---

## 🛠️ 기술 스택 (Tech Stack)

| 항목 | 기술 | 상세 내용 |
|------|------|------|
| **Framework** | Spring Boot 3.x | 효율적인 REST API 및 의존성 관리 |
| **Language** | Java 17 | 최신 자바 문법 및 안정적인 런타임 |
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
- **JWT 인증**: 모든 요청에 대한 토큰 검증 및 보안 필터 적용.
- **카카오 OAuth 2.0**: 소셜 로그인 연동을 통한 간편 가입 및 로그인.
- **Role-Based Access**: 일반 사용자, 사업자, 관리자별 상세 권한 제어.

---

## 🚀 API 엔드포인트 목록 (API Specs)

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
| PATCH | `/api/shipping/{id}/tracking` | (관리자) 운송장 등록 및 배송 시작 처리 |

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

### 🏃 Phase 3: 커뮤니티 및 문의 시스템 (진행 중)
- [x] **커뮤니티(게시판)**: 당첨 인증, 자유 게시판 (CRUD 완료)
- [x] **실시간 티커**: 메인 페이지 당첨 내역 흐름 알림 API 완료
- [x] **1:1 문의 시스템**: 등록, 상세 조회 및 관리자 답변 기능 완료
- [ ] **게시판 고도화**: AWS S3 연동을 통한 사진 업로드 및 댓글 기능

### 🏗️ Phase 4: 배송 및 결제 시스템
- [ ] **배송 추적 시스템**: 외부 택배 API(SweetTracker 등) 연동을 통한 실시간 배송 현황 조회
- [ ] **결제 시스템**: 토스페이먼츠 / 카카오페이 API 연동 (포인트 충전)
- [ ] **실시간 알림**: FCM을 통한 배송 시작 및 문의 답변 알림
- [ ] **운영 도구**: 정산 관리 및 통계 대시보드 고도화

---

## 🧑‍💻 개발자 (Developer)

**KyungAh Jang** | Full Stack Developer  
* 📧 Email: stars_ka@naver.com  
* 🐙 GitHub: [GitHub](https://github.com/kajang93)