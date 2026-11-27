// 추천 (서버 10단계). derive.ts의 recommendPeople/recommendRooms를 서버가 계산한다.
// 서버는 다른 사람의 시간표를 주지 않고 나와 겹치는 공강(slots)만 준다.
import { api } from './client';
import type { UserProfile } from './me';
import type { Day, PersonReco, RoomReco, Slot, User } from '../types';
import { AVATAR_COLORS } from '../data';

interface ServerSlot { day: number; start: number; end: number }
export interface ServerPersonReco { user: UserProfile; slots: ServerSlot[]; shared: string[]; score: number; reason: string }

export interface ServerRoomReco {
  room: {
    id: number; title: string; activity: string; day: number; start: number; end: number; meetDate: string;
    place: string; cap: number; memberCount: number; hostId: number; hostNick: string; topic: string | null; official: boolean;
  };
  status: 'open' | 'soon'; fit: number; slot: ServerSlot | null;
}

const toSlot = (s: ServerSlot): Slot => ({ day: s.day as Day, start: s.start, end: s.end });

/** 서버 공개 프로필 → 프론트 User. id는 서버 숫자 id를 문자열로. 시간표는 받지 않으므로 classes는 비어 있다 */
export const toUser = (p: UserProfile): User => ({
  id: String(p.id), nick: p.nick, dept: p.dept ?? undefined, color: AVATAR_COLORS[p.avatar] ?? AVATAR_COLORS[0],
  interests: p.interests, manner: p.manner, meetups: p.completed, classes: [],
});

export const toPersonReco = (r: ServerPersonReco): PersonReco => ({
  user: toUser(r.user), slots: r.slots.map(toSlot), shared: r.shared, score: r.score, reason: r.reason,
});

export const toRoomReco = (r: ServerRoomReco): RoomReco => ({
  room: {
    id: String(r.room.id),
    title: r.room.title,
    activity: r.room.activity,
    day: r.room.day as Day,
    start: r.room.start,
    end: r.room.end,
    place: r.room.place,
    cap: r.room.cap,
    members: [],
    hostId: String(r.room.hostId),
    topic: r.room.topic ?? '',
    official: r.room.official,
    meetDate: r.room.meetDate,
    memberCount: r.room.memberCount,
    hostNick: r.room.hostNick,
    status: r.status,
    joined: false,
    fitPct: r.fit,
    fitSlot: r.slot ? toSlot(r.slot) : undefined,
  },
  status: r.status,
  fit: r.fit,
});

export const recommendApi = {
  people: (opts?: { quiet?: boolean }) => api.get<ServerPersonReco[]>('/recommend/people', opts).then((l) => l.map(toPersonReco)),
  rooms: (opts?: { quiet?: boolean }) => api.get<ServerRoomReco[]>('/recommend/rooms', opts).then((l) => l.map(toRoomReco)),
};
