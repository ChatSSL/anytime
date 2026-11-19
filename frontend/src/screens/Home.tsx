import { useNavigate, Link } from 'react-router-dom';
import { useApp } from '../store';
import { DAYS } from '../types';
import { abs, dur, freeSlots, hm } from '../lib/time';
import { inClass, nextFree, nowMin, recommendPeople, recommendRooms, roomFit, unratedRooms, wantedSlots } from '../lib/derive';
import { Avatar, Empty, Icon, PersonCard, RoomCard } from '../components/ui';
import { meUser } from '../lib/derive';

export function Home() {
  const { s } = useApp(); const nav = useNavigate();
  const free = nextFree(s); const cls = inClass(s);
  const rooms = recommendRooms(s); const people = recommendPeople(s).slice(0, 3);
  const unrated = unratedRooms(s); const unread = s.notifs.filter((n) => !n.read).length;
  const now = nowMin(s);
  const me = meUser(s);

  let hero: { grey: boolean; over: string; big: string; sub: string; count: number } | null = null;
  if (free) {
    const slotRooms = rooms.filter((r) => { const f = roomFit(r.room, s); return f.slot && f.slot.day === free.day && f.slot.start < free.end && free.start < f.slot.end; }).length;
    const startAbs = abs(free.day, free.start);
    const label = `${free.day === s.clock.day ? '오늘' : DAYS[free.day] + '요일'} ${hm(free.start)}–${hm(free.end)}`;
    if (cls) hero = { grey: true, over: '지금은 수업 중이에요', big: `다음 공강은 ${hm(free.start)}`, sub: `${label} (${dur(startAbs - now)} 뒤)`, count: slotRooms };
    else if (startAbs <= now) hero = { grey: false, over: '지금 공강이에요', big: `${hm(free.end)}까지 ${dur(abs(free.day, free.end) - now)}`, sub: label, count: slotRooms };
    else hero = { grey: false, over: '다음 공강', big: label, sub: `${dur(startAbs - now)} 뒤`, count: slotRooms };
  }

  const heroEl = hero && (
    <section className={`hero ${hero.grey ? 'grey' : ''}`} aria-label="다음 공강">
      <span className="over" style={{ color: 'inherit', opacity: .8 }}>{hero.over}</span>
      <div className="big">{hero.big}</div>
      <p className="num">{hero.sub}</p>
      <p>{hero.count > 0 ? `이 시간에 열린 모임 ${hero.count}개` : '이 시간에 열린 모임이 아직 없어요'}</p>
      <button className="btn m" style={hero.grey ? { background: 'var(--ink)', color: 'var(--bg)' } : undefined} onClick={() => nav(hero!.count > 0 ? '/rooms' : '/rooms/new')}>{hero.count > 0 ? '모임 보기' : '모임 열기'}</button>
    </section>
  );

  return (
    <div className="page">
      <div className="topbar">
        <div className="logo"><b>A</b>nytime</div>
        <Link to="/notifications" className="iconbtn" aria-label={`알림 ${unread}개`}><Icon n="bell" />{unread > 0 && <i className="dot" style={{ top: 10, left: 26, margin: 0 }} />}</Link>
        <Link to="/me" aria-label="마이페이지"><Avatar user={me} size="sm" /></Link>
      </div>

      {unrated.length > 0 && (
        <Link to={`/review/${unrated[0].id}`} className="notice" style={{ margin: '4px 0 12px', color: 'var(--ink)' }}><Icon n="star" s={18} /><span><b>{unrated[0].title}</b> 어땠나요? 평가를 남겨 주세요.</span></Link>
      )}

      <div className="sec2">
        <div className="col" style={{ gap: 0 }}>
          {heroEl ?? <div className="hero grey"><p>등록된 공강이 없어요</p><button className="btn m" onClick={() => nav('/me/timetable')}>시간표 고치기</button></div>}
          <div className="sectionhead"><h2>지금 합류할 수 있는 모임</h2><Link to="/rooms" className="cap">전체 보기</Link></div>
          {rooms.length > 0 ? (
            <div className="hscroll">{rooms.slice(0, 4).map((r) => <RoomCard key={r.room.id} room={r.room} />)}</div>
          ) : (
            <div className="card"><Empty title="아직 공강이 맞는 모임이 없어요" text="직접 열면 공강이 겹치는 사람들에게 알려드려요." action={<button className="btn m" onClick={() => nav('/rooms/new')}>모임 만들기</button>} /></div>
          )}
          {s.settings.ads && !s.settings.premium && (
            <div className="ad" style={{ marginTop: 16 }}><b>광고</b><span>추천 순위에는 영향이 없어요. 모임과 추천 화면을 가리지 않는 자리에만 보여요.</span></div>
          )}
        </div>

        <div className="col" style={{ gap: 0 }}>
          <div className="sectionhead" style={{ marginTop: 20 }}><h2>나와 공강이 맞는 사람</h2></div>
          <div className="col">
            {people.length ? people.map((p) => <PersonCard key={p.user.id} reco={p} />)
              : <div className="card"><Empty title="겹치는 공강이 있는 사람이 아직 없어요" text="'이 시간에 만나고 싶어요'를 켜면 더 많이 보여요." action={<button className="btn m secondary" onClick={() => nav('/me/timetable')}>공강 설정</button>} /></div>}
          </div>
          <div className="sectionhead"><h2>이번 주 내 공강</h2><Link to="/me/timetable" className="cap">고치기</Link></div>
          <div className="card tl" aria-label="이번 주 내 공강 타임라인">
            {DAYS.map((d, i) => (
              <div key={d} style={{ display: 'contents' }}>
                <span className="num cap" style={{ fontWeight: 700 }}>{d}</span>
                <div className="track">{freeSlots(s.classes).filter((f) => f.day === i).map((f) => (
                  <i key={f.start} title={`${hm(f.start)}–${hm(f.end)}`} style={{ left: `${((f.start - 540) / 720) * 100}%`, width: `${((f.end - f.start) / 720) * 100}%`, opacity: wantedSlots(s).some((w) => w.day === i && w.start === f.start) ? 1 : .35 }} />
                ))}</div>
              </div>
            ))}
          </div>
          <p className="cap" style={{ marginTop: 6 }}>09:00–21:00 기준, 라임색이 공강이에요.</p>
        </div>
      </div>
    </div>
  );
}
