# 🧱 Kuji Server

> Kuji App의 백엔드 서버입니다.  
> Kotlin + Spring Boot 3.x 기반으로 REST API를 제공하며,  
> PostgreSQL을 사용한 데이터 저장 및 JWT 기반 인증 구조를 목표로 하고 있습니다.

---

## 📌 기술 스택

| 항목 | 기술 |
|------|------|
| **언어** | Kotlin (JDK 21) |
| **프레임워크** | Spring Boot 3.2.x |
| **빌드도구** | Gradle (Kotlin DSL) |
| **DB** | PostgreSQL |
| **ORM** | Spring Data JPA + Hibernate |
| **인증** | JWT (Spring Security는 추후 도입 예정) |
| **배포** | Railway (CI/CD 자동 배포) |

---

## 📂 프로젝트 구조

```bash
kuji-server/
├─ build.gradle.kts
├─ settings.gradle.kts
│
└─ src/
   ├─ main/
   │  ├─ java/com/kuji/backend/
   │  │  ├─ controller/          # REST API Controller (요청/응답 처리)
   │  │  ├─ service/             # 비즈니스 로직 처리 (예정)
   │  │  ├─ repository/          # JPA Repository 인터페이스 (예정)
   │  │  └─ entity/              # DB Entity 클래스 (예정)
   │  │
   │  └─ resources/              # (예정)
   │     ├─ application.yml      # 환경 설정 (DB, 포트 등)
   │     ├─ data.sql             # 초기 데이터 (선택)
   │     └─ schema.sql           # DB 스키마 정의 (선택)
   │
   └─ test/
      └─ java/com/kuji/backend/ # 단위 테스트 코드
```




---

## 🧠 개발 단계별 진행

| 단계 | 내용 | 상태 |
|------|------|------|
| 1️⃣ | Spring Boot 프로젝트 초기 세팅 | ✅ 완료 |
| 2️⃣ | PostgreSQL 연결 및 환경 분리 (dev/prod) | ✅ 완료 |
| 3️⃣ | `/hello` API 테스트 | ✅ 완료 |
| 4️⃣ | DB Entity 및 Repository 구현 | ⏳ 예정 |
| 5️⃣ | JWT 기반 인증 및 사용자 등록 API | ⏳ 예정 |
| 6️⃣ | 쿠지 상품/애니메이션 데이터 모델링 | ⏳ 예정 |
| 7️⃣ | Railway 배포 자동화 | ⏳ 예정 |

---
### ⚙️ 실행 방법

### 🔧 로컬 환경 실행

```bash
# 서버 실행
./gradlew bootRun
```

- 기본 포트: [http://localhost:8080](http://localhost:8080)
- 개발 환경: `application.yml → spring.profiles.active=dev`
- DB 연결 확인: `jdbc:postgresql://localhost:5432/kuji`

---

### 📦 빌드

```bash
./gradlew build
```

- 빌드 결과물: `build/libs/kuji-server.jar`

---

### ☁️ 배포 환경 (Railway)

| 항목 | 내용 |
|------|------|
| **플랫폼** | [Railway](https://railway.app) |
| **실행 방식** | Dockerfile 또는 Jar 직접 실행 |
| **DB** | Railway PostgreSQL |
| **로그 확인** | Railway Dashboard → Logs 탭 |

---

### 🔐 JWT 인증 (예정)

| 항목 | 설명 |
|------|------|
| **Access Token** | 로그인 시 발급 |
| **Refresh Token** | 만료 시 재발급용 |
| **Storage** | HTTP Header: `Authorization: Bearer <token>` |

---

### 📡 API 샘플 (예정)

| 메서드 | 엔드포인트 | 설명 |
|---------|-------------|------|
| `GET` | `/api/hello` | 서버 연결 테스트 |
| `GET` | `/api/animes` | 애니메이션 리스트 조회 |
| `GET` | `/api/kuji/{animeId}` | 해당 애니메이션의 쿠지 리스트 |
| `POST` | `/api/purchase` | 쿠지 구매 요청 |
| `POST` | `/api/auth/login` | 로그인 (JWT 발급) |

---

### 🧑‍💻 개발자

**KyungAh Jang**  
Full Stack Developer (Kotlin / React)  
📧 Email: [stars_ka@naver.com](mailto:stars_ka@naver.com)  
🐙 GitHub: [https://github.com/kajang93](https://github.com/kajang93)

---

> 💬 *이 백엔드 서버는 Kuji App의 데이터 및 비즈니스 로직을 담당하며,  
> 클라이언트와 REST API로 통신합니다.*
