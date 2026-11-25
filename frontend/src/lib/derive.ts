import { AppState, PersonReco, Room, Slot, User } from '../types';
import { AVATAR_COLORS, USERS } from '../data';
import { abs, freeSlots, intersectAll, nextFreeSlot, overlap, slotKey } from './time';
import { sharedConcepts, similarity } from './ai';

export const ME_ID = 'me';

export function meUser(s: AppState): User {
  return {
    id: ME_ID, nick: s.profile?.nick || '나', dept: s.profile?.showDept ? s.profile.dept : undefined,
    color: AVATAR_COLORS[s.profile?.avatar ?? 0], interests: s.profile?.interests ?? [],
    manner: s.manner, meetups: s.completed, classes: s.classes,
  };
}

/** 'me', 시드 사용자('u1'), 서버 추천으로 받은 사용자(숫자 id '4') 순서로 찾는다 */
export const userById = (s: AppState, id: string): User | undefined =>
  id === ME_ID ? meUser(s) : USERS.find((u) => u.id === id) ?? s.serverPeople?.find((p) => p.user.id === id)?.user;

export const nowMin = (s: AppState) => abs(s.clock.day, s.clock.min);

/** 내 공강 중 "만나고 싶어요"가 켜진 것만. 기본값은 켜짐. */
export const wantedSlots = (s: AppState): Slot[] => freeSlots(s.classes).filter((x) => s.wanted[slotKey(x)] !== false);

export type RoomStatus = 'open' | 'soon' | 'closed' | 'full' | 'ended' | 'cancelled';

/** 모집 마감: 시작 30분 전. 인원 미달: 시작 시점에 2명 미만이면 자동 취소(운영팀 모임 제외). */
export function roomStatus(r: Room, s: AppState): RoomStatus {
  const now = nowMin(s), start = abs(r.day, r.start), end = abs(r.day, r.end);
  if (!r.official && r.members.length < 2 && now >= start) return 'cancelled';
  if (now >= end) return 'ended';
  if (r.members.length >= r.cap) return 'full';
  if (now >= start - 30) return 'closed';
  if (now >= start - 60) return 'soon';
  return 'open';
}

export const STATUS_LABEL: Record<RoomStatus, string> = {
  open: '모집 중', soon: '마감 임박', closed: '마감', full: '정원 마감', ended: '종료', cancelled: '취소됨',
};
export const joinable = (st: RoomStatus) => st === 'open' || st === 'soon';

export function roomFit(r: Room, s: AppState): { pct: number; slot?: Slot } {
  const dur = r.end - r.start;
  let best = 0, bestSlot: Slot | undefined;
  for (const f of wantedSlots(s)) {
    const o = overlap(f, { day: r.day, start: r.start, end: r.end });
    if (o && o.end - o.start > best) { best = o.end - o.start; bestSlot = o; }
  }
  return { pct: Math.round((best / dur) * 100), slot: bestSlot };
}

export type { PersonReco };

/**
 * 로그인했으면 서버가 계산한 목록(GET /recommend/people)을 쓴다. 아래 계산은 서버 RecommendService와 같은 식이고,
 * 로그인 전 시연용으로 남겨 둔다. 차단은 아직 서버로 안 옮겨서(17단계) 여기서 한 번 더 거른다.
 */
export function recommendPeople(s: AppState): PersonReco[] {
  if (s.serverPeople) return s.serverPeople.filter((p) => !s.blocks.includes(p.user.id));
  const mine = wantedSlots(s), my = meUser(s);
  const out: PersonReco[] = [];
  for (const u of USERS) {
    if (u.id === 'staff' || s.blocks.includes(u.id)) continue;
    const slots = intersectAll(mine, freeSlots(u.classes));
    if (!slots.length) continue;
    const shared = sharedConcepts(my.interests, u.interests);
    const minutes = slots.reduce((a, x) => a + x.end - x.start, 0);
    const score = 0.55 * Math.min(1, minutes / 180) + 0.45 * similarity(my.interests, u.interests) + u.manner / 1000;
    const first = slots[0];
    const reason = `${'월화수목금'[first.day]}요일 ${fmt(first.start)}–${fmt(first.end)} 공강이 겹쳐요` +
      (shared.length ? ` · ${shared[0]} 취향이 비슷해요` : '');
    out.push({ user: u, slots, shared, score, reason });
  }
  return out.sort((a, b) => b.score - a.score);
}

const fmt = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;

export function recommendRooms(s: AppState): { room: Room; status: RoomStatus; fit: number }[] {
  return s.rooms
    .filter((r) => !s.blocks.includes(r.hostId))
    .map((r) => ({ room: r, status: roomStatus(r, s), fit: roomFit(r, s).pct }))
    .filter((x) => joinable(x.status) && !x.room.members.includes(ME_ID))
    .sort((a, b) => (b.fit > 0 ? 1 : 0) - (a.fit > 0 ? 1 : 0) || abs(a.room.day, a.room.start) - abs(b.room.day, b.room.start));
}

export const inClass = (s: AppState) =>
  s.classes.some((c) => c.day === s.clock.day && c.start <= s.clock.min && s.clock.min < c.end);

export const nextFree = (s: AppState) => nextFreeSlot(wantedSlots(s), s.clock);

export const myRooms = (s: AppState) => s.rooms.filter((r) => r.members.includes(ME_ID));
export const roomChat = (s: AppState, roomId: string) => s.chats.find((c) => c.roomId === roomId);
export const dmChat = (s: AppState, userId: string) => s.chats.find((c) => c.kind === 'dm' && c.userId === userId);

export const pendingChange = (s: AppState, roomId: string) =>
  s.notifs.find((n) => n.type === 'change' && n.needsAction && !n.resolved && n.link === `/rooms/${roomId}`);

/** 종료됐지만 아직 평가하지 않은 내 모임 */
export const unratedRooms = (s: AppState) =>
  myRooms(s).filter((r) => roomStatus(r, s) === 'ended' && !s.rated.includes(r.id));

export const mannerLabel = (m: number) => (m >= 38 ? '매우 좋음' : m >= 36.5 ? '좋음' : m >= 35 ? '보통' : '주의');
