# Anytime

학교 안에서 공강 시간이 맞는 재학생끼리 모임을 만들어 주는 플랫폼. 인공지능 융합 플랫폼 수업 팀 프로젝트 (팀 챗송심이).

## 폴더 구조

```
anytime/
├── backend/            Spring Boot 3 (Java 17)
├── frontend/           Vite + React
├── docs/               ERD, API 명세, 배포 가이드
├── deploy/             서버 배포 스크립트 (docs/deploy.md 참고)
└── docker-compose.yml  로컬 MySQL 8.4
```

## 설치 (4명 모두)

1. **JDK 17**: [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17) 설치 (Windows x64 .msi)
   - 다른 버전 Java(21 등)가 이미 있으면 지우지 않아도 된다. 설치 화면의 Custom Setup에서 `Modify PATH variable`, `Set JAVA_HOME`을 끄고 설치한 뒤, IntelliJ에서 17을 고르면 된다
   - Java가 없던 컴퓨터라면 두 옵션을 켜고 설치하고, `java -version`으로 17이 나오는지 확인
2. **IntelliJ IDEA**: Community 또는 학생 계정 Ultimate
   - 저장소 폴더(`anytime/`)를 열고, `backend/build.gradle`을 우클릭해 **Link Gradle Project**
   - File → Project Structure → Project: SDK는 Temurin 17, Language level은 17
   - Settings → Build Tools → Gradle: Gradle JVM은 Project SDK(17)
3. **MySQL 8.4 LTS**: 둘 중 하나
   - Docker Desktop이 있으면 저장소 루트에서 `docker compose up -d`
   - 없으면 [MySQL Community Server](https://dev.mysql.com/downloads/mysql/)에서 **8.4 LTS** Windows MSI를 받아 설치 (26.x 같은 최신 Innovation 버전 말고 8.4). 설치 후 Configurator에서 Development Computer, 포트 3306, root 비밀번호 설정, Windows 서비스로 실행. 그다음 시작 메뉴의 MySQL 8.4 Command Line Client에서 아래 SQL 실행
     ```sql
     CREATE DATABASE anytime DEFAULT CHARACTER SET utf8mb4;
     CREATE USER 'anytime'@'localhost' IDENTIFIED BY 'anytime';
     GRANT ALL PRIVILEGES ON anytime.* TO 'anytime'@'localhost';
     ```
4. **Node.js 18 이상**: 프론트 실행용

## 실행

```bash
# 백엔드 (http://localhost:8080)
cd backend
./gradlew bootRun        # Windows PowerShell: .\gradlew.bat bootRun
# 'Started AnytimeApplication'이 뜨면 성공. 서버가 켜져 있는 동안 이 터미널은 입력이 안 되니, 다른 명령은 새 터미널에서

# 확인
curl http://localhost:8080/api/health   # {"status":"ok"}

# 프론트 (http://localhost:5173)
cd frontend
npm install
npm run dev
```

DB 접속 정보는 기본값이 `anytime / anytime`이다. 바꾸려면 `.env.example`을 참고해 환경 변수로 넘긴다.

프론트의 `vite.config.ts`에는 `/api` 요청을 8080으로 넘기는 프록시가 들어 있어서, 백엔드와 같이 띄우면 CORS 설정 없이 연결된다. 화면 목록과 시연 방법은 [frontend/README.md](frontend/README.md)에 있다.

## API 개발 규칙 (4단계 공통 틀)

- **Swagger**: 서버를 띄우고 http://localhost:8080/swagger-ui.html 에서 모든 API를 보고 바로 호출해 볼 수 있다
- **에러 응답**은 모두 `{"code": "ROOM_NOT_FOUND", "message": "모임을 찾을 수 없어요"}` 모양이다. 서비스에서 실패하면 `throw new ApiException(ErrorCode.ROOM_NOT_FOUND)`처럼 던진다. 새 에러는 `common/ErrorCode.java`에 추가한다
- **입력 검사**: 요청 DTO에 `@NotBlank`, `@Size` 등을 달고 컨트롤러에서 `@Valid`를 붙이면 실패 시 자동으로 `INVALID_INPUT`이 나간다
- **현재 사용자**: 컨트롤러 파라미터에 `@CurrentUserId Long userId`를 쓴다 (`@AuthenticationPrincipal Long userId`도 같은 값). JWT 액세스 토큰의 사용자 id이고, 토큰이 없으면 401이 나가므로 null 검사는 필요 없다. 자세한 건 아래 7단계
- **경로**는 `/api/...`로 시작한다. 요청마다 서버 로그에 `GET /api/rooms -> 200 (12ms)`가 찍힌다

## 이메일 인증 (5단계)

- `POST /api/auth/email` `{"email": "아이디@dankook.ac.kr"}` → 6자리 인증번호 발송 (10분 유효, 1분 뒤 재발송 가능)
- `POST /api/auth/email/verify` `{"email": ..., "code": "123456"}` → `{"userId": 11, "newUser": true}`. 처음이면 사용자를 만든다(임시 닉네임 `새친구1234`). 5번 틀리면 다시 받아야 한다
- **로컬에서는 메일 설정이 없어도 된다.** `MAIL_USERNAME`이 비어 있으면 메일 대신 서버 로그에 `[개발 모드] ... 인증번호: 123456`이 찍힌다
- 실제 메일을 보내려면 Gmail 앱 비밀번호가 필요하다
  1. 보내는 Gmail 계정에서 2단계 인증을 켠다
  2. Google 계정 → 보안 → 앱 비밀번호에서 새 비밀번호(16자리)를 만든다
  3. IntelliJ 실행 설정(Run → Edit Configurations)의 Environment variables나 서버의 `.env`에 `MAIL_USERNAME=보내는주소@gmail.com`, `MAIL_APP_PASSWORD=16자리`를 넣는다
- 이메일은 원문으로 저장하지 않는다. 조회용 HMAC 해시(`EMAIL_HASH_KEY`)와 메일 발송용 AES 암호문(`EMAIL_ENC_KEY`)으로 저장한다. 로컬은 기본 키가 들어 있고, 서버에는 반드시 다른 값을 넣는다

## 로그인 토큰 (7단계, JWT)

- `POST /api/auth/email/verify`가 성공하면 `{userId, newUser, accessToken, refreshToken, expiresIn}`을 준다
- 그 뒤 모든 `/api/**` 요청에 `Authorization: Bearer <accessToken>`을 붙인다. 토큰 없이 열린 경로는 `/api/health`, `/api/auth/*`뿐이다 (`config/SecurityConfig.java`의 `PUBLIC_API`)
- 액세스 토큰 15분, 리프레시 토큰 14일. 만료되면 `401 AUTH_TOKEN_EXPIRED` → `POST /api/auth/refresh {"refreshToken"}`로 새 한 쌍을 받는다. 쓴 리프레시 토큰은 다시 못 쓴다
- `POST /api/auth/logout {"refreshToken"}`: 리프레시 토큰 폐기 (`refresh_tokens` 테이블에서 삭제)
- 401 응답 code: `UNAUTHORIZED`(토큰 없음), `AUTH_TOKEN_EXPIRED`(만료), `AUTH_TOKEN_INVALID`(위조·형식 오류), `AUTH_REFRESH_INVALID`(리프레시 실패, 다시 인증)
- 서버의 `JWT_SECRET`은 `setup-server.sh`가 만든다. 로컬은 기본값이 들어 있다

**컨트롤러에서 현재 사용자 꺼내기**

```java
@GetMapping("/api/me")
public MeResponse me(@CurrentUserId Long userId) { ... }
// 또는 Spring Security 기본 방식
public MeResponse me(@AuthenticationPrincipal Long userId) { ... }
```

**로컬에서 테스트할 때** (예전 `X-User-Id` 헤더 대신)

1. Swagger에서 `POST /api/auth/dev-login` `{"userId": 1}` 실행 → `accessToken` 복사 (1 = 데모유저, 시드 사용자 아무나 가능)
2. Swagger 오른쪽 위 **Authorize**에 붙여 넣으면 이후 요청에 토큰이 붙는다
3. curl: `curl -H "Authorization: Bearer <토큰>" http://localhost:8080/api/...`

`dev-login`은 local 프로필에만 있고 서버(prod)에는 없다.

**프론트에서 API 부르기**: `src/api/client.ts`의 `api.get/post/put/patch/delete`를 쓴다. 토큰 헤더, 만료 시 자동 재발급, 에러 → 토스트가 다 들어 있다.

```ts
import { api } from './api/client';
const me = await api.get<Me>('/me');                       // 실패하면 서버 message가 토스트로 뜬다
await api.post('/rooms/3/join', undefined, { quiet: true }); // 토스트 없이 화면에서 직접 처리할 때
```

## 프로필 (8단계)

| API | 설명 |
| --- | --- |
| `GET /api/me` | 내 프로필. 이메일·비공개 관심사까지 본인에게만. `profileCompleted=false`면 아직 프로필 만들기 전(가입 직후 임시 닉네임 `새친구1234`) |
| `PATCH /api/me` | 보낸 항목만 바꾼다. 닉네임 2~10자·띄어쓰기 불가, 겹치면 `409 USER_NICK_TAKEN`. `interests`는 1~8개 `[{"name": "코딩", "isPublic": true}]`, 보내면 목록 전체 교체. 학과를 비우면 학과 공개도 꺼진다 |
| `GET /api/users/check-nick?nick=` | 중복 확인 버튼용 `{available, message}`. 내 닉네임은 `available=true` |
| `GET /api/users/{id}` | 다른 사람에게 보이는 프로필: 닉네임, 공개한 학과·관심사, 매너 점수, 완료 모임 수. **이메일·시간표는 주지 않는다**. 차단 관계(어느 쪽이든)거나 영구 정지면 `404 USER_NOT_FOUND` |

- 프론트: `src/api/me.ts`. 프로필 화면의 중복 확인과 저장이 서버를 거치고, 로그인 직후·앱 시작 때 `GET /me`로 프로필을 채운다
- Windows Git Bash의 curl로 한글 JSON을 `-d '{...}'`로 보내면 인코딩이 깨져 400이 난다. Swagger를 쓰거나, UTF-8 파일로 저장해서 `--data-binary @body.json`으로 보낸다
- `Person.tsx`(다른 사람 프로필 화면)는 아직 프론트 id(`u1`)를 쓰므로 그대로 두었다. 10단계 추천 API가 서버 id를 주면 `meApi.user(id)`로 바꾼다

## 시간표 (9단계)

| API | 설명 |
| --- | --- |
| `GET /api/me/timetable` | 내 수업과 만나고 싶은 공강 `{classes: [{id, name, day, start, end}], wanted: [{day, start, end}]}`. 본인 것만 |
| `PUT /api/me/classes` | `{"classes": [...]}`로 **전체 교체**(빈 목록이면 비우기). 수업끼리 겹치면 `400 TIMETABLE_CLASS_OVERLAP`. 새 수업과 겹치게 된 공강은 서버가 같이 지운다 |
| `PUT /api/me/wanted` | `{"wanted": [...]}`로 **전체 교체**. 내 수업과 겹치면 `400 TIMETABLE_WANTED_NOT_FREE`, 공강끼리 겹치면 `400 INVALID_INPUT` |

- 시간은 요일(0=월~4=금) + 하루 중 분(540 = 09:00). 끝나는 시각에 바로 시작하는 수업(09:00–10:30, 10:30–12:00)은 겹치지 않는다
- 다른 사람의 시간표 원본은 어떤 API도 주지 않는다. 10단계 추천은 서버 안에서 `class_blocks`, `wanted_slots`를 읽어 겹치는 공강만 응답한다
- 프론트: `src/api/timetable.ts`. 프론트는 공강마다 `wanted[key] = true/false`(기본 true)를 들고, 서버에는 켜진 공강만 보낸다
- `store.tsx`의 `addClass`·`removeClass`·`loadExample`·`toggleWanted`는 화면에 바로 반영하고 서버 저장은 뒤에서 한다. 수업 수정(삭제+추가)은 한 번의 요청으로 모으고, 실패하면 서버에 저장된 상태로 되돌린다
- 로그인할 때 서버 시간표가 비어 있고 이 기기에 시간표가 있으면(로그인 전에 만든 것) 서버로 올린다
- 시드: 데모유저(id 1)의 공강 5칸을 `wanted_slots`에 넣었다

## 브랜치와 PR 규칙

- `main`에는 직접 push하지 않는다. PR로만 합친다
- 브랜치 이름: `feat/<단계번호>-<이름>` (예: `feat/10-recommend`, `feat/14-stomp`)
- 단계 하나가 끝나면 바로 PR. 하루 한 번은 합친다
- 작업 시작 전에 `git pull origin main`
- PR은 다른 팀원 한 명이 보고 승인하면 머지

### 담당자가 있는 파일

여러 명이 같이 고치면 충돌이 잘 나는 파일이다. 고칠 일이 있으면 PR에 적고 담당자에게 알린다.

| 파일 | 담당 |
| --- | --- |
| 엔티티(`domain/`), `db/schema.sql`, `db/data.sql` | 1번 |
| `config/SecurityConfig.java` | 2번 |
| 알림 공통 메서드 `notify(...)` | 3번 |

## 비밀 정보

`.env`, Gmail 앱 비밀번호, JWT 비밀키, AI API 키는 저장소에 올리지 않는다. `.env.example`만 올린다.

## 현재 상태

- [x] 1단계: 저장소, 뼈대, 설치 가이드
- [x] 2단계: DB 설계 ([docs/erd.md](docs/erd.md))
- [x] 3단계: 엔티티·리포지토리·시드 (실행할 때마다 DB가 시드로 초기화됨. [docs/README.md](docs/README.md) 참고)

- [x] 4단계: 공통 틀 (에러 응답 `{code, message}`, 입력 검사, Swagger, 요청 로그, `@CurrentUserId`)
- [x] 5단계: 이메일 인증 (발송·확인 API, 이메일 HMAC 해시 + AES 암호화)
- [x] 6단계: 서버 배포 ([docs/deploy.md](docs/deploy.md)). 배포는 Git Bash에서 `./deploy/deploy.sh`
- [x] 7단계: JWT + Security, 프론트 `src/api/client.ts`와 인증 화면 연결
- [x] 8단계: 프로필 API (`GET/PATCH /me`, `GET /users/:id`, 닉네임 중복 검사, 학과·관심사 공개 여부), 프로필 화면 연결
- [x] 9단계: 시간표 API (`GET /me/timetable`, `PUT /me/classes`, `PUT /me/wanted`, 수업 시간 겹침 검사), 시간표 화면 연결
