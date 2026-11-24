# 서버 배포 (6단계)

서버 한 대에 MySQL(Docker)과 Spring jar를 같이 올리고 `http://공인IP` 로 접속한다. 도메인·HTTPS는 하지 않는다.

```
내 컴퓨터                               클라우드 서버 (Ubuntu 22.04, /opt/anytime)
./deploy/deploy.sh ── jar 복사(scp) ──▶  anytime.jar  ← systemd가 계속 켜 둠 (포트 80)
                                         .env         ← DB 비밀번호, JWT 비밀키 (서버에만 있음)
                                         MySQL 8.4    ← Docker, 127.0.0.1:3306 (밖에서 안 보임)
```

| 파일 | 하는 일 |
| --- | --- |
| [deploy/deploy.sh](../deploy/deploy.sh) | 내 컴퓨터에서 실행. 서버 준비(`setup`), 배포, DB 초기화 배포(`reset`) |
| [deploy/setup-server.sh](../deploy/setup-server.sh) | 서버에서 한 번 실행됨. Java 17, Docker, 방화벽, 스왑, `.env`, MySQL, 서비스 등록 |
| [deploy/anytime.service](../deploy/anytime.service) | jar를 계속 켜 두고, 죽거나 재부팅되면 다시 켜는 systemd 설정 |
| [deploy/docker-compose.yml](../deploy/docker-compose.yml) | 서버용 MySQL. 루트의 docker-compose.yml은 로컬용이다 |

## 처음 한 번: 서버 만들기

AWS EC2 기준이다. NCP도 순서는 같다 (사용자 이름만 `root`).

1. **EC2 인스턴스 시작**
   - 이미지: Ubuntu Server 22.04 LTS
   - 유형: t2.micro 또는 t3.micro (프리 티어)
   - 키 페어: 새로 만들기 → `anytime.pem` 다운로드 → `C:\Users\<내 이름>\.ssh\anytime.pem` 에 둔다
   - 스토리지: 20GB
2. **보안 그룹(방화벽) 인바운드 규칙**
   - SSH 22: 내 IP (팀원이 배포하면 그 사람 IP도 추가)
   - HTTP 80: 0.0.0.0/0 (모두)
   - 3306은 열지 않는다
3. **탄력적 IP 할당 → 인스턴스에 연결**: 이걸 안 하면 서버를 껐다 켤 때마다 IP가 바뀐다
4. **접속 확인** (Git Bash)
   ```bash
   ssh -i ~/.ssh/anytime.pem ubuntu@<공인IP>
   ```
   `ubuntu@ip-...:~$` 가 나오면 성공. `exit` 로 나온다

## 처음 한 번: 서버 준비와 첫 배포

저장소 루트에서 **Git Bash**로 한다 (PowerShell 아님).

```bash
cp deploy/deploy.env.example deploy/deploy.env
# deploy/deploy.env 를 열어 SERVER_HOST 에 공인 IP를 적는다

./deploy/deploy.sh setup    # 서버에 Java·Docker·MySQL 설치 (3~5분)
./deploy/deploy.sh reset    # jar를 올리고 테이블 + 시드 데이터를 넣는다
```

`배포 끝: http://<공인IP>/api/health` 가 나오면 브라우저에서 그 주소를 열어 `{"status":"ok"}` 를 확인한다. 휴대폰 LTE로도 열어 본다.

`/` 주소는 아직 404(Whitelabel Error Page)가 맞다. 프론트는 19단계에서 같이 올린다.

## 평소 배포 (3·4번 주자 포함)

```bash
git pull origin main
./deploy/deploy.sh          # 빌드 → 업로드 → 재시작 → 헬스 체크. DB 데이터는 그대로
```

| 하고 싶은 것 | 명령 |
| --- | --- |
| DB를 시드로 되돌리기 (발표 직전, schema.sql 바꾼 뒤) | `./deploy/deploy.sh reset` |
| 서버 로그 보기 | `ssh -i ~/.ssh/anytime.pem ubuntu@<IP> sudo journalctl -u anytime -f` |
| 서버 상태 보기 | `... sudo systemctl status anytime` |
| 직전 jar로 되돌리기 | 서버에서 `cd /opt/anytime && cp anytime.jar.bak anytime.jar && sudo systemctl restart anytime` |
| 서버 비밀값 고치기 (메일, JWT 등) | 서버에서 `nano /opt/anytime/.env` 후 `sudo systemctl restart anytime` |

schema.sql에 테이블이나 컬럼이 바뀐 PR이 머지되면 평소 배포로는 서버 DB에 반영되지 않는다 (서버는 `SQL_INIT_MODE=never`). 그때는 `reset` 으로 배포한다. 서버 데이터가 시드로 돌아간다.

## 서버 `.env`

`setup` 이 서버에 `/opt/anytime/.env` 를 만들고 DB 비밀번호와 `JWT_SECRET` 을 무작위로 채운다. 이 파일은 서버에만 있고 저장소·팀방에 올리지 않는다.

이메일 인증(5단계)을 서버에서 쓰려면 `MAIL_USERNAME`, `MAIL_APP_PASSWORD`, `EMAIL_HASH_KEY`, `EMAIL_ENC_KEY` 를 서버에서 직접 채운다.

## 막힐 때

| 증상 | 해결 |
| --- | --- |
| `UNPROTECTED PRIVATE KEY FILE` | PowerShell에서 `icacls $HOME\.ssh\anytime.pem /inheritance:r /grant:r "$($env:USERNAME):R"` |
| `Connection timed out` (ssh) | 보안 그룹 22번에 지금 내 IP가 있는지 확인 (집·학교 와이파이마다 IP가 다르다) |
| 브라우저에서 `/api/health` 가 안 열림 | 보안 그룹 80번 확인. 서버 안에서는 되는지 `ssh ... curl localhost/api/health` |
| `서버가 3분 안에 뜨지 않았다` | 함께 나온 로그의 맨 아래 `Caused by` 를 본다. `Communications link failure` 면 MySQL이 안 떠 있는 것: 서버에서 `cd /opt/anytime && sudo docker compose up -d` |
| 빌드 실패 | 먼저 로컬에서 `cd backend && ./gradlew bootJar` 가 되는지 본다 |
