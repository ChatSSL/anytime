import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useApp } from '../store';
import { Slot, User } from '../types';
import { freeSlots, intersectAll, slotKey, slotLabel } from '../lib/time';
import { sharedConcepts } from '../lib/ai';
import { MannerGauge, Avatar, Chip, Empty, FreeBadge, Icon, Sheet, Skeleton, TopBar } from '../components/ui';
import { SafetyMenu } from '../components/safety';
import { dmChat, mannerLabel, meUser, userById, wantedSlots } from '../lib/derive';
import { authApi } from '../api/auth';
import { meApi } from '../api/me';
import { toUser } from '../api/recommend';

const isServerId = (id?: string) => !!id && /^\d+$/.test(id);

export function PersonDetail() {
  const { id } = useParams(); const nav = useNavigate(); const [sp] = useSearchParams();
  const { s, a } = useApp();
  const loggedIn = authApi.isLoggedIn();
  const serverMode = loggedIn && isServerId(id);
  const known = id ? userById(s, id) : undefined;
  // 추천 목록에 없는 서버 사용자(숫자 id)는 공개 프로필만 받아 온다. 차단 관계면 서버가 404를 준다
  const [remote, setRemote] = useState<User | null | undefined>(undefined);
  useEffect(() => {
    if (loggedIn) {
      a.loadDmRequests();
      a.loadChats();
    }
  }, [a, loggedIn]);
  useEffect(() => {
    if (known || !isServerId(id)) return;
    setRemote(undefined);
    meApi.user(Number(id)).then((p) => setRemote(toUser(p))).catch(() => setRemote(null));
  }, [id, known]);
  const u = known ?? remote ?? undefined;
  const [req, setReq] = useState(false); const [menu, setMenu] = useState(false); const [busy, setBusy] = useState(false);
  const [slot, setSlot] = useState(''); const [act, setAct] = useState(''); const [msg, setMsg] = useState('');
  useEffect(() => { if (sp.get('request')) setReq(true); }, [sp]);

  if (!known && isServerId(id) && remote === undefined) {
    return <div className="page narrow"><TopBar title="" /><div className="card col"><Skeleton h={44} w={44} /><Skeleton h={18} w="60%" /><Skeleton h={14} /></div></div>;
  }
  if (!u || u.id === 'staff' || s.blocks.includes(u.id)) {
    return <div className="page"><TopBar title="프로필" /><Empty title="볼 수 없는 프로필이에요" text="차단했거나 없는 사용자예요." action={<button className="btn m" onClick={() => nav('/home')}>홈으로</button>} /></div>;
  }
  // 서버 사용자는 시간표를 받지 않으므로 서버가 계산한 겹치는 공강을 쓴다. 시드 사용자(로그인 전 시연)는 직접 계산한다
  const reco = s.serverPeople?.find((p) => p.user.id === u.id);
  const slots: Slot[] = reco ? reco.slots : intersectAll(wantedSlots(s), freeSlots(u.classes));
  const shared = reco ? reco.shared : sharedConcepts(meUser(s).interests, u.interests);
  const serverDm = serverMode ? s.serverChats?.find((c) => c.kind === 'dm' && c.otherUser && String(c.otherUser.id) === u.id) : undefined;
  const localDm = serverMode ? undefined : dmChat(s, u.id);
  const dmId = serverDm ? String(serverDm.id) : localDm?.id;
  const sent = serverMode
    ? !!s.serverDmRequests?.sent.some((r) => String(r.toUser.id) === u.id && r.status === 'pending')
    : s.sentRequests.includes(u.id);
  const incomingReq = serverMode
    ? s.serverDmRequests?.received.find((r) => String(r.fromUser.id) === u.id && r.status === 'pending')
    : undefined;
  const acts = [...new Set([...shared, ...u.interests])].slice(0, 6);
  const chosen = slot || (slots[0] ? slotKey(slots[0]) : '');
  const chosenSlot = slots.find((x) => slotKey(x) === chosen);
  const send = async () => {
    if (!chosenSlot || busy) return;
    setBusy(true);
    try {
      await a.requestDm(u.id, chosenSlot, act || acts[0] || '만남', msg.trim());
      setMsg('');
      setReq(false);
    } catch {
      // 에러 토스트는 client.ts가 띄운다
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page narrow">
      <TopBar title="" right={<button className="iconbtn" aria-label="신고 · 차단" onClick={() => setMenu(true)}><Icon n="flag" /></button>} />
      <div className="col" style={{ gap: 20 }}>
        <div className="row" style={{ gap: 16 }}>
          <Avatar user={u} size="lg" />
          <div className="grow col" style={{ gap: 2 }}><h1 style={{ fontSize: 24 }}>{u.nick}</h1><p className="cap">{u.dept || '학과 비공개'}</p></div>
        </div>
        <div className="card"><MannerGauge value={u.manner} /><p className="cap" style={{ marginTop: 6 }}>매너 점수 {mannerLabel(u.manner)} · 최근 모임 <span className="num">{u.meetups}</span>번 참여</p></div>

        <div className="card col" style={{ gap: 8 }}>
          <h3 className="row" style={{ gap: 6 }}><Icon n="spark" s={18} />AI가 추천한 이유</h3>
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            <li>{slots[0] ? `${slotLabel(slots[0])} 공강이 겹쳐요` : '겹치는 공강이 아직 없어요'}</li>
            <li>{shared.length ? `${shared.join(', ')} 취향이 비슷해요` : '관심 활동이 조금 달라서 새로운 이야기를 나눌 수 있어요'}</li>
            <li>최근 모임에 {u.meetups}번 참여했어요</li>
          </ul>
          <p className="cap">추천은 공강과 취향을 기준으로 해요.</p>
        </div>

        <div className="col" style={{ gap: 8 }}>
          <h3>겹치는 공강</h3>
          <div className="chips">{slots.length ? slots.map((x) => <FreeBadge key={slotKey(x)} slot={x} />) : <span className="cap">겹치는 시간이 없어요</span>}</div>
          <p className="cap row" style={{ gap: 6 }}><Icon n="lock" s={14} />상대의 전체 시간표는 보이지 않아요.</p>
        </div>

        <div className="col" style={{ gap: 8 }}><h3>관심 활동</h3><div className="chips">{u.interests.map((i) => <span key={i} className="chip sm">{i}</span>)}</div></div>

        <div className="cta">
          {dmId ? <button className="btn" onClick={() => nav(`/chat/${dmId}`)}>채팅 가기</button>
            : incomingReq ? <button className="btn" onClick={() => nav('/notifications')}>받은 1:1 요청 확인하기</button>
              : <button className="btn" disabled={sent || !slots.length} onClick={() => setReq(true)}>{sent ? '요청을 보냈어요' : '1:1 만남 요청하기'}</button>}
        </div>
      </div>

      <Sheet open={req} onClose={() => setReq(false)} title="1:1 만남 요청">
        <div className="field"><span className="label">만날 공강 (겹치는 시간만)</span>
          <div className="col" style={{ gap: 4 }} role="radiogroup">{slots.map((x) => <label key={slotKey(x)} className="check" style={{ padding: '6px 0' }}><input type="radio" name="slot" checked={chosen === slotKey(x)} onChange={() => setSlot(slotKey(x))} /><span className="num">{slotLabel(x)}</span></label>)}</div></div>
        <div className="field"><span className="label">하고 싶은 활동</span><div className="chips">{acts.map((x) => <Chip key={x} on={(act || acts[0]) === x} onClick={() => setAct(x)}>{x}</Chip>)}</div></div>
        <div className="field"><label htmlFor="pm">한 줄 메시지 (선택)</label><input id="pm" className="input" maxLength={50} value={msg} onChange={(e) => setMsg(e.target.value)} placeholder="예: 같이 카탄 한 판 어때요?" /><span className="help num">{msg.length}/50</span></div>
        <button className="btn block" disabled={!chosenSlot || busy} onClick={send}>{busy ? '요청 보내는 중...' : '요청 보내기'}</button>
        <p className="cap center">첫 만남은 교내 공개 장소를 추천해요.</p>
      </Sheet>
      <SafetyMenu open={menu} onClose={() => setMenu(false)} target={u} />
    </div>
  );
}
