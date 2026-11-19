import { ClassBlock, DAYS, Day, Slot } from '../types';

export const DAY_START = 9 * 60;
export const DAY_END = 21 * 60;

export const pad = (n: number) => String(n).padStart(2, '0');
export const hm = (m: number) => `${pad(Math.floor(m / 60))}:${pad(m % 60)}`;
export const parse = (s: string) => { const [h, m] = s.split(':').map(Number); return h * 60 + m; };
export const dur = (m: number) => {
  const h = Math.floor(m / 60), r = m % 60;
  return h && r ? `${h}시간 ${r}분` : h ? `${h}시간` : `${r}분`;
};
export const slotLabel = (s: Slot) => `${DAYS[s.day]} ${hm(s.start)}–${hm(s.end)}`;
export const slotKey = (s: Slot) => `${s.day}-${s.start}-${s.end}`;
export const abs = (day: number, min: number) => day * 1440 + min;

/** 공강: 같은 요일 수업 사이의 빈 구간 (minGap분 이상). 규칙 기반 계산이라 AI가 필요 없다. */
export function freeSlots(classes: ClassBlock[], minGap = 60): Slot[] {
  const out: Slot[] = [];
  for (let d = 0; d < 5; d++) {
    const list = classes.filter((c) => c.day === d).sort((a, b) => a.start - b.start);
    for (let i = 0; i < list.length - 1; i++) {
      const gap = list[i + 1].start - list[i].end;
      if (gap >= minGap) out.push({ day: d as Day, start: list[i].end, end: list[i + 1].start });
    }
  }
  return out;
}

export function overlap(a: Slot, b: Slot): Slot | null {
  if (a.day !== b.day) return null;
  const start = Math.max(a.start, b.start), end = Math.min(a.end, b.end);
  return end > start ? { day: a.day, start, end } : null;
}

/** 두 사람의 공강이 겹치는 구간만 돌려준다. 다른 사용자에게는 이 결과만 보인다. */
export function intersectAll(a: Slot[], b: Slot[], min = 60): Slot[] {
  const out: Slot[] = [];
  for (const x of a) for (const y of b) {
    const o = overlap(x, y);
    if (o && o.end - o.start >= min) out.push(o);
  }
  return out;
}

export function classConflict(classes: ClassBlock[], day: number, start: number, end: number): ClassBlock | undefined {
  return classes.find((c) => c.day === day && c.start < end && start < c.end);
}

export function parseClasses(lines: string[]): ClassBlock[] {
  return lines.map((l, i) => {
    const m = l.match(/^(.) (\d\d:\d\d)-(\d\d:\d\d)\s*(.*)$/)!;
    return { id: `c${i}-${l}`, name: m[4] || '수업', day: DAYS.indexOf(m[1] as never) as Day, start: parse(m[2]), end: parse(m[3]) };
  });
}

export function nextFreeSlot(slots: Slot[], clock: { day: Day; min: number }): Slot | undefined {
  const n = abs(clock.day, clock.min);
  return [...slots].sort((a, b) => abs(a.day, a.start) - abs(b.day, b.start)).find((s) => abs(s.day, s.end) > n);
}
