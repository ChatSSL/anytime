// 모임 (서버 11~12단계). 목록·상세·생성·합류·탈퇴·방장 기능을 서버로 연결한다.
import { api } from './client';
import type { UserProfile } from './me';
import type { Day, Room, Slot } from '../types';
import type { RoomStatus } from '../lib/derive';

interface ServerSlot { day: number; start: number; end: number }

export interface ServerRoom {
  id: number;
  title: string;
  activity: string;
  day: number;
  start: number;
  end: number;
  meetDate: string;
  place: string;
  cap: number;
  memberCount: number;
  hostId: number;
  hostNick: string;
  topic: string | null;
  official: boolean;
  status: RoomStatus;
  joined: boolean;
  fit: number;
  slot: ServerSlot | null;
}

export interface ServerRoomDetail extends ServerRoom {
  isHost: boolean;
  chatId: number | null;
  members: UserProfile[];
}

export interface CreateRoomBody {
  title: string;
  activity: string;
  day: number;
  start: number;
  end: number;
  place: string;
  cap: number;
  topic?: string;
}

export interface RoomListParams {
  day?: number;
  activity?: string[];
  place?: string[];
  afterMin?: number;
  minCap?: number;
  start?: number;
  end?: number;
  fitOnly?: boolean;
  mine?: boolean;
}

const toSlot = (s: ServerSlot | null): Slot | undefined =>
  s ? { day: s.day as Day, start: s.start, end: s.end } : undefined;

export const toRoom = (r: ServerRoom): Room => ({
  id: String(r.id),
  title: r.title,
  activity: r.activity,
  day: r.day as Day,
  start: r.start,
  end: r.end,
  place: r.place,
  cap: r.cap,
  members: [],
  hostId: String(r.hostId),
  topic: r.topic ?? '',
  official: r.official,
  meetDate: r.meetDate,
  memberCount: r.memberCount,
  hostNick: r.hostNick,
  status: r.status,
  joined: r.joined,
  fitPct: r.fit,
  fitSlot: toSlot(r.slot),
});

function buildQuery(params?: RoomListParams): string {
  if (!params) return '';
  const q = new URLSearchParams();
  if (params.day !== undefined) q.set('day', String(params.day));
  params.activity?.forEach((a) => q.append('activity', a));
  params.place?.forEach((p) => q.append('place', p));
  if (params.afterMin !== undefined) q.set('afterMin', String(params.afterMin));
  if (params.minCap !== undefined) q.set('minCap', String(params.minCap));
  if (params.start !== undefined) q.set('start', String(params.start));
  if (params.end !== undefined) q.set('end', String(params.end));
  if (params.fitOnly !== undefined) q.set('fitOnly', String(params.fitOnly));
  if (params.mine !== undefined) q.set('mine', String(params.mine));
  const str = q.toString();
  return str ? `?${str}` : '';
}

export const roomsApi = {
  list: (params?: RoomListParams, opts?: { quiet?: boolean }) =>
    api.get<ServerRoom[]>(`/rooms${buildQuery(params)}`, opts),
  get: (id: number, opts?: { quiet?: boolean }) =>
    api.get<ServerRoomDetail>(`/rooms/${id}`, opts),
  create: (body: CreateRoomBody) =>
    api.post<ServerRoomDetail>('/rooms', body),
  join: (id: number) =>
    api.post<ServerRoomDetail>(`/rooms/${id}/join`),
  leave: (id: number, reason?: string) =>
    api.post<ServerRoomDetail>(`/rooms/${id}/leave`, reason ? { reason } : {}),
  cancel: (id: number) =>
    api.post<ServerRoomDetail>(`/rooms/${id}/cancel`),
  transferHost: (id: number, userId: number) =>
    api.post<ServerRoomDetail>(`/rooms/${id}/host`, { userId }),
};
