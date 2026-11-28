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
- `Person.tsx`(다른 사람 프로필 화면)는 10단계에서 서버 id를 쓰도록 바꿨다 (아래 추천 참고)

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
- 시드: 시드 사용자 모두의 수업 사이 공강을 `wanted_slots`에 넣었다 (10단계에서 데모유저 외 사용자 추가)

## 추천 (10단계)

| API | 설명 |
| --- | --- |
| `GET /api/recommend/people` | 나와 공강이 60분 이상 겹치는 사람, 점수 높은 순(최대 50명). `[{user, slots, shared, score, reason}]`. `user`는 `GET /users/{id}`와 같은 공개 프로필, `slots`는 **겹치는 구간만**(상대 시간표는 안 준다) |
| `GET /api/recommend/rooms` | 지금 합류할 수 있는(`open`/`soon`) 모임 중 내가 안 들어간 것. `[{room, status, fit, slot}]`. 내 공강과 겹치는(`fit > 0`) 모임 먼저, 그 안에서는 빨리 시작하는 순 |

- 계산은 프론트 `derive.ts`를 그대로 옮겼다 (`recommend/FreeTime.java` = `time.ts`, `recommend/InterestConcepts.java` = `ai.ts`, `RecommendService` = `recommendPeople`·`recommendRooms`·`roomFit`)
  - 점수 = `0.55 × min(1, 겹친 분/180) + 0.45 × 관심사 유사도 + 매너/1000`. 관심사 유사도는 키워드를 개념으로 묶은 자카드 유사도
- **나와 상대 모두 '만나고 싶은 공강'(`wanted_slots`)만 쓴다.** 상대가 끈 공강에는 추천되지 않는다. 프론트는 시드 사용자에게 이 값이 없어서 수업 사이 공강 전체를 썼는데, 시드에 전부 넣어 두었으므로 결과는 같다
- 상대 관심사는 **공개한 것만** 점수와 `shared`에 쓴다. 일시 정지(`sanction >= 2`) 이상은 추천하지 않는다
- **차단 제외**: `BlockRepository.findHiddenUserIds(userId)`가 차단 공통 메서드다(내가 차단 + 나를 차단, 양방향). 사람 추천에서는 그 사람을, 모임 추천에서는 그 사람이 방장인 모임을 뺀다. 17단계(차단 API)는 `blocks`에 넣기만 하면 추천에서 바로 빠진다
- **derive.ts와 같은지 확인**: `RecommendServiceTest`가 시드와 같은 데이터로 사람 순서·점수(소수점 끝자리까지)·이유 문장, 모임 순서를 비교한다. 기대값은 `frontend/scripts/recommend-parity.ts`의 출력이다(실행법은 파일 맨 위). 실제 서버도 dev-login(1)로 `GET /api/recommend/people`을 부르면 같은 순서·점수가 나온다
- **방 상태** `room/RoomPhase.java`: derive.ts `roomStatus()`를 옮긴 것. 프론트 시연 시계 대신 `meet_date + start_min`과 실제 지금 시각(한국 시간)으로 계산한다. **11단계 모임 목록·상세에서 그대로 쓰면 된다** (`RoomPhase.of(room, 참여 인원, LocalDateTime.now(clock))`)
- **지금 시각**: `config/TimeConfig`의 `Clock` 빈(Asia/Seoul)을 주입받아 `LocalDateTime.now(clock)`을 쓴다. 서버 VM이 UTC여도 한국 시간으로 계산되고, 테스트에서는 `Clock.fixed(...)`로 시각을 고정할 수 있다. 18단계 크론도 이걸 쓰면 된다
- 프론트: `src/api/recommend.ts`. 로그인하면 앱 시작·시간표 저장·프로필 저장 뒤에 `GET /recommend/people`을 받아 `serverPeople`에 두고, 홈·모임 화면의 사람 추천이 이걸 쓴다. `Person.tsx`는 서버 id(숫자)를 받으면 추천 목록의 겹치는 공강을 쓰고, 목록에 없으면 `GET /users/{id}`로 프로필만 받는다
- 모임 추천 화면은 아직 프론트 `derive.ts`를 쓴다(모임이 아직 `data.ts`에 있어서 id가 서버와 다르다). **11단계에서 모임을 서버로 옮길 때** `recommendApi.rooms()`로 바꾸면 된다 (응답 타입 `ServerRoomReco`는 만들어 두었다)

## 모임 (11단계)

| API | 설명 |
| --- | --- |
| `GET /api/rooms` | 열린 모임 목록. `day`, `activity`, `place`, `afterMin`, `minCap`, 공강 겹침(`fitOnly`), 내 모임(`mine`) 필터 지원. 차단 관계인 사람이 방장인 모임은 빠진다 |
| `POST /api/rooms` | 모임 만들기. 정지(`sanction >= 2`) 계정은 `403 USER_SUSPENDED`. 모임(`Room`), 방장 `RoomMember`, 모임 채팅(`Chat.forRoom`), 방장 `ChatMember`, 첫 시스템 메시지를 한 트랜잭션으로 만든다 |
| `GET /api/rooms/{id}` | 모임 상세. `isHost`, `chatId`, 참여자 공개 프로필(`UserProfileResponse`)을 내려준다. 차단 관계인 참여자는 목록에서 빼되 `memberCount`는 실제 인원수를 유지한다. 방이 없거나 비참여자에게 방장이 차단 관계면 `404 ROOM_NOT_FOUND` |

- **방 날짜(`meetDate`)**: `Clock` 기준 오늘 이후(포함) 가장 가까운 `day` 요일로 넣는다. 오늘 요일인데 시작 시간이 이미 지났으면 다음 주 날짜로 잡는다
- **방 상태**: `RoomPhase.of(room, memberCount, LocalDateTime.now(clock))`를 목록·상세 모두에서 계산해 내려준다
- **프론트**: `src/api/rooms.ts` 추가. 로그인 상태면 모임 목록, 상세, 생성과 모임 추천(`recommendApi.rooms()`)이 서버를 탄다 (비로그인 시연용 로컬 fallback 유지). 합류·탈퇴(12단계)와 채팅(13·14단계)은 다음 단계에서 서버에 연결한다

## 모임 합류·탈퇴·방장 관리 및 공통 알림 (12단계)

| API | 설명 |
| --- | --- |
| `POST /api/rooms/{id}/join` | 모임 합류. 정지(`sanction >= 2`) 계정은 `403 USER_SUSPENDED`로 막고, `Room` 행에 비관적 잠금(`PESSIMISTIC_WRITE`)을 걸어 동시 정원 초과를 방지한다. 같은 날짜(`meetDate`)에 시간이 겹치는 활성 모임이 이미 있으면 `409 CONFLICT`를 반환한다(11단계 모임 생성에도 동일 적용). 이전에 나갔던 참여자는 기존 `room_members` 행의 `left_at`을 비워 재활성화하며, `chat_members` 등록·입장 시스템 메시지·방장 `JOIN` 알림까지 한 트랜잭션으로 처리한다 |
| `POST /api/rooms/{id}/leave` | 모임 참여 취소. 시작 60분 이내에는 사유(`reason`)가 필수이며(미입력 시 `400 INVALID_INPUT`), `"그냥 못 가요"` 사유면 매너 점수 `0.5` 차감(최저 `30.0`) 및 `cancel_log(penalty=0.5)`를 기록한다. 그 외 사유나 60분 초과 전·진행 중(`closed`) 취소는 빈 사유를 `"단순 취소"`로 처리하고 `penalty=0.0`으로 기록만 남긴다. 방장이 나가면 가장 먼저 합류한 활성 참여자에게 방장을 자동 위임하고 `CHANGE` 알림을 보내며, 남은 인원이 없으면 `CANCELED`로 바꾼다 |
| `POST /api/rooms/{id}/cancel` | 방장 모임 취소. 방장 권한(`FORBIDDEN`)을 검사한 뒤 상태를 `CANCELED`로 바꾸고 시스템 메시지 기록 및 남은 참여자에게 `CANCEL` 알림을 보낸다(매너 차감 없음) |
| `POST /api/rooms/{id}/host` | 방장 위임(`{"userId": ...}`). 활성 참여자이면서 제재(`sanction >= 2`)가 없는 대상에게 방장을 넘기고 시스템 메시지 및 `CHANGE` 알림을 보낸다 |

- **동적 상태 및 에러 코드**: `join`은 `RoomPhase.joinable()`(`open`/`soon`)일 때만 허용하며 상태에 따라 `ROOM_FULL`, `ROOM_CLOSED`, `ROOM_ENDED`, `ROOM_CANCELLED`, `ROOM_ALREADY_JOINED`를 내려준다. 차단 관계인 방장의 모임은 `404 ROOM_NOT_FOUND`, 차단 관계인 일반 참여자가 있는 모임은 `403 FORBIDDEN`으로 막는다
- **공통 알림 메서드 (`NotificationService.notify`)**: 알림 저장과 `users.settings.notif.<type>` 수신 여부 확인을 한곳에서 처리한다(`settings`나 해당 알림 키가 없으면 기본값 `true`로 발송). 이후 13단계 1:1 요청·18단계 크론 알림도 이 메서드를 쓴다
- **프론트**: `src/api/rooms.ts`에 `join`·`leave`·`cancel`·`transferHost`를 추가하고 `Rooms.tsx`(`RoomDetail`)·`store.tsx`에 연결했다. 추가로 `derive.ts`(`myRooms` 서버 목록 필터링), `Me.tsx`(마운트 시 `loadRooms`), `Rooms.tsx`(`RoomCreate` 동시간대 모임 중복 차단), `Auth.tsx`(기존 회원 재로그인 시 프로필 설정 생략), `store.tsx`(DB 초기화 등으로 `404 USER_NOT_FOUND` 시 자동 로그아웃)까지 반영했다 (비로그인 시연용 로컬 fallback 유지)

## 채팅 조회 및 1:1 요청 (13단계)

| API | 설명 |
| --- | --- |
| `GET /api/chats` | 참여 중인 모임(`ROOM`)·1:1(`DM`) 채팅 목록(최신 활동 순). 모임 메타(`ChatRoomInfo`) 또는 상대 공개 프로필(`UserProfileResponse`), 마지막 메시지, 안 읽은 수(`unreadCount`)를 내려준다. 양방향 차단 관계인 DM 채팅방은 빠진다 |
| `GET /api/chats/{id}/messages` | 채팅 메시지 최신순(`id DESC`) 페이징 조회(`?page=0&size=50`). 참여자 권한과 DM 차단 여부를 검사하고 `chat_members.last_read_at`을 현재 시각으로 갱신한다 |
| `POST /api/chats/{id}/read` | 채팅방 읽음 시각(`last_read_at`) 갱신 (`204 No Content`) |
| `PATCH /api/chats/{id}/link` | 모임(`ROOM`) 채팅방 오픈채팅 링크 등록·수정·삭제(빈 값/`null` 허용). DM 채팅방은 `400 CHAT_LINK_NOT_ALLOWED`, 값이 있으면 `http://`·`https://` 형식과 255자 이하를 검사한다 |
| `POST /api/dm-requests` | 1:1 만남 요청 전송. 자기 자신(`400 DM_REQUEST_SELF`), 차단 관계(`404 USER_NOT_FOUND`), 이미 열린 DM 채팅방(`409 DM_CHAT_ALREADY_EXISTS`), 양방향 대기 요청 중복(`409 DM_REQUEST_ALREADY_EXISTS`)을 막고 상대에게 `REQUEST` 알림을 보낸다 |
| `GET /api/dm-requests` | 차단 사용자를 제외한 받은 대기 요청(`received`)과 보낸 요청(`sent`)을 `{received, sent}`로 내려준다 |
| `POST /api/dm-requests/{id}/accept` | 1:1 요청 수락. 비관적 잠금(`PESSIMISTIC_WRITE`)으로 중복 수락을 막고, 기존 DM 채팅방이 있으면 재사용·없으면 새로 만들어 공강 안내 시스템 메시지와 요청 메시지를 저장한다. 수신자의 `REQUEST` 알림을 `ACCEPTED`로 처리하고 요청자에게 `JOIN` 알림을 보낸다 |
| `POST /api/dm-requests/{id}/decline` | 1:1 요청 거절. 비관적 잠금 후 상태를 `DECLINED`로 바꾸고 `REQUEST` 알림을 `DECLINED`로 처리한다 |

- **에러 코드**: `CHAT_NOT_FOUND`, `CHAT_FORBIDDEN`, `CHAT_LINK_NOT_ALLOWED`, `DM_REQUEST_NOT_FOUND`, `DM_REQUEST_SELF`, `DM_REQUEST_ALREADY_EXISTS`, `DM_REQUEST_ALREADY_PROCESSED`, `DM_CHAT_ALREADY_EXISTS`
- **프론트**: `src/api/chats.ts`를 추가하고 `Chat.tsx`·`Rooms.tsx`·`Person.tsx`·`Notifications.tsx`·`App.tsx`·`store.tsx`를 연결했다. 채팅 목록·메시지 페이징("이전 메시지 더 보기")·읽음 배지·오픈채팅 링크·모임 상세 임베드 채팅·1:1 요청 전송/수락/거절이 서버를 탄다(비로그인 시연용 로컬 fallback 유지). 실시간 메시지 전송(`send`)은 14단계(STOMP WebSocket)에서 연결한다

## 실시간 채팅 WebSocket · STOMP (14단계)

| 구분 | 경로 / 설명 |
| --- | --- |
| Handshake | `/ws/chat` (네이티브 WebSocket, SockJS 미사용). 허용 Origin은 `app.ws.allowed-origin-patterns`(`${WS_ALLOWED_ORIGINS:...}`)로 주입한다 |
| 브로커 / 프리픽스 | Simple Broker `/topic`·`/queue`, 앱 프리픽스 `/app`, 사용자 프리픽스 `/user` |
| 메시지 전송 | `SEND /app/chat.send` (`{"chatId": ..., "text": "..."}`). `senderId`는 받지 않고 인증된 `Principal`에서 꺼낸다 |
| 채팅방 구독 | `SUBSCRIBE /topic/chat.{chatId}`. 활성 `chat_members` 참여자이면서 DM 차단 관계가 아닐 때만 허용한다 |
| 에러 수신 | `SUBSCRIBE /user/queue/errors`. 전송 검증·권한 오류(`ApiException`)를 `{code, message}`로 내려주어 소켓 세션을 유지한다 |

- **인증·인가 (`StompAuthChannelInterceptor`)**: `CONNECT` 프레임의 `Authorization: Bearer <token>`을 `JwtProvider.parseAccessToken`으로 검증해 `Principal`에 등록하고(토큰 원문 로깅 금지), `SUBSCRIBE`·`SEND` 단계에서 참여자 권한과 DM 양방향 차단을 검사한다. `SecurityConfig.java`는 수정하지 않았다
- **선저장 후 브로드캐스트**: `ChatService.sendMessage`(`@Transactional`)가 제재(`sanction >= 2`)·참여자·차단·본문(공백 금지, 1,000자 이하)을 검증하고 `messages` 저장(`saveAndFlush`) 및 보낸 사람 `last_read_at` 갱신을 커밋한 뒤, `ChatStompController`에서 `/topic/chat.{chatId}`로 `ChatBroadcastMessage`를 브로드캐스트한다
- **프론트**: `@stomp/stompjs`와 `vite.config.ts`의 `/ws`(`ws: true`) 프록시를 추가했다. `src/api/stomp.ts`가 `beforeConnect`에서 최신 토큰을 주입하고 만료 시 `refreshAccessToken()`으로 재연결하며, `Chat.tsx`(`ServerChatPanel`·`ChatList`)·`store.tsx`에서 실시간 메시지 수신·중복 제거·`myUserId` 기준 내 말풍선 판별·읽음 동기화·에러 토스트·로그아웃 시 소켓 종료를 처리한다

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
- [x] 10단계: 추천 API (`GET /recommend/people`, `GET /recommend/rooms`, derive.ts 이식, 차단 양방향 제외), 홈 사람 추천·프로필 화면 연결
- [x] 11단계: 모임 CRUD API (`GET/POST /rooms`, `GET /rooms/:id`, 필터·동적 상태 계산·채팅방 동시 생성·차단 제외), 모임 목록·상세·생성·추천 화면 연결
- [x] 12단계: 공통 알림 메서드(`NotificationService.notify`) 및 모임 합류·탈퇴·방장 관리 API (`POST /rooms/:id/join`, `/leave`, `/cancel`, `/host`, 동시성 제어·동시간대 중복 차단·1시간 전 취소 패널티·방장 자동 위임), 모임·마이페이지·인증 화면 연동
- [x] 13단계: 채팅 목록·메시지 페이징·읽음·오픈채팅 링크 API (`GET /chats`, `/chats/:id/messages`, `POST /chats/:id/read`, `PATCH /chats/:id/link`) 및 1:1 DM 요청·수락·거절 API (`/dm-requests`), 채팅·프로필·알림 화면 연결
- [x] 14단계: Spring WebSocket + STOMP 실시간 채팅 (`/ws/chat`, `/app/chat.send`, `/topic/chat.{chatId}`, `/user/queue/errors`), `ChannelInterceptor` JWT·구독 권한 검증, 커밋 후 브로드캐스트 및 `@stomp/stompjs` 프론트 연동