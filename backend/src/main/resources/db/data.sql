-- 시연용 시드 데이터. 프론트 src/data.ts를 그대로 옮겼다.
-- id 1 = 데모유저: 로그인(7단계)이 붙기 전까지 '현재 사용자'로 쓴다.
-- 시드 사용자는 이메일 로그인을 하지 않는다. email_hash는 HMAC 대신 겹치지 않는 자리표시 값, email_enc는 'SEED:'를 붙인 원문.
-- 실제 가입자는 5단계에서 HMAC 해시와 AES 암호문으로 저장한다.
-- rooms.meet_date는 시드를 넣는 날 기준으로 가장 가까운 그 요일(오늘 포함)로 계산한다.

-- 사용자 (프론트 id -> DB id: me=1, u1=2, u2=3, u3=4, u4=5, u5=6, u6=7, u7=8, u8=9, staff=10)
INSERT INTO users (id, email_hash, email_enc, school, nick, dept, show_dept, avatar, manner, completed, sanction, settings, verified_at) VALUES
(1, LPAD('seed-me', 64, '0'), 'SEED:me@dankook.ac.kr', '단국대학교', '데모유저', '소프트웨어학과', TRUE, 0, 36.8, 3, 0, '{"notif": {"reco": true, "reminder": true, "change": true, "chat": true, "rate": true}, "ads": true, "quiet": false, "premium": false}', NOW()),
(2, LPAD('seed-u1', 64, '0'), 'SEED:u1@dankook.ac.kr', '단국대학교', '민트초코', '컴퓨터공학과', TRUE, 1, 37.2, 8, 0, NULL, NOW()),
(3, LPAD('seed-u2', 64, '0'), 'SEED:u2@dankook.ac.kr', '단국대학교', '라떼한잔', '디자인학과', TRUE, 2, 36.9, 5, 0, NULL, NOW()),
(4, LPAD('seed-u3', 64, '0'), 'SEED:u3@dankook.ac.kr', '단국대학교', '코딩하는곰', '소프트웨어학과', TRUE, 4, 36.5, 11, 0, NULL, NOW()),
(5, LPAD('seed-u4', 64, '0'), 'SEED:u4@dankook.ac.kr', '단국대학교', '새벽러닝', '체육학과', TRUE, 3, 38.0, 14, 0, NULL, NOW()),
(6, LPAD('seed-u5', 64, '0'), 'SEED:u5@dankook.ac.kr', '단국대학교', '영화광하루', '경영학과', TRUE, 5, 36.6, 6, 0, NULL, NOW()),
(7, LPAD('seed-u6', 64, '0'), 'SEED:u6@dankook.ac.kr', '단국대학교', '카탄마스터', '수학과', TRUE, 7, 37.8, 17, 0, NULL, NOW()),
(8, LPAD('seed-u7', 64, '0'), 'SEED:u7@dankook.ac.kr', '단국대학교', '어학당', '영어영문학과', TRUE, 6, 36.4, 4, 0, NULL, NOW()),
(9, LPAD('seed-u8', 64, '0'), 'SEED:u8@dankook.ac.kr', '단국대학교', '연필한자루', '건축학과', TRUE, 0, 36.7, 3, 0, NULL, NOW()),
(10, LPAD('seed-staff', 64, '0'), 'SEED:staff@dankook.ac.kr', '단국대학교', '운영팀', NULL, TRUE, 7, 40, 30, 0, NULL, NOW());

INSERT INTO user_interests (user_id, name, is_public) VALUES
(1, '코딩', TRUE),
(1, '보드게임', TRUE),
(1, '카페공부', TRUE),
(2, '보드게임', TRUE),
(2, '카페공부', TRUE),
(2, '코딩', TRUE),
(3, '산책', TRUE),
(3, '사진', TRUE),
(3, '전시', TRUE),
(3, '카페공부', TRUE),
(4, '코딩', TRUE),
(4, '스터디', TRUE),
(4, '게임', TRUE),
(4, '카탄 같은 보드게임', TRUE),
(5, '운동', TRUE),
(5, '산책', TRUE),
(5, '점심', TRUE),
(6, '영화', TRUE),
(6, '수다', TRUE),
(6, '전시', TRUE),
(6, '독서', TRUE),
(7, '보드게임', TRUE),
(7, '게임', TRUE),
(7, '스터디', TRUE),
(7, '점심', TRUE),
(8, '언어교환', TRUE),
(8, '독서', TRUE),
(8, '카페공부', TRUE),
(8, '수다', TRUE),
(9, '스케치', TRUE),
(9, '전시', TRUE),
(9, '음악', TRUE),
(9, '산책', TRUE),
(10, '점심', TRUE),
(10, '보드게임', TRUE),
(10, '산책', TRUE);

INSERT INTO class_blocks (user_id, name, day, start_min, end_min) VALUES
(1, '자료구조', 0, 540, 630),
(1, '운영체제', 0, 780, 870),
(1, '인공지능융합플랫폼', 1, 630, 720),
(1, '소프트웨어공학', 1, 900, 990),
(1, '데이터베이스', 2, 630, 720),
(1, '네트워크', 2, 840, 930),
(1, '알고리즘', 3, 540, 630),
(1, '클라우드', 3, 780, 870),
(1, '운영체제보안', 4, 630, 720),
(1, '캡스톤', 4, 840, 930),
(2, NULL, 0, 540, 660),
(2, NULL, 0, 840, 930),
(2, NULL, 2, 630, 720),
(2, NULL, 2, 840, 930),
(2, NULL, 4, 630, 720),
(2, NULL, 4, 840, 960),
(3, NULL, 1, 540, 720),
(3, NULL, 1, 900, 990),
(3, NULL, 2, 540, 720),
(3, NULL, 2, 900, 1020),
(3, NULL, 3, 630, 720),
(3, NULL, 3, 810, 900),
(4, NULL, 0, 600, 690),
(4, NULL, 0, 810, 900),
(4, NULL, 2, 630, 720),
(4, NULL, 2, 780, 870),
(4, NULL, 3, 540, 630),
(4, NULL, 3, 780, 870),
(5, NULL, 1, 630, 720),
(5, NULL, 1, 840, 930),
(5, NULL, 3, 630, 720),
(5, NULL, 3, 840, 930),
(5, NULL, 4, 540, 660),
(5, NULL, 4, 780, 870),
(6, NULL, 0, 540, 630),
(6, NULL, 0, 780, 870),
(6, NULL, 2, 540, 660),
(6, NULL, 2, 840, 930),
(6, NULL, 4, 630, 720),
(6, NULL, 4, 810, 900),
(7, NULL, 2, 630, 720),
(7, NULL, 2, 840, 960),
(7, NULL, 1, 540, 630),
(7, NULL, 1, 780, 870),
(8, NULL, 0, 630, 720),
(8, NULL, 0, 840, 930),
(8, NULL, 3, 630, 720),
(8, NULL, 3, 840, 930),
(8, NULL, 2, 540, 660),
(8, NULL, 2, 840, 930),
(9, NULL, 1, 630, 720),
(9, NULL, 1, 870, 960),
(9, NULL, 4, 540, 660),
(9, NULL, 4, 810, 900);

-- 만나고 싶은 공강 (9단계). 프론트는 공강을 기본으로 '만나고 싶어요'로 두므로, 시드 사용자 모두 수업 사이 공강
-- (같은 요일 수업 사이 60분 이상, 프론트 freeSlots)을 전부 넣는다.
-- 10단계 추천은 나와 상대 둘 다 이 목록만 쓴다. 그래서 프론트 derive.ts(상대는 freeSlots 전체)와 결과가 같다.
INSERT INTO wanted_slots (user_id, day, start_min, end_min) VALUES
(1, 0, 630, 780),
(1, 1, 720, 900),
(1, 2, 720, 840),
(1, 3, 630, 780),
(1, 4, 720, 840),
(2, 0, 660, 840),
(2, 2, 720, 840),
(2, 4, 720, 840),
(3, 1, 720, 900),
(3, 2, 720, 900),
(3, 3, 720, 810),
(4, 0, 690, 810),
(4, 2, 720, 780),
(4, 3, 630, 780),
(5, 1, 720, 840),
(5, 3, 720, 840),
(5, 4, 660, 780),
(6, 0, 630, 780),
(6, 2, 660, 840),
(6, 4, 720, 810),
(7, 1, 630, 780),
(7, 2, 720, 840),
(8, 0, 720, 840),
(8, 2, 660, 840),
(8, 3, 720, 840),
(9, 1, 720, 870),
(9, 4, 660, 810);

-- 모임 (프론트 id -> DB id: r1=1, r2=2, r3=3, r4=4, r5=5, r6=6, r9=7, r11=8, r12=9, r7=10)
INSERT INTO rooms (id, host_id, title, activity, day, start_min, end_min, meet_date, place, cap, topic, official, status) VALUES
(1, 7, '점심 보드게임 같이 해요', '보드게임', 2, 720, 810, DATE_ADD(CURDATE(), INTERVAL MOD(2 - WEEKDAY(CURDATE()) + 7, 7) DAY), '학생회관 1층 라운지', 6, '카탄, 뱅 같은 가벼운 게임', FALSE, 'OPEN'),
(2, 3, '공강 산책', '산책', 2, 750, 810, DATE_ADD(CURDATE(), INTERVAL MOD(2 - WEEKDAY(CURDATE()) + 7, 7) DAY), '중앙 잔디', 4, '30분만 가볍게 캠퍼스 한 바퀴', FALSE, 'OPEN'),
(3, 8, '카페공부 같이 해요', '카페공부', 2, 720, 840, DATE_ADD(CURDATE(), INTERVAL MOD(2 - WEEKDAY(CURDATE()) + 7, 7) DAY), '도서관 앞 카페', 6, '각자 공부, 마지막 10분은 수다', FALSE, 'OPEN'),
(4, 6, '영화 얘기 수다', '수다', 3, 720, 780, DATE_ADD(CURDATE(), INTERVAL MOD(3 - WEEKDAY(CURDATE()) + 7, 7) DAY), '학생회관 1층 라운지', 5, '요즘 본 영화 추천 릴레이', FALSE, 'OPEN'),
(5, 10, '점심 같이 먹어요', '점심', 3, 690, 750, DATE_ADD(CURDATE(), INTERVAL MOD(3 - WEEKDAY(CURDATE()) + 7, 7) DAY), '학생식당', 8, '운영팀이 먼저 열어 둔 점심 모임', TRUE, 'OPEN'),
(6, 7, '금요일 보드게임', '보드게임', 4, 720, 810, DATE_ADD(CURDATE(), INTERVAL MOD(4 - WEEKDAY(CURDATE()) + 7, 7) DAY), '학생회관 1층 라운지', 6, '신입 환영, 룰은 알려드려요', FALSE, 'OPEN'),
(7, 6, '공강 수다', '수다', 2, 660, 720, DATE_ADD(CURDATE(), INTERVAL MOD(2 - WEEKDAY(CURDATE()) + 7, 7) DAY), '정문 앞 광장', 4, '잠깐 쉬어가는 수다', FALSE, 'OPEN'),
(8, 4, '게임 한판', '게임', 2, 720, 780, DATE_ADD(CURDATE(), INTERVAL MOD(2 - WEEKDAY(CURDATE()) + 7, 7) DAY), '공대 로비', 3, '모바일 게임 협동', FALSE, 'OPEN'),
(9, 10, '운영팀 산책', '산책', 2, 750, 810, DATE_ADD(CURDATE(), INTERVAL MOD(2 - WEEKDAY(CURDATE()) + 7, 7) DAY), '정문 앞 광장', 8, '운영팀과 같이 한 바퀴', TRUE, 'OPEN'),
(10, 8, '월요일 카페공부', '카페공부', 0, 720, 780, DATE_ADD(CURDATE(), INTERVAL MOD(0 - WEEKDAY(CURDATE()) + 7, 7) DAY), '도서관 앞 카페', 6, '시험 대비 조용히', FALSE, 'OPEN');

INSERT INTO room_members (room_id, user_id) VALUES
(1, 7),
(1, 4),
(1, 2),
(2, 3),
(2, 5),
(3, 8),
(3, 4),
(3, 6),
(3, 2),
(4, 6),
(5, 10),
(6, 7),
(6, 4),
(7, 6),
(7, 8),
(8, 4),
(8, 7),
(8, 5),
(9, 10),
(10, 1),
(10, 8),
(10, 3);

-- 모임마다 모임방 채팅 하나 (chats.id = rooms.id)
INSERT INTO chats (id, kind, room_id) VALUES
(1, 'ROOM', 1),
(2, 'ROOM', 2),
(3, 'ROOM', 3),
(4, 'ROOM', 4),
(5, 'ROOM', 5),
(6, 'ROOM', 6),
(7, 'ROOM', 7),
(8, 'ROOM', 8),
(9, 'ROOM', 9),
(10, 'ROOM', 10);

INSERT INTO chat_members (chat_id, user_id, last_read_at) VALUES
(1, 7, NOW()),
(1, 4, NOW()),
(1, 2, NOW()),
(2, 3, NOW()),
(2, 5, NOW()),
(3, 8, NOW()),
(3, 4, NOW()),
(3, 6, NOW()),
(3, 2, NOW()),
(4, 6, NOW()),
(5, 10, NOW()),
(6, 7, NOW()),
(6, 4, NOW()),
(7, 6, NOW()),
(7, 8, NOW()),
(8, 4, NOW()),
(8, 7, NOW()),
(8, 5, NOW()),
(9, 10, NOW()),
(10, 1, NOW()),
(10, 8, NOW()),
(10, 3, NOW());

-- r7 월요일 카페공부 채팅 (프론트 chat-r7)
INSERT INTO messages (chat_id, sender_id, text) VALUES
(10, NULL, '어학당님이 모임방을 열었어요'),
(10, 8, '조용히 공부하다가 끝나기 10분 전에 수다 떨어요!');

-- 민트초코님이 데모유저에게 보낸 1:1 요청 (프론트 알림 n2)
INSERT INTO dm_requests (id, from_id, to_id, day, start_min, end_min, activity, message, status) VALUES
(1, 2, 1, 2, 720, 840, '보드게임', '수요일 공강에 보드게임 같이 해요', 'PENDING');

-- 데모유저 알림 (프론트 n1~n3)
INSERT INTO notifications (user_id, type, title, body, link, related_user_id, needs_action) VALUES
(1, 'RECO', '새 추천', '수 12:00 공강이 맞는 모임 3개가 열렸어요', '/rooms', NULL, FALSE),
(1, 'REQUEST', '1:1 요청이 왔어요', '민트초코님이 수 12:00–14:00에 보드게임을 같이 하고 싶어 해요', NULL, 2, TRUE),
(1, 'RATE', '평가 요청', '월요일 카페공부 모임은 어땠나요?', '/review/10', NULL, FALSE);
