#!/usr/bin/env bash
# 서버(Ubuntu 22.04)에서 처음 한 번만 실행하는 준비 스크립트.
# 직접 실행하지 않아도 된다: 내 컴퓨터에서 ./deploy/deploy.sh setup 을 하면 서버로 복사해서 돌린다.
# 여러 번 실행해도 안전하다 (이미 된 것은 건너뛴다. .env는 있으면 덮어쓰지 않는다)
set -euo pipefail

if [ "$(id -u)" != 0 ]; then
  echo "sudo로 실행해야 한다: sudo bash $0" >&2
  exit 1
fi

APP_DIR=/opt/anytime
OWNER=${SUDO_USER:-root}          # jar를 scp로 올릴 SSH 사용자 (EC2는 ubuntu)
SRC_DIR=$(cd "$(dirname "$0")" && pwd)

echo "[1/7] 시간대를 Asia/Seoul로"
timedatectl set-timezone Asia/Seoul

echo "[2/7] 스왑 2GB (메모리 1GB 서버에서 Java + MySQL이 같이 돌도록)"
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
fi
swapon --show | grep -q /swapfile || swapon /swapfile
grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab

echo "[3/7] Java 17, curl, openssl 설치"
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jre-headless curl openssl

echo "[4/7] Docker 설치"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "[5/7] 방화벽: 22(SSH)와 80(웹)만 연다"
if command -v ufw >/dev/null; then
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw --force enable
fi

echo "[6/7] $APP_DIR 폴더, .env, MySQL"
id anytime >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin anytime
mkdir -p "$APP_DIR"
cp "$SRC_DIR/docker-compose.yml" "$APP_DIR/docker-compose.yml"
chown "$OWNER":"$OWNER" "$APP_DIR"

if [ ! -f "$APP_DIR/.env" ]; then
  # 비밀번호는 서버에서 무작위로 만든다. 이 값은 서버 밖으로 나가지 않는다
  cat > "$APP_DIR/.env" <<EOF
# 서버 전용 비밀 정보. 저장소에 올리지 않는다
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=anytime
DB_USER=anytime
DB_PASSWORD=$(openssl rand -hex 16)
DB_ROOT_PASSWORD=$(openssl rand -hex 16)

# 5단계 이메일 인증: 팀방에서 공유한 값으로 채운다
EMAIL_HASH_KEY=
EMAIL_ENC_KEY=
MAIL_USERNAME=
MAIL_APP_PASSWORD=

# 7단계 JWT
JWT_SECRET=$(openssl rand -hex 32)

# AI 연동 (선택)
AI_API_KEY=
EOF
  echo "  .env를 새로 만들었다: $APP_DIR/.env"
else
  echo "  .env가 이미 있어서 그대로 둔다"
fi
chown "$OWNER":"$OWNER" "$APP_DIR/.env"
chmod 600 "$APP_DIR/.env"

(cd "$APP_DIR" && docker compose up -d)

echo "[7/7] systemd 서비스 등록"
cp "$SRC_DIR/anytime.service" /etc/systemd/system/anytime.service
systemctl daemon-reload
systemctl enable anytime
# jar는 아직 없으니 여기서 켜지 않는다. deploy.sh가 jar를 올리고 켠다

echo
echo "서버 준비 끝. 이제 내 컴퓨터에서: ./deploy/deploy.sh reset"
