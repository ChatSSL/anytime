import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApp } from '../store';
import { ME_ID, recommendRooms, userById } from '../lib/derive';
import { Avatar, Chip, Empty, Icon, RoomCard, TopBar } from '../components/ui';

const LABELS = ['별로예요', '아쉬워요', '보통이에요', '좋았어요', '최고예요'];
const LOW = ['시간 약속을 안 지켰어요', '불편한 말을 했어요', '원치 않는 연락', '기타'];

export function Review() {
  const { roomId } = useParams(); const nav = useNavigate();
  const { s, a } = useApp();
  const room = s.rooms.find((r) => r.id === roomId);
  const others = (room?.members ?? []).filter((m) => m !== ME_ID && m !== 'staff').map((m) => userById(s, m)!).filter(Boolean);
  const [step, setStep] = useState(1);
  const [here, setHere] = useState<Record<string, boolean>>(Object.fromEntries(others.map((u) => [u.id, true])));
  const [score, setScore] = useState<Record<string, number>>({}); const [why, setWhy] = useState<Record<string, string>>({});
  const [sat, setSat] = useState(0); const [note, setNote] = useState('');

  if (!room || !room.members.includes(ME_ID)) return <div className="page"><TopBar title="평가" /><Empty title="평가할 모임이 없어요" action={<button className="btn m" onClick={() => nav('/home')}>홈으로</button>} /></div>;
  if (s.rated.includes(room.id) && step < 3) return <div className="page"><TopBar title="평가" /><Empty title="이미 평가를 마쳤어요" action={<button className="btn m" onClick={() => nav('/me')}>마이페이지로</button>} /></div>;

  const present = others.filter((u) => here[u.id]);
  const confirmed = present.length + 1; // 나 포함
  const total = others.length + 1;
  const lowMissing = present.some((u) => (score[u.id] ?? 0) > 0 && (score[u.id] ?? 0) <= 2 && !why[u.id]);
  const allScored = present.every((u) => score[u.id]) && sat > 0;
  const next = recommendRooms(s)[0];

  const finish = () => {
    const vals = present.map((u) => score[u.id]);
    a.attend(room.id, [ME_ID, ...present.map((u) => u.id)]);
    a.rate(room.id, vals.length ? vals.reduce((x, y) => x + y, 0) / vals.length : sat);
    setStep(3);
  };

  return (
    <div className="flowpage" style={{ maxWidth: 560 }}>
      <TopBar title={room.title} onBack={() => (step === 2 ? setStep(1) : nav(-1))} right={step < 3 ? <span className="cap num">{step}/2</span> : undefined} />
      {step === 1 && (
        <>
          <div className="body">
            <h1>{room.title} 어땠어요?</h1>
            <p className="muted">함께한 사람이 실제로 왔는지 먼저 확인해요.</p>
            <div className="list card" style={{ padding: '0 16px' }}>
              {others.map((u) => (
                <div key={u.id} className="listrow"><Avatar user={u} /><b className="grow">{u.nick}</b>
                  <div className="chips"><Chip on={here[u.id]} onClick={() => setHere({ ...here, [u.id]: true })}>왔어요</Chip><Chip on={!here[u.id]} onClick={() => setHere({ ...here, [u.id]: false })}>안 왔어요</Chip></div></div>
              ))}
              {others.length === 0 && <p className="cap" style={{ padding: 16 }}>다른 참여자가 없어요.</p>}
            </div>
            <div className="notice"><Icon n="info" s={18} />참석자 과반이 확인하면 모임이 완료로 기록돼요. 지금 {confirmed}/{total}명이 확인됐어요.</div>
          </div>
          <div className="cta"><button className="btn block" onClick={() => setStep(2)}>다음: 평가하기</button></div>
        </>
      )}
      {step === 2 && (
        <>
          <div className="body">
            <h1>서로 평가해요</h1>
            <p className="cap">평가한 사람은 상대에게 공개되지 않아요.</p>
            {present.map((u) => (
              <div key={u.id} className="card col">
                <div className="row"><Avatar user={u} /><b>{u.nick}</b></div>
                <div className="chips" role="radiogroup" aria-label={`${u.nick} 평가`}>{LABELS.map((l, i) => <Chip key={l} on={score[u.id] === i + 1} onClick={() => setScore({ ...score, [u.id]: i + 1 })}>{i + 1} · {l}</Chip>)}</div>
                {(score[u.id] ?? 9) <= 2 && (
                  <div className="field"><span className="label">이유를 골라주세요 (필수)</span>
                    <div className="chips">{LOW.map((r) => <Chip key={r} on={why[u.id] === r} onClick={() => setWhy({ ...why, [u.id]: r })}>{r}</Chip>)}</div></div>
                )}
              </div>
            ))}
            {present.length === 0 && <p className="cap">평가할 참석자가 없어요. 만족도만 남겨 주세요.</p>}
            <div className="card col"><b>전체 만족도</b>
              <div className="stars" role="radiogroup" aria-label="전체 만족도">{[1, 2, 3, 4, 5].map((n) => <button key={n} className={n <= sat ? 'on' : ''} aria-label={`${n}점`} onClick={() => setSat(n)}><Icon n="star" s={30} fill={n <= sat} /></button>)}</div>
              <label className="label" htmlFor="note">한 줄 후기 (선택)</label>
              <input id="note" className="input" maxLength={60} value={note} onChange={(e) => setNote(e.target.value)} placeholder="다음에 오는 사람에게 도움이 돼요" /></div>
          </div>
          <div className="cta"><button className="btn block" disabled={!allScored || lowMissing} onClick={finish}>평가 저장</button></div>
        </>
      )}
      {step === 3 && (
        <>
          <div className="body" style={{ alignItems: 'center', textAlign: 'center', gap: 16 }}>
            <div className="bigcheck"><Icon n="check" s={48} /></div>
            <h1>평가가 저장됐어요</h1>
            <p className="muted">매너 점수는 <b className="num">{s.manner.toFixed(1)}°</b>예요. 모임 완료로 기록됐어요.</p>
            {next && <div style={{ width: '100%', textAlign: 'left' }}><h3 style={{ margin: '8px 0' }}>다음 공강에 또 만나볼까요?</h3><RoomCard room={next.room} full /></div>}
          </div>
          <div className="cta"><button className="btn secondary" onClick={() => nav('/me')}>마이페이지</button><button className="btn" onClick={() => nav('/rooms')}>모임 더 보기</button></div>
        </>
      )}
    </div>
  );
}
