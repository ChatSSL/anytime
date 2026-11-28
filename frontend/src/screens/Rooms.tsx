import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useApp } from '../store';
import { ACTIVITIES, AVATAR_COLORS, PLACES } from '../data';
import { DAYS, Day } from '../types';
import { abs, dur, freeSlots, hm, classConflict, slotKey, slotLabel } from '../lib/time';
import { ME_ID, joinable, myRooms, pendingChange, recommendPeople, recommendRooms, roomChat, roomFit, roomStatus, userById } from '../lib/derive';
import { Avatar, Chip, Empty, Icon, Modal, RoomCard, Sheet, Skeleton, StatusPill, TopBar } from '../components/ui';
import { SafetyMenu, SosButton } from '../components/safety';
import { ChatPanel } from './Chat';
import { authApi } from '../api/auth';
import { roomsApi, ServerRoomDetail, toRoom } from '../api/rooms';
import { toUser } from '../api/recommend';

const isServerId = (id?: string) => !!id && /^\d+$/.test(id);

/* ------------------------------------------------------------------ 목록 */
export function Rooms() {
  const { s, a } = useApp(); const nav = useNavigate();
  const [day, setDay] = useState<number>(s.clock.day);
  const [fitOnly, setFitOnly] = useState(true);
  const [acts, setActs] = useState<string[]>([]); const [places, setPlaces] = useState<string[]>([]);
  const [afterMin, setAfterMin] = useState(540); const [minCap, setMinCap] = useState(2);
  const [open, setOpen] = useState(false);

  useEffect(() => { a.loadRooms(); }, [a]);

  const source = s.serverRooms ?? s.rooms;
  const all = source.filter((r) => !s.blocks.includes(r.hostId));
  const filtered = all.filter((r) =>
    r.day === day && (!fitOnly || roomFit(r, s).pct > 0 || (r.joined ?? r.members.includes(ME_ID))) &&
    (!acts.length || acts.includes(r.activity)) && (!places.length || places.includes(r.place)) &&
    r.start >= afterMin && r.cap >= minCap,
  ).sort((a, b) => a.start - b.start);
  const similar = recommendRooms(s).filter((x) => x.room.day !== day).slice(0, 2);
  const active = acts.length + places.length + (afterMin > 540 ? 1 : 0) + (minCap > 2 ? 1 : 0);
  const reset = () => { setActs([]); setPlaces([]); setAfterMin(540); setMinCap(2); };
  const tog = (arr: string[], set: (v: string[]) => void, x: string) => set(arr.includes(x) ? arr.filter((y) => y !== x) : [...arr, x]);

  return (
    <div className="page">
      <div className="topbar"><h2 style={{ fontSize: 22 }}>열린 모임</h2><button className="btn s" onClick={() => nav('/rooms/new')}><Icon n="plus" s={16} />만들기</button></div>
      <div className="chips scroll" role="tablist" aria-label="요일">
        {DAYS.map((d, i) => <Chip key={d} on={day === i} onClick={() => setDay(i)}>{d}{i === s.clock.day ? ' · 오늘' : ''}</Chip>)}
      </div>
      <div className="chips scroll" style={{ marginTop: 8 }}>
        <Chip lime on={fitOnly} onClick={() => setFitOnly(!fitOnly)}>내 공강에 맞는 모임</Chip>
        <Chip on={active > 0} onClick={() => setOpen(true)}><Icon n="filter" s={14} />필터{active ? ` ${active}` : ''}</Chip>
        {acts.map((a) => <Chip key={a} on onClick={() => tog(acts, setActs, a)}>{a} ✕</Chip>)}
      </div>
      <div className="sectionhead"><span className="cap" aria-live="polite">{filtered.length}개의 모임</span></div>
      {filtered.length ? <div className="grid2">{filtered.map((r) => <RoomCard key={r.id} room={r} full />)}</div> : (
        <div className="card"><Empty title="이 날은 열린 모임이 없어요" text={fitOnly ? '"내 공강에 맞는 모임"을 끄면 더 많이 보여요.' : '직접 모임을 열어 보세요.'} action={<button className="btn m" onClick={() => nav('/rooms/new')}>모임 만들기</button>} /></div>
      )}
      {similar.length > 0 && (<><div className="sectionhead"><h2>비슷한 모임</h2></div><div className="grid2">{similar.map((x) => <RoomCard key={x.room.id} room={x.room} full />)}</div></>)}

      <Sheet open={open} onClose={() => setOpen(false)} title="필터">
        <div className="field"><span className="label">활동</span><div className="chips">{ACTIVITIES.map((a) => <Chip key={a} on={acts.includes(a)} onClick={() => tog(acts, setActs, a)}>{a}</Chip>)}</div></div>
        <div className="field"><label htmlFor="after">시작 시간 {hm(afterMin)} 이후</label><input id="after" type="range" min={540} max={1140} step={30} value={afterMin} onChange={(e) => setAfterMin(+e.target.value)} style={{ accentColor: 'var(--primary)' }} /></div>
        <div className="field"><label htmlFor="cap">정원 {minCap}명 이상</label><input id="cap" type="range" min={2} max={8} value={minCap} onChange={(e) => setMinCap(+e.target.value)} style={{ accentColor: 'var(--primary)' }} /></div>
        <div className="field"><span className="label">장소</span><div className="chips">{PLACES.map((p) => <Chip key={p} on={places.includes(p)} onClick={() => tog(places, setPlaces, p)}>{p}</Chip>)}</div></div>
        <div className="row"><button className="btn secondary m" onClick={reset}>초기화</button><button className="btn m grow" onClick={() => setOpen(false)}>결과 보기 {filtered.length}개</button></div>
      </Sheet>
    </div>
  );
}

/* ------------------------------------------------------------------ 상세 */
export function RoomDetail() {
  const { id } = useParams(); const nav = useNavigate();
  const { s, a, toast } = useApp();
  const serverMode = isServerId(id);
  const [remote, setRemote] = useState<ServerRoomDetail | null | undefined>(undefined);
  useEffect(() => {
    if (!serverMode) return;
    setRemote(undefined);
    roomsApi.get(Number(id), { quiet: true }).then(setRemote).catch(() => setRemote(null));
  }, [id, serverMode]);

  const [cancel, setCancel] = useState(false); const [reason, setReason] = useState(''); const [menu, setMenu] = useState(false); const [joined, setJoined] = useState(false);
  const [busy, setBusy] = useState(false);
  const [cancelRoomOpen, setCancelRoomOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const [nextHostId, setNextHostId] = useState('');

  if (serverMode && remote === undefined) {
    return <div className="page"><TopBar title="모임 상세" /><div className="card col"><Skeleton h={120} /><Skeleton h={24} w="60%" /><Skeleton h={16} /></div></div>;
  }

  const room = serverMode ? (remote ? toRoom(remote) : undefined) : s.rooms.find((r) => r.id === id);
  if (!room) return <div className="page"><TopBar title="모임" /><Empty title="모임을 찾을 수 없어요" action={<button className="btn m" onClick={() => nav('/rooms')}>목록으로</button>} /></div>;

  const st = remote ? remote.status : roomStatus(room, s);
  const memberUsers = remote ? remote.members.map(toUser) : room.members.map((m) => userById(s, m)!).filter(Boolean);
  const host = remote
    ? (memberUsers.find((u) => u.id === String(remote.hostId)) ?? {
      id: String(remote.hostId), nick: remote.hostNick, color: AVATAR_COLORS[0], interests: [], manner: 36.5, meetups: 0, classes: [],
    })
    : userById(s, room.hostId);
  const member = remote ? remote.joined : room.members.includes(ME_ID);
  const isHost = remote ? remote.isHost : room.hostId === ME_ID;
  const memberCount = remote ? remote.memberCount : room.members.length;
  const fit = roomFit(room, s), conflict = classConflict(s.classes, room.day, room.start, room.end);
  const chat = roomChat(s, room.id), change = pendingChange(s, room.id);
  const startAbs = abs(room.day, room.start), nowAbs = abs(s.clock.day, s.clock.min);
  const similar = recommendRooms(s).filter((x) => x.room.id !== room.id).slice(0, 2);
  const others = remote ? memberUsers.filter((u) => !remote.isHost || u.id !== String(remote.hostId)) : memberUsers.filter((u) => u.id !== ME_ID);
  const transferCandidates = memberUsers.filter((u) => u.id !== String(room.hostId));
  const unavailable = !member && !joinable(st);
  const serverStartMs = remote ? new Date(`${remote.meetDate}T00:00:00+09:00`).getTime() + remote.start * 60_000 : 0;
  const serverDiffMs = remote ? serverStartMs - Date.now() : 0;
  const close1h = remote ? (serverDiffMs > 0 && serverDiffMs <= 60 * 60_000) : (startAbs - nowAbs <= 60);
  const remainMin = remote ? Math.max(0, Math.ceil(serverDiffMs / 60_000)) : (startAbs - nowAbs);

  const doJoin = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const updated = await a.join(room.id);
      if (updated) {
        setRemote(updated);
        if (updated.joined) setJoined(true);
      } else if (serverMode) {
        const latest = await roomsApi.get(Number(id), { quiet: true }).catch(() => null);
        if (latest) {
          setRemote(latest);
          if (latest.joined) setJoined(true);
        }
      } else {
        setJoined(true);
      }
    } catch {
      // 에러 토스트는 client.ts가 표시한다
    } finally {
      setBusy(false);
    }
  };

  const doLeave = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const r = reason || '단순 취소';
      const updated = await a.leave(room.id, r, close1h && r === '그냥 못 가요');
      if (updated) setRemote(updated);
      else if (serverMode) setRemote(await roomsApi.get(Number(id), { quiet: true }).catch(() => remote));
      setJoined(false);
      setCancel(false); setReason('');
    } catch {
      // 에러 토스트는 client.ts가 표시한다
    } finally {
      setBusy(false);
    }
  };

  const doCancelRoom = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const updated = await a.cancelRoom(room.id);
      if (updated) setRemote(updated);
      else if (serverMode) setRemote(await roomsApi.get(Number(id), { quiet: true }).catch(() => remote));
      setCancelRoomOpen(false);
    } catch {
      // 에러 토스트는 client.ts가 표시한다
    } finally {
      setBusy(false);
    }
  };

  const doTransferHost = async () => {
    if (busy || !nextHostId) return;
    setBusy(true);
    try {
      const updated = await a.transferHost(room.id, nextHostId);
      if (updated) setRemote(updated);
      else if (serverMode) setRemote(await roomsApi.get(Number(id), { quiet: true }).catch(() => remote));
      setTransferOpen(false); setNextHostId('');
    } catch {
      // 에러 토스트는 client.ts가 표시한다
    } finally {
      setBusy(false);
    }
  };
  const REASONS = ['휴강 또는 수업 연장', '갑작스러운 일정', '몸이 안 좋아요', '그냥 못 가요'];

  const info = (
    <div className="col" style={{ gap: 14 }}>
      <div className="hero" style={{ background: 'var(--primary)', color: 'var(--on-primary)', minHeight: 120, justifyContent: 'flex-end' }}>
        <div className="row between"><span className="act" style={{ background: 'rgba(255,255,255,.2)', color: 'inherit', width: 48, height: 48, fontSize: 22 }} aria-hidden="true">{room.activity[0]}</span>{member ? <span className="pill mine">참여 중</span> : <StatusPill st={st} />}</div>
        <h1 style={{ fontSize: 24 }}>{room.title}</h1>
      </div>

      {st === 'cancelled' && <div className="notice danger"><Icon n="info" s={18} /><span><b>취소된 모임이에요</b><br />비슷한 모임을 아래에서 골라보세요.</span></div>}
      {st === 'closed' && !member && <div className="notice warn"><Icon n="clock" s={18} />모집이 마감됐어요. 시작 30분 전에 자동으로 닫혀요.</div>}
      {member && st === 'closed' && <div className="notice"><Icon n="bell" s={18} />모집이 마감됐어요. 곧 시작해요!</div>}
      {member && (st === 'soon' || st === 'closed') && remainMin > 0 && <div className="notice"><Icon n="bell" s={18} />리마인드: {dur(remainMin)} 뒤 시작해요.</div>}
      {change && member && (
        <div className="notice warn col" style={{ gap: 8 }}><span><b>일정이 바뀌었어요</b> · {change.body}</span>
          <div className="row"><button className="btn s" onClick={() => a.resolveChange(room.id, true)}>그래도 참여할게요</button><button className="btn secondary s" onClick={() => a.resolveChange(room.id, false)}>이번엔 빠질게요</button></div></div>
      )}
      {joined && member && <div className="notice ok"><Icon n="check" s={18} />내 시간표에 일정이 등록됐어요.</div>}
      {!member && joinable(st) && fit.pct > 0 && fit.slot && <div className="notice lime">내 공강과 {fit.pct}% 겹쳐요 · {slotLabel(fit.slot)}</div>}
      {!member && joinable(st) && conflict && <div className="notice danger"><Icon n="info" s={18} />{conflict.name} 수업과 시간이 겹쳐요.</div>}

      <div className="card col list" style={{ gap: 0, padding: '4px 16px' }}>
        {[['clock', `${DAYS[room.day]} ${hm(room.start)}–${hm(room.end)}`], ['pin', room.place], ['users', `${memberCount}/${room.cap}명 · 2명부터 열려요`], ...(room.topic ? [['spark', room.topic]] : [])].map(([ic, t]) => (
          <div key={t} className="listrow" style={{ padding: '12px 0' }}><Icon n={ic} s={20} /><span className="num grow">{t}</span></div>
        ))}
        <div className="listrow" style={{ padding: '12px 0' }}>{host && <Avatar user={host} size="sm" />}<span className="grow">{room.official ? '운영팀이 먼저 열어 둔 모임' : `방장 ${host?.nick ?? ''}`}</span></div>
      </div>

      <div className="col" style={{ gap: 8 }}>
        <span className="label">참여자</span>
        <div className="avatars" aria-label={`참여자 ${memberCount}명`}>
          {memberUsers.map((u) => <Avatar key={u.id} user={u} />)}
          {Array.from({ length: Math.max(0, Math.min(room.cap - memberCount, 4)) }).map((_, i) => <span key={i} className="avatar empty" aria-hidden="true">+</span>)}
        </div>
      </div>

      <details className="card flat">
        <summary style={{ fontWeight: 700, cursor: 'pointer' }}>모임 규칙</summary>
        <ul className="cap" style={{ margin: '8px 0 0', paddingLeft: 18 }}>
          <li>시작 30분 전에 자동으로 모집이 마감돼요. 1시간 전에 리마인드가 가요.</li>
          <li>시작 1시간 전까지는 자유롭게 취소할 수 있어요.</li>
          <li>그 뒤 취소는 사유를 골라요. 휴강·수업 연장은 기록만 남아요.</li>
          <li>시작 시점에 2명 미만이면 자동 취소되고 비슷한 모임을 추천해요.</li>
          <li>방장이 일정을 바꾸면 참여 여부를 다시 확인해요.</li>
        </ul>
      </details>

      {(st === 'cancelled' || unavailable) && similar.length > 0 && (
        <div className="col"><h3>비슷한 모임</h3>{similar.map((x) => <RoomCard key={x.room.id} room={x.room} full />)}</div>
      )}

      <div className="cta">
        {!member && joinable(st) && <button className="btn" disabled={busy} onClick={doJoin}>{busy ? '합류 중...' : '합류하기'}</button>}
        {unavailable && <><button className="btn" disabled>{st === 'cancelled' ? '취소된 모임이에요' : st === 'ended' ? '끝난 모임이에요' : '마감됐어요'}</button><button className="btn secondary" onClick={() => nav('/rooms')}>비슷한 모임 보기</button></>}
        {member && st !== 'ended' && st !== 'cancelled' && (<>
          <button className="btn" onClick={() => nav(remote?.chatId ? `/chat/${remote.chatId}` : `/chat/chat-${room.id}`)}>채팅방 가기</button>
          <button className="btn secondary" disabled={busy} onClick={() => setCancel(true)}>참여 취소</button></>)}
        {member && st === 'ended' && !s.rated.includes(room.id) && <button className="btn" onClick={() => nav(`/review/${room.id}`)}>모임 평가하기</button>}
      </div>
      {isHost && st !== 'ended' && st !== 'cancelled' && (
        <div className="row wrap">
          {transferCandidates.length > 0 && (
            <button className="btn secondary s" disabled={busy} onClick={() => { setNextHostId(transferCandidates[0].id); setTransferOpen(true); }}>방장 위임</button>
          )}
          <button className="btn ghost s" style={{ color: 'var(--danger)' }} disabled={busy} onClick={() => setCancelRoomOpen(true)}>모임 취소</button>
        </div>
      )}
      {!remote && member && !isHost && st !== 'ended' && st !== 'cancelled' && (
        <button className="btn ghost s" style={{ alignSelf: 'flex-start' }} onClick={() => a.simulateHostChange(room.id)}>시연: 방장이 시간을 30분 늦춰요</button>
      )}
    </div>
  );

  const embeddedChatId = serverMode ? (remote?.chatId ? String(remote.chatId) : undefined) : chat?.id;

  return (
    <div className="page">
      <TopBar title="모임 상세" right={<>
        <button className="iconbtn" aria-label="링크 복사" onClick={() => { navigator.clipboard?.writeText(location.href).then(() => toast('링크를 복사했어요', 'info')).catch(() => toast('링크를 복사하지 못했어요. 주소창의 주소를 직접 복사해 주세요', 'err')); }}><Icon n="share" /></button>
        {host && host.id !== ME_ID && host.id !== 'staff' && !isHost && <button className="iconbtn" aria-label="신고 · 차단" onClick={() => setMenu(true)}><Icon n="flag" /></button>}
      </>} />
      <div className="splitroom">
        {info}
        {member && embeddedChatId && st !== 'cancelled' && <ChatPanel chatId={embeddedChatId} embedded />}
      </div>

      <Modal open={cancel && !close1h} onClose={() => setCancel(false)} title="참여를 취소할까요?"
        actions={<><button className="btn danger block" disabled={busy} onClick={doLeave}>{busy ? '취소 중...' : '취소하기'}</button><button className="btn ghost block" onClick={() => setCancel(false)}>유지하기</button></>}>
        <p className="cap">시작 1시간 전까지는 자유롭게 취소할 수 있어요.</p>
      </Modal>
      <Sheet open={cancel && close1h} onClose={() => setCancel(false)} title="취소 사유를 골라주세요">
        <div className="col" style={{ gap: 4 }} role="radiogroup" aria-label="취소 사유">
          {REASONS.map((r) => <label key={r} className="check" style={{ padding: '8px 0' }}><input type="radio" name="reason" checked={reason === r} onChange={() => setReason(r)} />{r}</label>)}
        </div>
        <div className="notice">휴강·수업 연장 같은 불가피한 사유는 기록만 남고 점수에는 영향이 없어요. 연락 없이 불참하면 매너 점수가 내려가요.</div>
        <button className="btn danger block" disabled={!reason || busy} onClick={doLeave}>{busy ? '취소 중...' : '참여 취소'}</button>
      </Sheet>
      <Modal open={cancelRoomOpen} onClose={() => setCancelRoomOpen(false)} title="모임을 취소할까요?"
        actions={<><button className="btn danger block" disabled={busy} onClick={doCancelRoom}>{busy ? '취소 중...' : '모임 취소하기'}</button><button className="btn ghost block" onClick={() => setCancelRoomOpen(false)}>돌아가기</button></>}>
        <p className="cap">취소하면 참여 중인 다른 멤버들에게 알림이 가고 다시 열 수 없어요.</p>
      </Modal>
      <Sheet open={transferOpen} onClose={() => setTransferOpen(false)} title="방장 위임">
        <div className="col" style={{ gap: 4 }} role="radiogroup" aria-label="새 방장 선택">
          {transferCandidates.map((u) => (
            <label key={u.id} className="check" style={{ padding: '8px 0' }}>
              <input type="radio" name="nextHost" checked={nextHostId === u.id} onChange={() => setNextHostId(u.id)} />
              <Avatar user={u} size="sm" />
              <span>{u.nick}</span>
            </label>
          ))}
        </div>
        <button className="btn block" disabled={!nextHostId || busy} onClick={doTransferHost}>{busy ? '위임 중...' : '방장 넘기기'}</button>
      </Sheet>
      <SafetyMenu open={menu} onClose={() => setMenu(false)} target={host} roomId={room.id} />
      {member && <SosButton candidates={others.filter((u) => u.id !== 'staff')} roomId={room.id} />}
    </div>
  );
}

/* ------------------------------------------------------------------ 생성 */
export function RoomCreate() {
  const { s, a } = useApp(); const nav = useNavigate();
  useEffect(() => {
    if (authApi.isLoggedIn()) a.loadRooms();
  }, [a]);
  const free = freeSlots(s.classes);
  const [activity, setActivity] = useState('보드게임'); const [title, setTitle] = useState('');
  const [slotK, setSlotK] = useState<string>(free[0] ? slotKey(free[0]) : 'manual');
  const [mDay, setMDay] = useState<Day>(s.clock.day); const [mStart, setMStart] = useState(720); const [mEnd, setMEnd] = useState(810);
  const [place, setPlace] = useState(PLACES[0]); const [cap, setCap] = useState(4); const [topic, setTopic] = useState('');
  const [doneId, setDoneId] = useState('');
  const [saving, setSaving] = useState(false);

  const sel = free.find((f) => slotKey(f) === slotK);
  const t = sel ? { day: sel.day, start: sel.start, end: Math.min(sel.end, sel.start + 90) } : { day: mDay, start: mStart, end: mEnd };
  const conflict = classConflict(s.classes, t.day, t.start, t.end);
  const roomConflict = myRooms(s).find((r) => {
    const st = roomStatus(r, s);
    return st !== 'ended' && st !== 'cancelled' && r.day === t.day && r.start < t.end && t.start < r.end;
  });
  const bad = t.end <= t.start;
  const finalTitle = title.trim() || `${activity} 같이 해요`;
  const notified = recommendPeople(s).filter((p) => p.slots.some((x) => x.day === t.day && x.start < t.end && t.start < x.end)).length;
  const TIMES = Array.from({ length: 25 }, (_, i) => 540 + i * 30);

  if (doneId) {
    return (
      <div className="page narrow col center" style={{ alignItems: 'center', paddingTop: 48, gap: 16 }}>
        <div className="bigcheck"><Icon n="check" s={48} /></div>
        <h1>모임이 열렸어요</h1>
        <div className="card" style={{ width: '100%', textAlign: 'left' }}><h3>{finalTitle}</h3><p className="cap num">{DAYS[t.day]} {hm(t.start)}–{hm(t.end)} · {place} · 최대 {cap}명</p></div>
        <p>공강이 겹치는 <b className="num">{notified}</b>명에게 알려요.</p>
        <p className="cap">시작 30분 전에 자동으로 모집이 마감돼요.</p>
        <div className="row" style={{ width: '100%' }}>
          <button className="btn secondary grow" onClick={() => navigator.clipboard?.writeText(`${location.origin}${location.pathname}#/rooms/${doneId}`).catch(() => {})}>공유하기</button>
          <button className="btn grow" onClick={() => nav(isServerId(doneId) ? `/rooms/${doneId}` : `/chat/chat-${doneId}`, { replace: true })}>{isServerId(doneId) ? '모임 보러 가기' : '채팅방으로'}</button>
        </div>
      </div>
    );
  }
  const submit = async () => {
    if (saving) return;
    setSaving(true);
    try {
      const id = await a.createRoom({ title: finalTitle, activity, ...t, place, cap, topic: topic.trim() || `${activity} 같이 해요` });
      if (id) setDoneId(id);
    } catch {
      // 에러 토스트는 client.ts가 표시한다
    } finally {
      setSaving(false);
    }
  };
  return (
    <div className="page narrow">
      <TopBar title="모임 만들기" />
      <div className="col" style={{ gap: 20 }}>
        <div className="field"><span className="label">활동</span><div className="chips">{ACTIVITIES.map((x) => <Chip key={x} on={activity === x} onClick={() => setActivity(x)}>{x}</Chip>)}</div></div>
        <div className="field"><label htmlFor="ttl">제목</label><input id="ttl" className="input" maxLength={30} value={title} onChange={(e) => setTitle(e.target.value)} placeholder={`${activity} 같이 해요`} /></div>
        <div className="field"><span className="label">시간 · 내 공강에서 고르기</span>
          <div className="chips">{free.map((f) => <Chip key={slotKey(f)} lime on={slotK === slotKey(f)} onClick={() => setSlotK(slotKey(f))}>{slotLabel(f)}</Chip>)}<Chip on={slotK === 'manual'} onClick={() => setSlotK('manual')}>직접 선택</Chip></div>
          {slotK === 'manual' && (
            <div className="row" style={{ marginTop: 8 }}>
              <select aria-label="요일" className="input" value={mDay} onChange={(e) => setMDay(+e.target.value as Day)}>{DAYS.map((d, i) => <option key={d} value={i}>{d}요일</option>)}</select>
              <select aria-label="시작" className="input" value={mStart} onChange={(e) => setMStart(+e.target.value)}>{TIMES.slice(0, -1).map((x) => <option key={x} value={x}>{hm(x)}</option>)}</select>
              <select aria-label="종료" className="input" value={mEnd} onChange={(e) => setMEnd(+e.target.value)}>{TIMES.slice(1).map((x) => <option key={x} value={x}>{hm(x)}</option>)}</select>
            </div>)}
          {bad && <span className="help err">종료 시간이 시작보다 늦어야 해요.</span>}
          {conflict && !bad && <span className="help err">수업과 겹쳐요 · {conflict.name}</span>}
          {roomConflict && !bad && <span className="help err">이미 참여 중인 모임({roomConflict.title})과 시간이 겹쳐요</span>}
        </div>
        <div className="field"><span className="label">장소</span><div className="chips">{PLACES.map((p) => <Chip key={p} on={place === p} onClick={() => setPlace(p)}>{p}</Chip>)}</div>
          <span className="help">첫 만남은 교내 공개 장소를 추천해요.</span></div>
        <div className="field"><span className="label">정원 (2~8명)</span>
          <div className="row"><button className="iconbtn" aria-label="줄이기" disabled={cap <= 2} onClick={() => setCap(cap - 1)}>−</button><b className="num" style={{ minWidth: 40, textAlign: 'center', fontSize: 20 }}>{cap}</b><button className="iconbtn" aria-label="늘리기" disabled={cap >= 8} onClick={() => setCap(cap + 1)}>+</button></div></div>
        <div className="field"><label htmlFor="tp">주제 · 소개</label><textarea id="tp" className="input" rows={3} maxLength={100} value={topic} onChange={(e) => setTopic(e.target.value)} placeholder="예: 카탄, 뱅 같은 가벼운 게임" /><span className="help num">{topic.length}/100</span></div>
        <div className="card flat col" style={{ gap: 4 }}><span className="over">요약</span><h3>{finalTitle}</h3><p className="cap num">{DAYS[t.day]} {hm(t.start)}–{hm(t.end)} · {place} · {cap}명</p></div>
        <button className="btn block" disabled={bad || !!conflict || !!roomConflict || saving} onClick={submit}>{saving ? '여는 중...' : '방 열기'}</button>
      </div>
    </div>
  );
}
