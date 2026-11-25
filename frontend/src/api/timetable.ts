// 시간표 (서버 9단계)
// 서버는 "만나고 싶은 공강" 목록만 저장한다. 프론트는 공강마다 wanted[key] = true/false로 들고 있고
// 기본값이 true(만나고 싶어요)라서, 보낼 때는 켜진 공강(wantedSlots)만 보내고 받을 때 다시 key 표로 바꾼다.
import { api } from './client';
import type { ClassBlock, Day, Slot } from '../types';
import { freeSlots, slotKey } from '../lib/time';

interface ServerClass { id: number; name: string | null; day: number; start: number; end: number }
interface ServerSlot { day: number; start: number; end: number }
export interface Timetable { classes: ServerClass[]; wanted: ServerSlot[] }

const body = (c: Omit<ClassBlock, 'id'>) => ({ name: c.name, day: c.day, start: c.start, end: c.end });

/** 서버 응답 → 프론트 classes, wanted. 서버에 없는 공강은 '만나고 싶지 않아요'(false) */
export function fromServer(t: Timetable): { classes: ClassBlock[]; wanted: Record<string, boolean> } {
  const classes: ClassBlock[] = t.classes.map((c) => ({ id: String(c.id), name: c.name ?? '', day: c.day as Day, start: c.start, end: c.end }));
  const on = new Set(t.wanted.map((w) => slotKey({ ...w, day: w.day as Day })));
  const wanted: Record<string, boolean> = {};
  for (const f of freeSlots(classes)) wanted[slotKey(f)] = on.has(slotKey(f));
  return { classes, wanted };
}

/** 수업 목록이 같은지 (id는 프론트·서버가 달라서 비교하지 않는다) */
export function sameClasses(a: Omit<ClassBlock, 'id'>[], b: Omit<ClassBlock, 'id'>[]) {
  const key = (l: Omit<ClassBlock, 'id'>[]) => l.map((c) => `${c.day}-${c.start}-${c.end}-${c.name ?? ''}`).sort().join('|');
  return key(a) === key(b);
}

export function sameSlots(a: Slot[], b: ServerSlot[]) {
  const key = (l: ServerSlot[]) => l.map((s) => `${s.day}-${s.start}-${s.end}`).sort().join('|');
  return key(a) === key(b);
}

export const timetableApi = {
  get: (opts?: { quiet?: boolean }) => api.get<Timetable>('/me/timetable', opts),

  /** 전체 교체. 겹치면 ApiError(400, 'TIMETABLE_CLASS_OVERLAP'). 새 수업과 겹친 공강은 서버가 지운다 */
  putClasses: (classes: Omit<ClassBlock, 'id'>[]) => api.put<Timetable>('/me/classes', { classes: classes.map(body) }),

  /** 전체 교체. 수업과 겹치면 ApiError(400, 'TIMETABLE_WANTED_NOT_FREE') */
  putWanted: (wanted: Slot[]) => api.put<Timetable>('/me/wanted', { wanted: wanted.map(({ day, start, end }) => ({ day, start, end })) }),
};
