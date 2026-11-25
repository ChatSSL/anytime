// 추천 이식 확인용 (10단계). derive.ts를 시드 data.sql과 같은 입력으로 돌려 사람·모임 순서와 점수를 찍는다.
// 백엔드 RecommendServiceTest의 기대값이 이 출력이다. derive.ts나 점수식을 바꾸면 다시 돌려서 테스트 값도 같이 고친다.
//
// 실행 (frontend 폴더에서):
//   npx esbuild scripts/recommend-parity.ts --bundle --platform=node --format=esm --outfile=node_modules/.tmp/parity.mjs
//   node node_modules/.tmp/parity.mjs
//
// 실제 서버와 비교하려면 dev-login(userId 1) 토큰으로 GET /api/recommend/people 를 불러 사람 순서·score를 맞춰 본다.
// (모임은 서버가 실제 지금 시각을 쓰므로 수요일 10:40이 아니면 상태가 다르게 나온다)
import { initialState, EXAMPLE_TIMETABLE } from '../src/data';
import { parseClasses } from '../src/lib/time';
import { recommendPeople, recommendRooms } from '../src/lib/derive';

// 프론트 id → 시드 DB id (data.sql 주석과 같다)
const ID: Record<string, number> = { me: 1, u1: 2, u2: 3, u3: 4, u4: 5, u5: 6, u6: 7, u7: 8, u8: 9, staff: 10 };
const RID: Record<string, number> = { r1: 1, r2: 2, r3: 3, r4: 4, r5: 5, r6: 6, r9: 7, r11: 8, r12: 9, r7: 10 };

const s = initialState();
// 데모유저(id 1). 관심사 순서는 서버 GET /me가 주는 이름순
s.profile = { nick: '데모유저', dept: '소프트웨어학과', showDept: true, avatar: 0, interests: ['보드게임', '카페공부', '코딩'] };
s.classes = parseClasses(EXAMPLE_TIMETABLE);
s.clock = { day: 2, min: 640 }; // 수 10:40

const run = (label: string) => {
  console.log(`## ${label}`);
  console.log('people:');
  for (const p of recommendPeople(s)) {
    console.log(`  ${ID[p.user.id]} score=${p.score} slots=${JSON.stringify(p.slots.map((x) => [x.day, x.start, x.end]))} reason=${p.reason}`);
  }
  console.log('rooms:');
  for (const r of recommendRooms(s)) console.log(`  ${RID[r.room.id]} status=${r.status} fit=${r.fit}`);
};

run('기본');
s.blocks = ['u1', 'u6']; // 민트초코(2), 카탄마스터(7) 차단 → 카탄마스터가 방장인 모임 1, 6도 빠진다
run('2, 7 차단');
