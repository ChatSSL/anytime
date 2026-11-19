import { Link, useNavigate } from 'react-router-dom';
import { useApp } from '../store';
import { Day, DAYS } from '../types';
import { USERS } from '../data';
import { hm } from '../lib/time';
import { meUser, myRooms, roomStatus, userById } from '../lib/derive';
import { Avatar, Chip, Empty, Icon, MannerGauge, TopBar } from '../components/ui';

const DEMO_TIMES: { label: string; day: Day; min: number }[] = [
  { label: '수 10:40 · 수업 중', day: 2, min: 640 },
  { label: '수 11:45 · 마감 임박', day: 2, min: 705 },
  { label: '수 12:10 · 공강 중', day: 2, min: 730 },
  { label: '목 12:00 · 인원 미달 취소', day: 3, min: 720 },
  { label: '금 09:00', day: 4, min: 540 },
];

export function Me() {
  const { s, a } = useApp(); const nav = useNavigate();
  const me = meUser(s); const mine = myRooms(s);
  const done = s.completed, cancelled = s.cancelLog.length;
  const rate = done + cancelled ? Math.round((done / (done + cancelled)) * 100) : 100;
  const upcoming = mine.filter((r) => ['open', 'soon', 'closed', 'full'].includes(roomStatus(r, s))).length;
  const rows: [string, string, string][] = [
    ['clock', '내 시간표 · 공강 수정', '/me/timetable'], ['star', '매너 점수 기준', '/me/manner'], ['lock', '개인정보 · 공개 범위', '/me/privacy'],
    ['bell', '알림 설정', '/notifications/settings'], ['users', `차단 목록 (${s.blocks.length})`, '/me/blocked'], ['shield', '이용 제한 안내', '/me/sanction'],
    ['spark', '프리미엄 · 광고 (확장 예정)', '/premium'], ['info', '상태 화면 모음', '/states'],
  ];
  return (
    <div className="page narrow">
      <div className="topbar"><h2 style={{ fontSize: 22 }}>마이페이지</h2></div>
      <div className="col" style={{ gap: 16 }}>
        <div className="card col">
          <div className="row" style={{ gap: 14 }}>
            <Avatar user={me} size="lg" />
            <div className="grow"><h2>{me.nick}</h2><p className="cap">{me.dept || '학과 비공개'} · {s.school}</p></div>
            <button className="btn secondary s" onClick={() => nav('/profile')}><Icon n="edit" s={16} />편집</button>
          </div>
          <div className="chips">{me.interests.map((i) => <span key={i} className="chip sm">{i}</span>)}</div>
          <MannerGauge value={s.manner} />
        </div>
        <div className="row" style={{ gap: 8 }}>
          {[['참여한 모임', `${done}`], ['모임 완료율', `${rate}%`], ['예정된 모임', `${upcoming}`]].map(([k, v]) => (
            <div key={k} className="card grow center"><div className="num" style={{ fontSize: 22, fontWeight: 700 }}>{v}</div><div className="cap">{k}</div></div>
          ))}
        </div>

        {mine.length > 0 && (
          <div className="col" style={{ gap: 4 }}><h3>내 모임</h3>
            <div className="list card" style={{ padding: '0 16px' }}>{mine.map((r) => (
              <Link key={r.id} to={`/rooms/${r.id}`} className="listrow" style={{ color: 'inherit' }}><span className="act">{r.activity[0]}</span>
                <div className="grow"><b>{r.title}</b><p className="cap num">{DAYS[r.day]} {hm(r.start)}–{hm(r.end)}{r.hostId === 'me' ? ' · 내가 연 모임' : ''}</p></div><Icon n="chev" s={18} /></Link>
            ))}</div></div>
        )}

        <div className="list card" style={{ padding: '0 16px' }}>
          {rows.map(([ic, label, to]) => <Link key={to} to={to} className="listrow" style={{ color: 'inherit' }}><Icon n={ic} s={20} /><span className="grow">{label}</span><Icon n="chev" s={18} /></Link>)}
          <button className="listrow" onClick={() => { if (window.confirm('처음 상태로 되돌릴까요? 시연 데이터가 모두 지워져요.')) a.reset(); }}><Icon n="close" s={20} /><span className="grow" style={{ color: 'var(--danger)' }}>로그아웃 · 처음부터 다시</span></button>
        </div>

        <div className="card flat col">
          <div className="row between"><h3>시연 시간 이동</h3><span className="pill mine">시연용</span></div>
          <p className="cap">지금 시각을 바꿔서 수업 중, 공강 중, 마감, 자동 취소 화면을 확인해요. 현재 {DAYS[s.clock.day]} {hm(s.clock.min)}.</p>
          <div className="chips">{DEMO_TIMES.map((t) => <Chip key={t.label} on={s.clock.day === t.day && s.clock.min === t.min} onClick={() => a.setClock(t.day, t.min)}>{t.label}</Chip>)}</div>
        </div>
      </div>
    </div>
  );
}

export function MannerRules() {
  const { s } = useApp();
  return (
    <div className="page narrow">
      <TopBar title="매너 점수 기준" />
      <div className="col" style={{ gap: 16 }}>
        <div className="card"><MannerGauge value={s.manner} /></div>
        <div className="card" style={{ overflowX: 'auto' }}>
          <table className="table">
            <thead><tr><th>올라가요</th><th>내려가요</th></tr></thead>
            <tbody>
              <tr><td>약속한 모임에 참석해요</td><td>연락 없이 불참해요 (노쇼)</td></tr>
              <tr><td>제시간에 도착해요</td><td>신고가 반복해서 확인돼요</td></tr>
              <tr><td>다른 참여자에게 좋은 평가를 받아요</td><td>이유 없이 반복해서 취소해요</td></tr>
            </tbody>
          </table>
        </div>
        <div className="notice lime">휴강·수업 연장 같은 불가피한 취소는 점수에 영향이 없어요. 기록만 남아요.</div>
        <div className="col" style={{ gap: 6 }}>
          <h3>평가는 이렇게 지켜요</h3>
          <ul className="cap" style={{ margin: 0, paddingLeft: 18 }}>
            <li>모임 완료가 확인된 실제 참여자만 평가할 수 있어요.</li>
            <li>한 사람의 평가가 점수에 미치는 영향에는 상한이 있어요.</li>
            <li>평가한 사람은 공개되지 않아요.</li>
            <li>낮은 평가에는 사유 선택이 필수예요.</li>
          </ul>
        </div>
        <button className="btn secondary block" disabled>받은 평가에 이의 제기하기 · 확장 예정</button>
      </div>
    </div>
  );
}

export function Privacy() {
  const nav = useNavigate();
  return (
    <div className="page narrow">
      <TopBar title="개인정보 · 공개 범위" />
      <div className="col" style={{ gap: 16 }}>
        <div className="notice"><Icon n="lock" s={18} />다른 사용자에게는 겹치는 공강 시간만 보여요.</div>
        <div className="card col"><h3>공개</h3>
          {['닉네임', '학과 (내가 선택한 경우만)', '관심 활동', '겹치는 공강 시간'].map((t) => <div key={t} className="row"><Icon n="check" s={18} />{t}</div>)}</div>
        <div className="card col"><h3>비공개</h3>
          {['실명', '학번', '학교 인증 정보', '전체 시간표'].map((t) => <div key={t} className="row muted"><Icon n="lock" s={18} />{t}</div>)}</div>
        <div className="card col" style={{ gap: 4 }}><h3>위치</h3><p className="cap">위치는 모임 장소로만 공유돼요. 실시간 위치는 수집하지 않아요.</p></div>
        <div className="card col" style={{ gap: 4 }}><h3>AI와 데이터</h3><p className="cap">AI 추천에는 시간표 원본이 쓰이지 않고 관심사 텍스트만 쓰여요. 공강 계산은 서버 안에서만 해요.</p></div>
        <button className="btn secondary block" onClick={() => nav('/profile')}>프로필 공개 항목 바꾸기</button>
      </div>
    </div>
  );
}

export function Blocked() {
  const { s, a } = useApp();
  return (
    <div className="page narrow">
      <TopBar title="차단 목록" />
      {s.blocks.length === 0 ? <Empty title="차단한 사용자가 없어요" text="차단하면 서로 추천에서 제외돼요." /> : (
        <div className="list card" style={{ padding: '0 16px' }}>
          {s.blocks.map((id) => { const u = userById(s, id) ?? USERS.find((x) => x.id === id); return (
            <div key={id} className="listrow"><Avatar user={u} /><span className="grow"><b>{u?.nick ?? id}</b></span><button className="btn secondary s" onClick={() => a.unblock(id)}>차단 해제</button></div>
          ); })}
        </div>
      )}
    </div>
  );
}

export function Sanction() {
  const { s, a } = useApp();
  const stages = [
    { n: 1 as const, t: '경고', d: '운영자가 확인한 위반 행동에 대해 먼저 안내해요.' },
    { n: 2 as const, t: '7일 이용 정지', d: '7일 동안 모임 합류와 채팅이 제한돼요.' },
    { n: 3 as const, t: '영구 정지', d: '반복되거나 심각한 위반일 때 서비스 이용이 영구히 제한돼요.' },
  ];
  return (
    <div className="page narrow">
      <TopBar title="이용 제한 안내" />
      <div className="col" style={{ gap: 14 }}>
        {s.sanction > 0 && <div className="notice danger"><Icon n="info" s={18} /><span><b>{stages[s.sanction - 1].t}</b> 조치가 적용 중이에요. 이의가 있으면 문의해 주세요.</span></div>}
        <p className="cap">행동을 기준으로 제재해요. 허위 신고나 악의적 악용이 확인된 경우에만 신고자도 제재해요.</p>
        {stages.map((st) => (
          <div key={st.n} className="card flat col" style={{ gap: 4, borderColor: s.sanction === st.n ? 'var(--danger)' : undefined }}>
            <div className="row between"><h3>{st.n}단계 · {st.t}</h3>{s.sanction === st.n && <span className="pill cancelled">적용 중</span>}</div><p className="cap">{st.d}</p></div>
        ))}
        <div className="card flat col"><div className="row between"><h3>시연: 적용 상태 바꾸기</h3><span className="pill mine">시연용</span></div>
          <div className="chips">{[0, 1, 2, 3].map((n) => <Chip key={n} on={s.sanction === n} onClick={() => a.setSanction(n as 0 | 1 | 2 | 3)}>{n === 0 ? '없음' : stages[n - 1].t}</Chip>)}</div></div>
        <button className="btn secondary block" disabled>이의 문의 · 출시 전 운영 정책으로 확정</button>
      </div>
    </div>
  );
}
