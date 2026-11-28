import { ReactNode, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClassBlock, DAYS, Room, Slot, User } from '../types';
import { useApp } from '../store';
import { hm, dur, slotLabel } from '../lib/time';
import { PersonReco, ME_ID, RoomStatus, STATUS_LABEL, mannerLabel, roomStatus, userById, roomFit } from '../lib/derive';

/* ---------- icons ---------- */
const P: Record<string, string> = {
  back: 'M15 5l-7 7 7 7', close: 'M6 6l12 12M18 6L6 18', bell: 'M6 16V11a6 6 0 1112 0v5l1.5 2h-15L6 16zM10 20h4',
  home: 'M4 11l8-7 8 7v8a1 1 0 01-1 1h-4v-6H9v6H5a1 1 0 01-1-1v-8z', grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  chat: 'M4 5h16v11H9l-5 4V5z', user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 20c0-4 4-6 8-6s8 2 8 6',
  plus: 'M12 5v14M5 12h14', check: 'M5 12.5l4.5 4.5L19 7.5', flag: 'M6 21V4M6 4h11l-2 4 2 4H6',
  spark: 'M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2 2-6z', clock: 'M12 7v5l3 2M12 21a9 9 0 100-18 9 9 0 000 18z',
  pin: 'M12 21s7-6 7-11a7 7 0 10-14 0c0 5 7 11 7 11zM12 12a2 2 0 100-4 2 2 0 000 4z', users: 'M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7zM2.5 20c0-3.5 3-5 6.5-5s6.5 1.5 6.5 5M17 5a3.5 3.5 0 010 6.5M18 15c2.5.5 3.5 2 3.5 5',
  chev: 'M9 5l7 7-7 7', more: 'M5 12h.01M12 12h.01M19 12h.01', shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z',
  edit: 'M4 20h4L19 9l-4-4L4 16v4z', send: 'M4 12l16-8-6 16-2-7-8-1z', lock: 'M6 11h12v9H6zM8 11V8a4 4 0 118 0v3', phone: 'M5 4h4l2 5-2.5 1.5a11 11 0 005 5L15 13l5 2v4a2 2 0 01-2 2A15 15 0 013 6a2 2 0 012-2z',
  star: 'M12 3l2.8 6 6.2.7-4.6 4.2 1.3 6.1L12 17l-5.7 3 1.3-6.1L3 9.7 9.2 9z', share: 'M12 15V4M8 8l4-4 4 4M5 13v6h14v-6',
  info: 'M12 8h.01M11 12h1v5h1M12 21a9 9 0 100-18 9 9 0 000 18z', trash: 'M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13', filter: 'M4 6h16M7 12h10M10 18h4',
};
export const Icon = ({ n, s = 24, fill }: { n: string; s?: number; fill?: boolean }) => (
  <svg width={s} height={s} viewBox="0 0 24 24" fill={fill ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={P[n] ?? ''} />
  </svg>
);

/* ---------- atoms ---------- */
export const Avatar = ({ user, size }: { user?: Pick<User, 'nick' | 'color'>; size?: 'sm' | 'lg' }) => (
  <span className={`avatar ${size ?? ''}`} style={{ background: user?.color ?? '#9A9AB5' }} aria-label={user?.nick}>{user?.nick?.[0] ?? '?'}</span>
);
export const Switch = ({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) => (
  <button type="button" role="switch" aria-checked={on} aria-label={label} className="switch" onClick={() => onChange(!on)} />
);
export const Chip = ({ on, onClick, children, lime }: { on?: boolean; onClick?: () => void; children: ReactNode; lime?: boolean }) => (
  <button type="button" className={`chip ${on ? 'on' : ''} ${lime ? 'lime' : ''}`} aria-pressed={on} onClick={onClick}>{children}</button>
);
export const StatusPill = ({ st }: { st: RoomStatus }) => <span className={`pill ${st}`}>{STATUS_LABEL[st]}</span>;

export const FreeBadge = ({ slot, text }: { slot: Slot; text?: string }) => {
  const m = slot.end - slot.start, lv = m >= 120 ? 'l3' : m >= 90 ? 'l2' : 'l1';
  return <span className={`free-badge ${lv}`}>{`${DAYS[slot.day]} ${hm(slot.start)}–${hm(slot.end)} ${text ?? '공강 겹침'}`}</span>;
};

export function MannerGauge({ value }: { value: number }) {
  const pct = Math.max(0, Math.min(100, ((value - 30) / 15) * 100));
  return (
    <div className="col" style={{ gap: 6 }} aria-label={`매너 점수 ${value}`}>
      <div className="row between"><span className="num" style={{ fontSize: 20, fontWeight: 700 }}>{value.toFixed(1)}°</span><span className="cap">{mannerLabel(value)}</span></div>
      <div className="gauge"><i style={{ left: `${pct}%` }} /></div>
    </div>
  );
}

export function Empty({ title, text, action }: { title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="col center" style={{ alignItems: 'center', padding: '32px 16px', gap: 10 }}>
      <div className="illu" style={{ width: 160, height: 110, borderRadius: 20 }} aria-hidden="true">
        <i className="b" style={{ width: 50, height: 22, left: 20, top: 20 }} /><i className="f" style={{ width: 60, height: 30, right: 20, top: 40 }} /><i className="b" style={{ width: 40, height: 18, left: 40, bottom: 18 }} />
      </div>
      <h3>{title}</h3>{text && <p className="cap">{text}</p>}{action}
    </div>
  );
}

export const Skeleton = ({ h = 20, w = '100%' }: { h?: number; w?: number | string }) => <div className="skel" style={{ height: h, width: w }} />;

/* ---------- overlays ---------- */
export function Sheet({ open, onClose, children, title, full }: { open: boolean; onClose: () => void; children: ReactNode; title?: string; full?: boolean }) {
  useEffect(() => {
    if (!open) return;
    const k = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', k); return () => window.removeEventListener('keydown', k);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="scrim" onClick={onClose}>
      <div className={`sheet ${full ? 'full' : ''}`} role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="grab" />
        {title && <div className="row between"><h2>{title}</h2><button className="iconbtn" onClick={onClose} aria-label="닫기"><Icon n="close" /></button></div>}
        {children}
      </div>
    </div>
  );
}

export function Modal({ open, onClose, title, children, actions }: { open: boolean; onClose: () => void; title: string; children?: ReactNode; actions: ReactNode }) {
  if (!open) return null;
  return (
    <div className="scrim modal-wrap" onClick={onClose}>
      <div className="modal" role="alertdialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>{children}<div className="col" style={{ gap: 8, marginTop: 8 }}>{actions}</div>
      </div>
    </div>
  );
}

export function Toasts() {
  const { toasts } = useApp();
  return <div className="toasts" aria-live="polite">{toasts.map((t) => <div key={t.id} className={`toast ${t.kind === 'err' ? 'err' : ''}`}>{t.text}</div>)}</div>;
}

export function TopBar({ title, back = true, right, onBack }: { title?: string; back?: boolean; right?: ReactNode; onBack?: () => void }) {
  const nav = useNavigate();
  return (
    <div className="topbar">
      {back && <button className="iconbtn" aria-label="뒤로가기" onClick={() => (onBack ? onBack() : nav(-1))} style={{ marginLeft: -10 }}><Icon n="back" /></button>}
      <h2>{title}</h2>{right}
    </div>
  );
}

/* ---------- domain cards ---------- */
export function RoomCard({ room, full }: { room: Room; full?: boolean }) {
  const { s } = useApp(); const nav = useNavigate();
  const st = roomStatus(room, s), fit = roomFit(room, s);
  const hostNick = room.hostNick ?? userById(s, room.hostId)?.nick ?? '';
  const count = room.memberCount ?? room.members.length;
  const joined = room.joined ?? room.members.includes(ME_ID);
  const dim = st === 'closed' || st === 'full' || st === 'ended' || st === 'cancelled';
  return (
    <button className={`card roomcard ${full ? 'full' : ''} ${dim ? 'dim' : ''}`} onClick={() => nav(`/rooms/${room.id}`)} aria-label={`${room.title} 상세`}>
      <div className="row" style={{ alignItems: 'flex-start' }}>
        <span className="act" aria-hidden="true">{room.activity[0]}</span>
        <div className="grow"><h3>{room.title}</h3><p className="cap num">{DAYS[room.day]} {hm(room.start)}–{hm(room.end)}</p></div>
        {joined ? <span className="pill mine">참여 중</span> : <StatusPill st={st} />}
      </div>
      <p className="cap row" style={{ gap: 4 }}><Icon n="pin" s={14} />{room.place}</p>
      <div className="row" style={{ gap: 10 }}>
        <div className="bar grow" aria-hidden="true"><i style={{ width: `${(count / room.cap) * 100}%` }} /></div>
        <span className="cap num">{count}/{room.cap}명</span>
      </div>
      <div className="row between">
        <span className="cap">{room.official ? '운영팀이 연 모임' : `방장 ${hostNick}`}</span>
        {fit.slot && fit.pct > 0 && <FreeBadge slot={fit.slot} text={fit.pct >= 100 ? '100% 겹침' : `${fit.pct}% 겹침`} />}
      </div>
    </button>
  );
}

export function PersonCard({ reco }: { reco: PersonReco }) {
  const nav = useNavigate(); const { s } = useApp();
  const u = reco.user, sent = s.sentRequests.includes(u.id);
  return (
    <div className="card col" style={{ gap: 10 }}>
      <button className="row" style={{ background: 'none', border: 0, padding: 0, textAlign: 'left', alignItems: 'flex-start', gap: 12 }} onClick={() => nav(`/person/${u.id}`)} aria-label={`${u.nick} 프로필`}>
        <Avatar user={u} />
        <div className="grow"><h3>{u.nick}</h3><p className="cap">{u.dept || '학과 비공개'} · 매너 <span className="num">{u.manner.toFixed(1)}°</span></p></div>
      </button>
      <div className="chips">{u.interests.slice(0, 3).map((i) => <span key={i} className="chip sm">{i}</span>)}</div>
      <p className="cap row" style={{ gap: 6, alignItems: 'flex-start' }}><Icon n="spark" s={14} />{reco.reason}</p>
      <div className="row between wrap"><FreeBadge slot={reco.slots[0]} /><button className="btn s" disabled={sent} onClick={() => nav(`/person/${u.id}?request=1`)}>{sent ? '요청함' : '1:1 요청'}</button></div>
    </div>
  );
}

/* ---------- timetable ---------- */
const ROW = 40, H0 = 9;
export function Timetable({ classes, free = [], events = [], onCell, onClass }: {
  classes: ClassBlock[]; free?: Slot[]; events?: (Slot & { name: string })[];
  onCell?: (day: number, hour: number) => void; onClass?: (c: ClassBlock) => void;
}) {
  const top = (m: number) => ((m - H0 * 60) / 60) * ROW;
  const hours = Array.from({ length: 12 }, (_, i) => H0 + i);
  return (
    <div className="tt" role="grid" aria-label="주간 시간표">
      <div />{DAYS.map((d) => <div key={d} className="hd">{d}</div>)}
      <div>{hours.map((h) => <div key={h} className="tm" style={{ height: ROW }}>{h}</div>)}</div>
      {DAYS.map((_, d) => (
        <div key={d} className="dcol" style={{ position: 'relative' }}>
          {hours.map((h) => (
            <div key={h} className="cell" style={{ height: ROW }}>
              {onCell && <button aria-label={`${DAYS[d]} ${h}시 수업 추가`} onClick={() => onCell(d, h)} />}
            </div>
          ))}
          {free.filter((f) => f.day === d).map((f) => (
            <div key={`f${f.start}`} className="blk fr" style={{ top: top(f.start) + 1, height: top(f.end) - top(f.start) - 2, pointerEvents: 'none' }}>공강 {dur(f.end - f.start)}</div>
          ))}
          {classes.filter((c) => c.day === d).map((c) => (
            <button key={c.id} className="blk cls" onClick={() => onClass?.(c)} style={{ top: top(c.start) + 1, height: top(c.end) - top(c.start) - 2 }}>{c.name}</button>
          ))}
          {events.filter((e) => e.day === d).map((e, i) => (
            <div key={`e${i}`} className="blk ev" style={{ top: top(e.start) + 1, height: Math.max(18, top(e.end) - top(e.start) - 2), left: '22%' }}>{e.name}</div>
          ))}
        </div>
      ))}
    </div>
  );
}

export { slotLabel };
