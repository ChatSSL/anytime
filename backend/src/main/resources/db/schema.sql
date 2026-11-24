-- 로컬 실행 때마다 테이블을 지우고 다시 만든 뒤 data.sql 시드를 넣는다 (spring.sql.init.mode=always).
-- 데이터를 남기고 싶으면 SQL_INIT_MODE=never 환경 변수로 실행한다.
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS blocks;
DROP TABLE IF EXISTS cancel_log;
DROP TABLE IF EXISTS chat_members;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS chats;
DROP TABLE IF EXISTS dm_requests;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS ratings;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS room_change_votes;
DROP TABLE IF EXISTS room_changes;
DROP TABLE IF EXISTS room_members;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS class_blocks;
DROP TABLE IF EXISTS wanted_slots;
DROP TABLE IF EXISTS user_interests;
DROP TABLE IF EXISTS email_codes;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- Anytime DB 스키마 (MySQL 8.4, utf8mb4)
-- 기준: 프론트 src/types.ts, 연동 가이드 테이블 표
-- 시간은 프론트와 같이 요일(0=월 ~ 4=금) + 하루 중 분(0~1439) 정수로 저장한다.
-- 방 상태(모집 중, 마감 임박 등)는 저장하지 않고 조회 시점에 계산한다. rooms.status에는 되돌릴 수 없는 취소·종료만 넣는다.

SET NAMES utf8mb4;

-- 1. 사용자 ------------------------------------------------------------------

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email_hash    CHAR(64)     NOT NULL COMMENT '학교 이메일 HMAC-SHA256 (서버 비밀키 EMAIL_HASH_KEY). 로그인 조회·중복 검사용',
    email_enc     VARCHAR(255) NOT NULL COMMENT '학교 이메일 AES 암호문. 메일 보낼 때만 복호화',
    school        VARCHAR(50)  NOT NULL,
    nick          VARCHAR(20)  NOT NULL,
    dept          VARCHAR(50)  NULL,
    show_dept     BOOLEAN      NOT NULL DEFAULT TRUE,
    avatar        TINYINT      NOT NULL DEFAULT 0 COMMENT 'AVATAR_COLORS 인덱스',
    manner        DECIMAL(4,1) NOT NULL DEFAULT 36.5,
    completed     INT          NOT NULL DEFAULT 0 COMMENT '완료한 모임 수 (프론트 User.meetups / AppState.completed)',
    sanction      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 없음, 1 주의, 2 일시 정지, 3 영구 정지',
    settings      JSON         NULL     COMMENT '알림 종류별 on/off, ads, quiet, premium',
    verified_at   DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email_hash (email_hash),
    UNIQUE KEY uk_users_nick (nick)
);

-- 관심사. 피드백 반영: 관심사마다 공개 여부를 따로 둔다.
-- 추천 이유와 AI 미션 생성에는 is_public = TRUE인 것만 쓴다.
CREATE TABLE user_interests (
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(30) NOT NULL,
    is_public  BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, name),
    CONSTRAINT fk_interests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 2. 시간표 ------------------------------------------------------------------

CREATE TABLE class_blocks (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NULL,
    day        TINYINT     NOT NULL,
    start_min  SMALLINT    NOT NULL,
    end_min    SMALLINT    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_class_blocks_user (user_id),
    KEY idx_class_blocks_day_start (day, start_min),
    CONSTRAINT fk_class_blocks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_class_blocks_day  CHECK (day BETWEEN 0 AND 4),
    CONSTRAINT ck_class_blocks_time CHECK (start_min >= 0 AND end_min <= 1440 AND start_min < end_min)
);

-- 만나고 싶은 공강 (프론트 wanted: "day-start-end" -> true)
CREATE TABLE wanted_slots (
    user_id    BIGINT   NOT NULL,
    day        TINYINT  NOT NULL,
    start_min  SMALLINT NOT NULL,
    end_min    SMALLINT NOT NULL,
    PRIMARY KEY (user_id, day, start_min, end_min),
    KEY idx_wanted_day_start (day, start_min),
    CONSTRAINT fk_wanted_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_wanted_day  CHECK (day BETWEEN 0 AND 4),
    CONSTRAINT ck_wanted_time CHECK (start_min >= 0 AND end_min <= 1440 AND start_min < end_min)
);

-- 3. 모임 --------------------------------------------------------------------

CREATE TABLE rooms (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    host_id     BIGINT       NOT NULL,
    title       VARCHAR(50)  NOT NULL,
    activity    VARCHAR(20)  NOT NULL COMMENT 'ACTIVITIES 중 하나. 모임 목적(친목·취미·스터디) 표시에도 쓴다',
    day         TINYINT      NOT NULL,
    start_min   SMALLINT     NOT NULL,
    end_min     SMALLINT     NOT NULL,
    meet_date   DATE         NOT NULL COMMENT '실제 모이는 날짜. 만들 때 서버가 day로부터 가장 가까운 날짜를 계산해 넣는다',
    place       VARCHAR(50)  NOT NULL,
    cap         TINYINT      NOT NULL,
    topic       VARCHAR(200) NULL,
    official    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '운영팀이 연 모임',
    status      ENUM('OPEN','CANCELED','DONE') NOT NULL DEFAULT 'OPEN',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_rooms_day_start (day, start_min),
    KEY idx_rooms_meet_date (meet_date, status),
    KEY idx_rooms_host (host_id),
    CONSTRAINT fk_rooms_host FOREIGN KEY (host_id) REFERENCES users (id),
    CONSTRAINT ck_rooms_day  CHECK (day BETWEEN 0 AND 4),
    CONSTRAINT ck_rooms_time CHECK (start_min >= 0 AND end_min <= 1440 AND start_min < end_min),
    CONSTRAINT ck_rooms_cap  CHECK (cap BETWEEN 2 AND 20)
);

-- 다시 합류하면 같은 행의 left_at을 NULL로 되돌린다.
CREATE TABLE room_members (
    room_id       BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    joined_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at       DATETIME     NULL,
    leave_reason  VARCHAR(100) NULL,
    PRIMARY KEY (room_id, user_id),
    KEY idx_room_members_user (user_id),
    CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 방장의 일정 변경 제안
CREATE TABLE room_changes (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    room_id     BIGINT      NOT NULL,
    new_day     TINYINT     NOT NULL,
    new_start   SMALLINT    NOT NULL,
    new_end     SMALLINT    NOT NULL,
    new_place   VARCHAR(50) NULL,
    status      ENUM('PENDING','APPLIED','REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_room_changes_room (room_id),
    CONSTRAINT fk_room_changes_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT ck_room_changes_day  CHECK (new_day BETWEEN 0 AND 4),
    CONSTRAINT ck_room_changes_time CHECK (new_start >= 0 AND new_end <= 1440 AND new_start < new_end)
);

CREATE TABLE room_change_votes (
    change_id   BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    accepted    BOOLEAN  NOT NULL,
    voted_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (change_id, user_id),
    CONSTRAINT fk_votes_change FOREIGN KEY (change_id) REFERENCES room_changes (id) ON DELETE CASCADE,
    CONSTRAINT fk_votes_user   FOREIGN KEY (user_id)   REFERENCES users (id) ON DELETE CASCADE
);

-- 4. 채팅 --------------------------------------------------------------------

-- kind = 'ROOM'이면 room_id가 있고, 'DM'이면 room_id가 NULL이고 chat_members 두 명으로 상대를 찾는다.
CREATE TABLE chats (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    kind          ENUM('ROOM','DM') NOT NULL,
    room_id       BIGINT       NULL,
    mission       JSON         NULL     COMMENT 'AI가 만든 대화 주제·미션 {questions: [...], mission: "..."}',
    mission_done  BOOLEAN      NOT NULL DEFAULT FALSE,
    link          VARCHAR(255) NULL     COMMENT '오픈채팅 링크 대체',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chats_room (room_id),
    CONSTRAINT fk_chats_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
);

-- 안 읽은 수는 last_read_at 이후 메시지 수로 계산한다.
CREATE TABLE chat_members (
    chat_id       BIGINT   NOT NULL,
    user_id       BIGINT   NOT NULL,
    last_read_at  DATETIME NULL,
    PRIMARY KEY (chat_id, user_id),
    KEY idx_chat_members_user (user_id),
    CONSTRAINT fk_chat_members_chat FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE messages (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    chat_id     BIGINT        NOT NULL,
    sender_id   BIGINT        NULL COMMENT 'NULL이면 시스템 메시지',
    text        VARCHAR(1000) NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_messages_chat_created (chat_id, created_at),
    CONSTRAINT fk_messages_chat   FOREIGN KEY (chat_id)   REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE SET NULL
);

-- 1:1 요청 (requestDm(userId, slot, activity, message))
CREATE TABLE dm_requests (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    from_id     BIGINT       NOT NULL,
    to_id       BIGINT       NOT NULL,
    day         TINYINT      NOT NULL,
    start_min   SMALLINT     NOT NULL,
    end_min     SMALLINT     NOT NULL,
    activity    VARCHAR(20)  NULL,
    message     VARCHAR(200) NULL,
    status      ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'PENDING',
    chat_id     BIGINT       NULL COMMENT '수락되어 만들어진 DM 채팅방. 같은 두 사람의 DM 중복 생성 확인용',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dm_requests_to (to_id, status),
    KEY idx_dm_requests_from (from_id),
    CONSTRAINT fk_dm_requests_from FOREIGN KEY (from_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_requests_to   FOREIGN KEY (to_id)   REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_requests_chat FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE SET NULL
    -- 자기 자신에게 요청 금지는 서버에서 검사 (MySQL은 ON DELETE 동작이 걸린 FK 컬럼에 CHECK를 못 건다)
);

-- 5. 알림 --------------------------------------------------------------------

CREATE TABLE notifications (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    type             ENUM('RECO','JOIN','REQUEST','REMINDER','CHANGE','CANCEL','RATE') NOT NULL,
    title            VARCHAR(50)  NOT NULL,
    body             VARCHAR(200) NOT NULL,
    link             VARCHAR(255) NULL,
    related_user_id  BIGINT       NULL COMMENT '프론트 Notif.userId',
    needs_action     BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved         ENUM('ACCEPTED','DECLINED') NULL,
    read_at          DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notifications_user_created (user_id, created_at),
    CONSTRAINT fk_notifications_user    FOREIGN KEY (user_id)         REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_related FOREIGN KEY (related_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- 6. 참석·평가 ----------------------------------------------------------------

-- confirmed_by가 user_id의 참석을 확인했다는 기록. 참석자 과반이 확인하면 모임 완료.
CREATE TABLE attendance (
    room_id       BIGINT   NOT NULL,
    user_id       BIGINT   NOT NULL,
    confirmed_by  BIGINT   NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id, confirmed_by),
    CONSTRAINT fk_attendance_room      FOREIGN KEY (room_id)      REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_user      FOREIGN KEY (user_id)      REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_confirmer FOREIGN KEY (confirmed_by) REFERENCES users (id) ON DELETE CASCADE
);

-- 평가자는 상대에게 공개하지 않는다.
-- 피드백 반영: 평가는 바로 매너 점수에 넣지 않고, 크론이 모아서 반영한 뒤 applied_at을 채운다.
CREATE TABLE ratings (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    room_id     BIGINT       NOT NULL,
    rater_id    BIGINT       NOT NULL,
    target_id   BIGINT       NOT NULL,
    score       TINYINT      NOT NULL,
    reason      VARCHAR(50)  NULL COMMENT '2점 이하면 필수 (서버에서 검사)',
    note        VARCHAR(200) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at  DATETIME     NULL COMMENT '매너 점수에 반영된 시각. NULL이면 아직 대기',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ratings_once (room_id, rater_id, target_id),
    KEY idx_ratings_target_applied (target_id, applied_at),
    CONSTRAINT fk_ratings_room   FOREIGN KEY (room_id)   REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_ratings_rater  FOREIGN KEY (rater_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ratings_target FOREIGN KEY (target_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_ratings_score  CHECK (score BETWEEN 1 AND 5)
    -- 자기 자신 평가 금지는 서버에서 검사
);

-- 7. 안전 --------------------------------------------------------------------

-- 양방향 필터: 추천·채팅에서 blocker_id 또는 blocked_id 어느 쪽이든 걸리면 서로 숨긴다.
CREATE TABLE blocks (
    blocker_id  BIGINT   NOT NULL,
    blocked_id  BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    KEY idx_blocks_blocked (blocked_id),
    CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id) ON DELETE CASCADE
    -- 자기 자신 차단 금지는 서버에서 검사
);

CREATE TABLE reports (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id  BIGINT        NOT NULL,
    target_id    BIGINT        NOT NULL,
    room_id      BIGINT        NULL,
    reason       VARCHAR(50)   NOT NULL,
    detail       VARCHAR(1000) NULL,
    emergency    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_reports_target (target_id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_target   FOREIGN KEY (target_id)   REFERENCES users (id),
    CONSTRAINT fk_reports_room     FOREIGN KEY (room_id)     REFERENCES rooms (id) ON DELETE SET NULL
);

-- 매너 점수 차감 근거
CREATE TABLE cancel_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    room_id     BIGINT       NULL,
    reason      VARCHAR(100) NOT NULL,
    penalty     DECIMAL(3,1) NOT NULL DEFAULT 0.0 COMMENT '차감한 매너 점수 (0이면 차감 없음)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cancel_log_user (user_id),
    CONSTRAINT fk_cancel_log_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cancel_log_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE SET NULL
);

-- 8. 인증 --------------------------------------------------------------------

-- 발송 시 insert(같은 이메일이면 덮어쓰기), 검증 성공 시 삭제. 10분 만료, 5회 실패 시 재발송 필요.
-- 이메일 원문은 저장하지 않고 해시(users.email_hash와 같은 HMAC)만 둔다. 코드도 해시로 저장한다.
CREATE TABLE email_codes (
    email_hash  CHAR(64)  NOT NULL,
    code_hash   CHAR(64)  NOT NULL,
    fail_count  TINYINT   NOT NULL DEFAULT 0,
    expires_at  DATETIME  NOT NULL,
    created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (email_hash)
);

-- 리프레시 토큰 (7단계). 로그아웃·재발급 때 지워서 다시 못 쓰게 한다.
-- 토큰 원문은 저장하지 않고 SHA-256 해시만 둔다. 액세스 토큰은 저장하지 않는다(15분 만료).
CREATE TABLE refresh_tokens (
    token_hash  CHAR(64)  NOT NULL,
    user_id     BIGINT    NOT NULL,
    expires_at  DATETIME  NOT NULL,
    created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
