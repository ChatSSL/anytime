#!/usr/bin/env bash
# 내 컴퓨터(Git Bash)에서 저장소 루트 기준으로 실행한다.
#
#   ./deploy/deploy.sh setup   처음 한 번: 서버에 Java·Docker·MySQL·방화벽·서비스를 설치
#   ./deploy/deploy.sh reset   jar를 올리고 DB를 schema.sql + data.sql로 초기화 (첫 배포, 발표 직전)
#   ./deploy/deploy.sh         jar만 새로 올린다. DB 데이터는 그대로 (평소 배포)
#
# 서버 주소와 키 경로는 deploy/deploy.env 에 적는다 (deploy.env.example 참고)
set -euo pipefail

cd "$(dirname "$0")/.."

CONF=deploy/deploy.env
if [ ! -f "$CONF" ]; then
  echo "$CONF 가 없다. deploy/deploy.env.example 을 복사해서 서버 주소를 채운다:" >&2
  echo "  cp deploy/deploy.env.example deploy/deploy.env" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$CONF"
: "${SERVER_HOST:?deploy.env에 SERVER_HOST(서버 공인 IP)를 적는다}"
SERVER_USER=${SERVER_USER:-ubuntu}

SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10)
if [ -n "${SSH_KEY:-}" ]; then
  SSH_OPTS+=(-i "$SSH_KEY")
fi
TARGET="$SERVER_USER@$SERVER_HOST"
APP_DIR=/opt/anytime

remote() { ssh "${SSH_OPTS[@]}" "$TARGET" "$@"; }
upload() { scp "${SSH_OPTS[@]}" "$@"; }

MODE=${1:-deploy}

case "$MODE" in
  setup)
    echo "==> 서버 준비 파일 올리는 중"
    remote "mkdir -p /tmp/anytime-setup"
    upload deploy/setup-server.sh deploy/docker-compose.yml deploy/anytime.service "$TARGET:/tmp/anytime-setup/"
    echo "==> 서버에서 setup-server.sh 실행 (몇 분 걸린다)"
    remote "sudo bash /tmp/anytime-setup/setup-server.sh"
    exit 0
    ;;
  deploy|reset) ;;
  *)
    echo "사용법: ./deploy/deploy.sh [setup|reset]" >&2
    exit 1
    ;;
esac

echo "==> jar 빌드"
(cd backend && ./gradlew bootJar -q)
JAR=$(ls backend/build/libs/*.jar | grep -v -- '-plain\.jar$' | head -n 1)
echo "    $JAR"

echo "==> 서버로 올리는 중"
upload "$JAR" "$TARGET:$APP_DIR/anytime.jar.new"

# 이전 jar는 anytime.jar.bak 으로 남겨 둔다 (되돌릴 때 사용)
SWAP_JAR="cd $APP_DIR && { [ ! -f anytime.jar ] || cp anytime.jar anytime.jar.bak; } && mv anytime.jar.new anytime.jar"

if [ "$MODE" = reset ]; then
  echo "==> DB 초기화 모드로 재시작 (schema.sql + data.sql)"
  remote "$SWAP_JAR && sudo systemctl set-environment SQL_INIT_MODE=always && sudo systemctl restart anytime"
else
  echo "==> 재시작"
  remote "$SWAP_JAR && sudo systemctl restart anytime"
fi

echo "==> 서버가 뜰 때까지 기다리는 중 (최대 3분)"
if remote 'for i in $(seq 1 90); do curl -fs http://localhost/api/health >/dev/null && exit 0; sleep 2; done; exit 1'; then
  OK=1
else
  OK=0
fi

if [ "$MODE" = reset ]; then
  # 다음 재시작부터는 다시 데이터를 지우지 않도록 되돌린다
  remote "sudo systemctl unset-environment SQL_INIT_MODE"
fi

if [ "$OK" = 1 ]; then
  echo
  echo "배포 끝: http://$SERVER_HOST/api/health"
else
  echo >&2
  echo "서버가 3분 안에 뜨지 않았다. 마지막 로그:" >&2
  remote "sudo journalctl -u anytime -n 60 --no-pager" >&2 || true
  exit 1
fi
