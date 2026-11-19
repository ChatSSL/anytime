import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useApp } from '../store';
import { DAYS } from '../types';
import { abs, dur, hm } from '../lib/time';
import { ME_ID, userById } from '../lib/derive';
import { Avatar, Empty, Icon, TopBar } from '../components/ui';
import { ReportIcon, SosButton } from '../components/safety';

const QUICK = ['장소 정했어요', '5분 늦어요', '도착했어요'];

export function ChatList() {
  const { s } = useApp();
  const chats = s.chats.filter((c) => !(c.kind === 'dm' && s.blocks.includes(c.userId!)));
  return (
    <div className="page">
      <div className="topbar"><h2 style={{ fontSize: 22 }}>채팅</h2></div>
      {chats.length === 0 ? (
        <Empty title="아직 채팅이 없어요" text="모임에 합류하거나 1:1 요청이 수락되면 여기에 생겨요." action={<Link to="/rooms" className="btn m">모임 둘러보기</Link>} />
      ) : (
        <div className="list card" style={{ padding: '0 16px' }}>
          {chats.map((c) => {
            const room = c.kind === 'room' ? s.rooms.find((r) => r.id === c.roomId) : undefined;
            const u = c.kind === 'dm' ? userById(s, c.userId!) : undefined;
            const last = [...c.messages].reverse().find((m) => m.from !== 'sys' || true);
            return (
              <Link key={c.id} to={`/chat/${c.id}`} className="listrow" style={{ color: 'inherit' }}>
                {room ? <span className="act">{room.activity[0]}</span> : <Avatar user={u} />}
                <div className="grow"><div className="row between"><h3>{room?.title ?? u?.nick}</h3>{last && <span className="cap num">{last.time}</span>}</div>
                  <p className="cap" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{last?.ai ? 'AI 대화 주제가 도착했어요' : last?.text}</p></div>
                {c.unread > 0 && <span className="pill mine num">{c.unread}</span>}
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function ChatPage() {
  const { id } = useParams(); const nav = useNavigate();
  const { s } = useApp();
  const c = s.chats.find((x) => x.id === id);
  if (!c) return <div className="page"><TopBar title="채팅" /><Empty title="채팅방을 찾을 수 없어요" action={<button className="btn m" onClick={() => nav('/chats')}>목록으로</button>} /></div>;
  return (
    <div className="page" style={{ padding: 0, maxWidth: 'none' }}>
      <ChatPanel chatId={c.id} />
    </div>
  );
}

export function ChatPanel({ chatId, embedded }: { chatId: string; embedded?: boolean }) {
  const { s, a } = useApp(); const nav = useNavigate();
  const c = s.chats.find((x) => x.id === chatId)!;
  const [text, setText] = useState(''); const [link, setLink] = useState(c?.link ?? '');
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => { if (c?.unread) a.markChatRead(chatId); }, [chatId, c?.unread]);
  useEffect(() => { end.current?.scrollIntoView({ block: 'end' }); }, [c?.messages.length]);
  if (!c) return null;

  const room = c.kind === 'room' ? s.rooms.find((r) => r.id === c.roomId) : undefined;
  const other = c.kind === 'dm' ? userById(s, c.userId!) : undefined;
  const members = room ? room.members.map((m) => userById(s, m)!).filter(Boolean) : other ? [other] : [];
  const title = room?.title ?? other?.nick ?? '';
  const left = room ? abs(room.day, room.start) - abs(s.clock.day, s.clock.min) : 0;
  const submit = () => { a.send(c.id, text); setText(''); };
  const nameOf = (id: string) => userById(s, id)?.nick ?? '';

  return (
    <div className={`chatwrap ${embedded ? 'embedded' : ''}`}>
      {!embedded && (
        <div className="topbar" style={{ flex: 'none' }}>
          <button className="iconbtn" aria-label="뒤로가기" onClick={() => nav(-1)} style={{ marginLeft: -10 }}><Icon n="back" /></button>
          <div className="grow"><h2 style={{ fontSize: 17 }}>{title}</h2><p className="cap">{room ? `${room.members.length}명 참여` : other?.dept || ''}</p></div>
          {other && <ReportIcon target={other} />}
          {room && <ReportIcon target={members.find((m) => m.id !== ME_ID && m.id !== 'staff')} roomId={room.id} />}
        </div>
      )}
      {embedded && <div className="row between" style={{ padding: '12px 0 0' }}><h3>{title} 채팅</h3><span className="cap">{room?.members.length}명</span></div>}

      {room && (
        <div className="card flat row" style={{ padding: 10, gap: 8, flex: 'none' }}>
          <Icon n="clock" s={18} />
          <span className="grow cap"><b className="num">{DAYS[room.day]} {hm(room.start)}</b> · {room.place}{left > 0 ? ` · ${dur(left)} 뒤` : ''}</span>
          <Link to={`/rooms/${room.id}`} className="cap">장소/시간 변경</Link>
        </div>
      )}
      {other && (
        <div className="notice" style={{ flex: 'none', margin: '4px 0' }}><Icon n="shield" s={18} />첫 만남은 교내 공개 장소를 추천해요.</div>
      )}

      <div className="msgs" role="log" aria-live="polite">
        {c.messages.map((m) => {
          if (m.from === 'sys') return <div key={m.id} className="sysmsg">{m.text}</div>;
          if (m.ai) return (
            <div key={m.id} className="aicard">
              <div className="row" style={{ gap: 6, color: 'var(--primary)', fontWeight: 700 }}><Icon n="spark" s={18} />AI 대화 주제</div>
              <ul style={{ margin: 0, paddingLeft: 18 }}>{m.ai.questions.map((q) => <li key={q}>{q}</li>)}</ul>
              <div className="divider" />
              <label className="check" style={{ alignItems: 'center' }}>
                <input type="checkbox" checked={m.ai.done} onChange={(e) => a.setMission(c.id, m.id, e.target.checked)} />
                <span><b>오늘의 미션</b> · {m.ai.mission} <b style={{ color: 'var(--primary)' }}>{m.ai.done ? '완료' : ''}</b></span>
              </label>
              <p className="cap">참여자 공통 관심사로 만들었어요. 모임마다 한 번만 만들어요.</p>
            </div>
          );
          const me = m.from === ME_ID;
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: me ? 'flex-end' : 'flex-start', gap: 2 }}>
              {!me && <span className="cap" style={{ marginLeft: 4 }}>{nameOf(m.from)}</span>}
              <div className={`bub ${me ? 'me' : ''}`}>{m.text}</div>
            </div>
          );
        })}
        <div ref={end} />
      </div>

      {room && (
        <details className="card flat" style={{ flex: 'none', marginBottom: 6 }}>
          <summary className="cap" style={{ cursor: 'pointer' }}>채팅이 불안정하면 오픈채팅 링크로 이어가요</summary>
          <div className="row" style={{ marginTop: 8 }}>
            <input className="input" aria-label="오픈채팅 링크" style={{ height: 44 }} placeholder="https://open.kakao.com/..." value={link} onChange={(e) => setLink(e.target.value)} />
            <button className="btn m" onClick={() => a.setLink(c.id, link.trim())}>저장</button>
          </div>
          {c.link && <a className="btn secondary m block" style={{ marginTop: 8 }} href={c.link} target="_blank" rel="noreferrer">오픈채팅으로 이동</a>}
        </details>
      )}

      <div className="composer">
        {other && <button className="btn secondary m" onClick={() => a.send(c.id, '이 시간으로 정해요!')}>이 시간으로 정하기</button>}
        <div className="chips scroll" style={{ margin: 0, padding: 0 }}>{QUICK.map((q) => <button key={q} className="chip" onClick={() => a.send(c.id, q)}>{q}</button>)}</div>
        <form className="row" onSubmit={(e) => { e.preventDefault(); submit(); }}>
          <input className="input" style={{ height: 48, borderRadius: 24 }} aria-label="메시지 입력" placeholder="메시지를 입력하세요" value={text} onChange={(e) => setText(e.target.value)} />
          <button className="iconbtn" type="submit" aria-label="보내기" style={{ background: 'var(--primary)', color: 'var(--on-primary)', borderRadius: 24 }}><Icon n="send" /></button>
        </form>
      </div>
      {!embedded && <SosButton candidates={members.filter((m) => m.id !== ME_ID && m.id !== 'staff')} roomId={room?.id} />}
    </div>
  );
}
