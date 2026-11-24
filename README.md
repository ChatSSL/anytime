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
- [ ] 4단계: 공통 틀 (예외 응답 `{code, message}`, Swagger)
- [ ] 5단계: 이메일 인증
- [ ] 6단계: 서버 배포 ([docs/deploy.md](docs/deploy.md)). 배포는 Git Bash에서 `./deploy/deploy.sh`

`SecurityConfig`는 지금 모든 요청을 허용하는 임시 설정이다. 7단계에서 JWT 설정으로 교체한다.
