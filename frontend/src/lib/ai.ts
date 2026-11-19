import type { Room, User } from '../types';

/**
 * 1차 구현: 관심사 문장을 "개념"으로 묶어 유사도를 계산하는 휴리스틱.
 * 실제 서비스에서는 이 파일의 similarity()를 임베딩 API 호출로 교체하면 된다.
 * 외부 AI API에는 시간표 원본을 보내지 않고 관심사 텍스트만 전달한다.
 */
const CONCEPTS: Record<string, string[]> = {
  보드게임: ['보드게임', '카탄', '뱅', '할리갈리', '보드'],
  게임: ['게임', '롤', '스팀'],
  카페공부: ['카페', '공부', '스터디', '독서', '책'],
  코딩: ['코딩', '개발', '프로그래밍'],
  산책: ['산책', '러닝', '운동', '걷기', '조깅'],
  식사: ['점심', '밥', '식사', '맛집'],
  영화: ['영화', '드라마', '시네마'],
  전시: ['전시', '미술', '사진', '스케치'],
  음악: ['음악', '노래', '밴드'],
  대화: ['수다', '언어교환', '대화', '토크'],
};

export function concepts(list: string[]): Set<string> {
  const out = new Set<string>();
  for (const s of list) for (const [c, keys] of Object.entries(CONCEPTS)) if (keys.some((k) => s.includes(k))) out.add(c);
  return out;
}

export function sharedConcepts(a: string[], b: string[]): string[] {
  const B = concepts(b);
  return [...concepts(a)].filter((c) => B.has(c));
}

export function similarity(a: string[], b: string[]): number {
  const A = concepts(a), B = concepts(b);
  const inter = [...A].filter((x) => B.has(x)).length;
  const uni = new Set([...A, ...B]).size;
  return uni ? inter / uni : 0;
}

/** 생성형 AI 자리: 참여자 공통 관심사와 활동 유형으로 대화 주제와 미션을 만든다. 모임당 한 번만 호출한다. */
export function icebreaker(room: Room, members: User[]): { questions: string[]; mission: string } {
  const shared = members.length ? members.map((m) => m.interests).reduce((acc, cur) => acc.filter((x) => concepts(cur).has(x) || cur.includes(x)), [...concepts(members[0].interests)] as string[]) : [];
  const topic = shared[0] ?? room.activity;
  const bank: Record<string, { q: string[]; m: string }> = {
    보드게임: { q: ['최근 가장 재밌게 한 보드게임은?', '이기고 싶은 편이에요, 즐기고 싶은 편이에요?'], m: '서로 이름 대신 닉네임 유래 맞히기' },
    카페공부: { q: ['요즘 가장 집중이 잘 되는 장소는 어디예요?', '공부할 때 꼭 필요한 음료는요?'], m: '오늘 끝낼 한 가지를 서로 말하고 마지막에 확인하기' },
    산책: { q: ['캠퍼스에서 제일 좋아하는 장소는요?', '걸으면서 듣기 좋은 노래 하나만 추천해 주세요.'], m: '10분 동안 안 가본 길로만 걸어보기' },
    식사: { q: ['학식 중에 최애 메뉴는요?', '이번 학기 가장 잘한 선택은 뭐예요?'], m: '서로 다른 메뉴를 시켜 한 입씩 나눠 먹기' },
    코딩: { q: ['요즘 가장 재밌게 본 개발 이야기가 있나요?', '처음 코드를 짰을 때 기억나세요?'], m: '각자 요즘 막힌 문제를 한 문장으로 설명하기' },
  };
  const pick = bank[topic] ?? { q: [`${room.activity}에서 제일 기대되는 건 뭐예요?`, '이번 학기 가장 기억에 남는 순간은요?'], m: '서로 공통점 세 가지 찾기' };
  return { questions: pick.q, mission: pick.m };
}
