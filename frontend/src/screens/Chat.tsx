import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useApp } from '../store';
import { DAYS, User } from '../types';
import { abs, dur, hm } from '../lib/time';
import { ME_ID, userById } from '../lib/derive';
import { Avatar, Empty, Icon, Skeleton, TopBar } from '../components/ui';
import { ReportIcon, SosButton } from '../components/safety';
import { authApi } from '../api/auth';
import { chatsApi, ServerChatMessage } from '../api/chats';
import { chatStomp } from '../api/stomp';
import { toUser } from '../api/recommend';
import { roomsApi } from '../api/rooms';

const QUICK = ['장소 정했어요', '5분 늦어요', '도착했어요'];
const isServerId = (id?: string) => !!id && /^\d+$/.test(id);

function fmtIsoTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const m = iso.match(/T(\d{2}:\d{2})/);
  return m ? m[1] : '';
}

export function ChatList() {
  const { s, a } = useApp();
  const loggedIn = authApi.isLoggedIn();
  useEffect(() => {
    if (loggedIn) a.loadChats();
  }, [a, loggedIn]);

  const chatIdsKey = loggedIn && s.serverChats
    ? s.serverChats
      .filter((c) => !(c.kind === 'dm' && c.otherUser && s.blocks.includes(String(c.otherUser.id))))
      .map((c) => c.id)
      .sort((x, y) => x - y)
      .join(',')
    : '';

  useEffect(() => {
    if (!loggedIn || !chatIdsKey) return;
    const ids = chatIdsKey.split(',').map(Number).filter((n) => Number.isFinite(n));
    const unsubs = ids.map((id) =>
      chatStomp.subscribeChat(id, (incoming) => a.applyIncomingMessage(incoming, false))
    );
    return () => { unsubs.forEach((fn) => fn()); };
  }, [a, loggedIn, chatIdsKey]);

  if (loggedIn) {
    if (s.serverChats === undefined) {
      return (
        <div className="page">
          <div className="topbar"><h2 style={{ fontSize: 22 }}>채팅</h2></div>
          <div className="card col"><Skeleton h={18} w="50%" /><Skeleton h={14} /><Skeleton h={14} w="70%" /></div>
        </div>
      );
    }
    const serverChats = s.serverChats.filter(
      (c) => !(c.kind === 'dm' && c.otherUser && s.blocks.includes(String(c.otherUser.id)))
    );
    return (
      <div className="page">
        <div className="topbar"><h2 style={{ fontSize: 22 }}>채팅</h2></div>
        {serverChats.length === 0 ? (
          <Empty title="아직 채팅이 없어요" text="모임에 합류하거나 1:1 요청이 수락되면 여기에 생겨요." action={<Link to="/rooms" className="btn m">모임 둘러보기</Link>} />
        ) : (
          <div className="list card" style={{ padding: '0 16px' }}>
            {serverChats.map((c) => {
              const other = c.otherUser ? toUser(c.otherUser) : undefined;
              const title = c.room?.title ?? other?.nick ?? '채팅';
              const time = fmtIsoTime(c.lastMessage?.createdAt ?? c.lastActivityAt);
              const preview = c.lastMessage?.text ?? (c.mission ? 'AI 대화 주제가 도착했어요' : '대화를 시작해 보세요');
              return (
                <Link key={c.id} to={`/chat/${c.id}`} className="listrow" style={{ color: 'inherit' }}>
                  {c.room ? <span className="act">{c.room.activity[0]}</span> : <Avatar user={other} />}
                  <div className="grow">
                    <div className="row between"><h3>{title}</h3>{time && <span className="cap num">{time}</span>}</div>
                    <p className="cap" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{preview}</p>
                  </div>
                  {c.unreadCount > 0 && <span className="pill mine num">{c.unreadCount}</span>}
                </Link>
              );
            })}
          </div>
        )}
      </div>
    );
  }

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
  const { s, a } = useApp();
  const loggedIn = authApi.isLoggedIn();
  const serverMode = loggedIn && isServerId(id);

  useEffect(() => {
    if (serverMode) a.loadChats();
  }, [a, serverMode, id]);

  if (serverMode) {
    return (
      <div className="page" style={{ padding: 0, maxWidth: 'none' }}>
        <ChatPanel chatId={id!} />
      </div>
    );
  }

  const c = s.chats.find((x) => x.id === id);
  if (!c) return <div className="page"><TopBar title="채팅" /><Empty title="채팅방을 찾을 수 없어요" action={<button className="btn m" onClick={() => nav('/chats')}>목록으로</button>} /></div>;
  return (
    <div className="page" style={{ padding: 0, maxWidth: 'none' }}>
      <ChatPanel chatId={c.id} />
    </div>
  );
}

export function ChatPanel({ chatId, embedded }: { chatId: string; embedded?: boolean }) {
  if (authApi.isLoggedIn() && isServerId(chatId)) {
    return <ServerChatPanel chatId={chatId} embedded={embedded} />;
  }
  return <LocalChatPanel chatId={chatId} embedded={embedded} />;
}

function ServerChatPanel({ chatId, embedded }: { chatId: string; embedded?: boolean }) {
  const { s, a } = useApp(); const nav = useNavigate();
  const numericId = Number(chatId);
  const summary = s.serverChats?.find((x) => x.id === numericId);
  const [messages, setMessages] = useState<ServerChatMessage[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [text, setText] = useState('');
  const [link, setLink] = useState(summary?.link ?? '');
  const [savingLink, setSavingLink] = useState(false);
  const [roomMembers, setRoomMembers] = useState<User[]>([]);
  const myUserIdRef = useRef(s.myUserId);
  myUserIdRef.current = s.myUserId;
  const end = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setLink(summary?.link ?? '');
  }, [summary?.id, summary?.link]);

  useEffect(() => {
    let alive = true;
    setMessages([]);
    setLoading(true);
    setNotFound(false);
    chatsApi.messages(numericId, 0, 50, { quiet: true })
      .then((res) => {
        if (!alive) return;
        const fetched = [...res.messages].reverse();
        setMessages((prev) => {
          const seen = new Set(fetched.map((m) => m.id));
          const extra = prev.filter((m) => m.chatId === numericId && !seen.has(m.id));
          return [...fetched, ...extra].sort((x, y) => x.id - y.id);
        });
        setPage(res.page);
        setHasNext(res.hasNext);
        a.loadChats();
      })
      .catch(() => {
        if (!alive) return;
        setNotFound(true);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => { alive = false; };
  }, [a, numericId]);

  // 14단계: /topic/chat.{chatId} 실시간 구독 (id 기반 중복 제거 + 보고 있는 동안 읽음 동기화)
  useEffect(() => {
    if (notFound) return;
    return chatStomp.subscribeChat(numericId, (incoming) => {
      const mine = incoming.senderId !== null && myUserIdRef.current !== undefined && incoming.senderId === myUserIdRef.current;
      setMessages((prev) => {
        if (prev.some((m) => m.id === incoming.id)) return prev;
        return [...prev, { ...incoming, mine }];
      });
      a.applyIncomingMessage(incoming, true);
      if (!incoming.system && !mine) {
        chatsApi.markRead(numericId, { quiet: true }).catch(() => null);
      }
    });
  }, [a, numericId, notFound]);

  useEffect(() => {
    if (embedded || !summary?.roomId) { setRoomMembers([]); return; }
    let alive = true;
    roomsApi.get(summary.roomId, { quiet: true })
      .then((detail) => {
        if (alive) setRoomMembers(detail.members.map(toUser).filter((u) => u.nick !== s.profile?.nick));
      })
      .catch(() => { if (alive) setRoomMembers([]); });
    return () => { alive = false; };
  }, [embedded, summary?.roomId, s.profile?.nick]);

  useEffect(() => {
    if (!loading) end.current?.scrollIntoView({ block: 'end' });
  }, [loading, messages.length]);

  const loadOlder = async () => {
    if (!hasNext || loadingMore) return;
    setLoadingMore(true);
    try {
      const res = await chatsApi.messages(numericId, page + 1, 50, { quiet: true });
      const older = [...res.messages].reverse();
      setMessages((prev) => {
        const seen = new Set(prev.map((m) => m.id));
        return [...older.filter((m) => !seen.has(m.id)), ...prev];
      });
      setPage(res.page);
      setHasNext(res.hasNext);
    } finally {
      setLoadingMore(false);
    }
  };

  const saveLink = async () => {
    if (savingLink) return;
    setSavingLink(true);
    try {
      const saved = await a.setLink(chatId, link);
      setLink(saved ?? '');
    } catch {
      // 에러 토스트는 client.ts가 띄운다
    } finally {
      setSavingLink(false);
    }
  };

  const submit = () => {
    const t = text.trim();
    if (!t) return;
    a.send(chatId, t);
    setText('');
  };

  if (notFound) {
    if (embedded) return null;
    return (
      <div className="page">
        <TopBar title="채팅" />
        <Empty title="채팅방을 찾을 수 없어요" text="접근 권한이 없거나 존재하지 않는 채팅방이에요." action={<button className="btn m" onClick={() => nav('/chats')}>목록으로</button>} />
      </div>
    );
  }

  const room = summary?.room ?? null;
  const other = summary?.otherUser ? toUser(summary.otherUser) : undefined;
  const title = room?.title ?? other?.nick ?? '채팅';
  const candidates = other ? [other] : roomMembers;

  return (
    <div className={`chatwrap ${embedded ? 'embedded' : ''}`}>
      {!embedded && (
        <div className="topbar" style={{ flex: 'none' }}>
          <button className="iconbtn" aria-label="뒤로가기" onClick={() => nav(-1)} style={{ marginLeft: -10 }}><Icon n="back" /></button>
          <div className="grow">
            <h2 style={{ fontSize: 17 }}>{title}</h2>
            <p className="cap">{room ? `${room.memberCount}명 참여` : other?.dept || ''}</p>
          </div>
          {other && <ReportIcon target={other} />}
          {room && <ReportIcon target={candidates[0]} roomId={String(room.id)} />}
        </div>
      )}
      {embedded && <div className="row between" style={{ padding: '12px 0 0' }}><h3>{title} 채팅</h3><span className="cap">{room ? `${room.memberCount}명` : ''}</span></div>}

      {room && (
        <div className="card flat row" style={{ padding: 10, gap: 8, flex: 'none' }}>
          <Icon n="clock" s={18} />
          <span className="grow cap"><b className="num">{DAYS[room.day]} {hm(room.start)}</b> · {room.place}</span>
          <Link to={`/rooms/${room.id}`} className="cap">모임 상세</Link>
        </div>
      )}
      {other && (
        <div className="notice" style={{ flex: 'none', margin: '4px 0' }}><Icon n="shield" s={18} />첫 만남은 교내 공개 장소를 추천해요.</div>
      )}

      <div className="msgs" role="log" aria-live="polite">
        {hasNext && (
          <button className="btn ghost s" style={{ alignSelf: 'center' }} disabled={loadingMore} onClick={loadOlder}>
            {loadingMore ? '불러오는 중...' : '이전 메시지 더 보기'}
          </button>
        )}
        {summary?.mission && (
          <div className="aicard">
            <div className="row" style={{ gap: 6, color: 'var(--primary)', fontWeight: 700 }}><Icon n="spark" s={18} />AI 대화 주제</div>
            <ul style={{ margin: 0, paddingLeft: 18 }}>{summary.mission.questions.map((q) => <li key={q}>{q}</li>)}</ul>
            <div className="divider" />
            <div className="row" style={{ gap: 6 }}>
              <span><b>오늘의 미션</b> · {summary.mission.mission} <b style={{ color: 'var(--primary)' }}>{summary.missionDone ? '완료' : ''}</b></span>
            </div>
            <p className="cap">참여자 공통 관심사로 만들었어요. 모임마다 한 번만 만들어요.</p>
          </div>
        )}
        {loading ? (
          <div className="col" style={{ gap: 8 }}><Skeleton h={18} w="60%" /><Skeleton h={18} w="45%" /></div>
        ) : messages.length === 0 ? (
          <div className="sysmsg">아직 메시지가 없어요.</div>
        ) : (
          messages.map((m) => {
            if (m.system) return <div key={m.id} className="sysmsg">{m.text}</div>;
            const isMine = m.mine || (s.myUserId !== undefined && m.senderId === s.myUserId);
            return (
              <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start', gap: 2 }}>
                {!isMine && m.senderNick && <span className="cap" style={{ marginLeft: 4 }}>{m.senderNick}</span>}
                <div className={`bub ${isMine ? 'me' : ''}`}>{m.text}</div>
              </div>
            );
          })
        )}
        <div ref={end} />
      </div>

      {summary?.kind === 'room' && (
        <details className="card flat" style={{ flex: 'none', marginBottom: 6 }}>
          <summary className="cap" style={{ cursor: 'pointer' }}>채팅이 불안정하면 오픈채팅 링크로 이어가요</summary>
          <div className="row" style={{ marginTop: 8 }}>
            <input className="input" aria-label="오픈채팅 링크" style={{ height: 44 }} placeholder="https://open.kakao.com/..." value={link} onChange={(e) => setLink(e.target.value)} />
            <button className="btn m" disabled={savingLink} onClick={saveLink}>{savingLink ? '저장 중...' : '저장'}</button>
          </div>
          {summary.link && <a className="btn secondary m block" style={{ marginTop: 8 }} href={summary.link} target="_blank" rel="noreferrer">오픈채팅으로 이동</a>}
        </details>
      )}

      <div className="composer">
        {other && <button className="btn secondary m" onClick={() => a.send(chatId, '이 시간으로 정해요!')}>이 시간으로 정하기</button>}
        <div className="chips scroll" style={{ margin: 0, padding: 0 }}>{QUICK.map((q) => <button key={q} className="chip" onClick={() => a.send(chatId, q)}>{q}</button>)}</div>
        <form className="row" onSubmit={(e) => { e.preventDefault(); submit(); }}>
          <input className="input" maxLength={1000} style={{ height: 48, borderRadius: 24 }} aria-label="메시지 입력" placeholder="메시지를 입력하세요" value={text} onChange={(e) => setText(e.target.value)} />
          <button className="iconbtn" type="submit" aria-label="보내기" style={{ background: 'var(--primary)', color: 'var(--on-primary)', borderRadius: 24 }}><Icon n="send" /></button>
        </form>
      </div>
      {!embedded && <SosButton candidates={candidates} roomId={room ? String(room.id) : undefined} />}
    </div>
  );
}

function LocalChatPanel({ chatId, embedded }: { chatId: string; embedded?: boolean }) {
  const { s, a } = useApp(); const nav = useNavigate();
  const c = s.chats.find((x) => x.id === chatId)!;
  const [text, setText] = useState(''); const [link, setLink] = useState(c?.link ?? '');
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => { if (c?.unread) a.markChatRead(chatId); }, [a, chatId, c?.unread]);
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
