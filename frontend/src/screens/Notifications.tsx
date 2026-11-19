import { useNavigate } from 'react-router-dom';
import { useApp } from '../store';
import { NotifType } from '../types';
import { Empty, Icon, Switch, TopBar } from '../components/ui';

const ICON: Record<NotifType, [string, string]> = {
  reco: ['spark', 'var(--primary)'], join: ['check', 'var(--success)'], request: ['users', 'var(--primary)'],
  reminder: ['clock', '#B7791A'], change: ['info', '#B7791A'], cancel: ['close', 'var(--danger)'], rate: ['star', 'var(--primary)'],
};

export function Notifications() {
  const { s, a } = useApp(); const nav = useNavigate();
  const today = s.notifs.filter((n) => n.time.includes(':')), before = s.notifs.filter((n) => !n.time.includes(':'));
  const open = (id: string, link?: string) => { a.readNotif(id); if (link) nav(link); };
  const group = (title: string, list: typeof s.notifs) => list.length > 0 && (
    <div className="col" style={{ gap: 6 }}><span className="over">{title}</span>
      <div className="list card" style={{ padding: '0 16px' }}>
        {list.map((n) => (
          <div key={n.id} className="listrow" style={{ alignItems: 'flex-start', background: n.read ? undefined : 'var(--primary-soft)', margin: '0 -16px', padding: '14px 16px' }}>
            <span style={{ color: ICON[n.type][1], marginTop: 2 }}><Icon n={ICON[n.type][0]} /></span>
            <div className="grow col" style={{ gap: 4 }}>
              <button onClick={() => open(n.id, n.link)} style={{ all: 'unset', cursor: n.link ? 'pointer' : 'default' }}>
                <div className="row between"><b>{n.title}</b><span className="cap num">{n.time}</span></div><p className="cap" style={{ color: 'var(--ink)' }}>{n.body}</p></button>
              {n.type === 'change' && n.needsAction && <span className="pill soon" style={{ alignSelf: 'flex-start' }}>답변 필요</span>}
              {n.type === 'request' && n.needsAction && (
                <div className="row"><button className="btn s" onClick={() => a.respondRequest(n.id, true)}>수락</button><button className="btn secondary s" onClick={() => a.respondRequest(n.id, false)}>거절</button></div>)}
              {n.resolved && <span className="cap">{n.resolved === 'accepted' ? '수락했어요' : '거절했어요'}</span>}
            </div>
            {!n.read && <i style={{ width: 8, height: 8, borderRadius: 9, background: 'var(--primary)', marginTop: 8 }} aria-label="읽지 않음" />}
          </div>
        ))}
      </div></div>
  );
  return (
    <div className="page narrow">
      <TopBar title="알림" right={<><button className="btn ghost s" onClick={a.readAllNotifs}>모두 읽음</button><button className="iconbtn" aria-label="알림 설정" onClick={() => nav('/notifications/settings')}><Icon n="filter" /></button></>} />
      {s.notifs.length === 0 ? <Empty title="새 알림이 없어요" text="추천이나 모임 소식이 오면 여기에 보여요." /> : <div className="col" style={{ gap: 16 }}>{group('오늘', today)}{group('이전', before)}</div>}
    </div>
  );
}

export function NotifSettings() {
  const { s, a } = useApp();
  const rows: [string, string][] = [['reco', '추천'], ['reminder', '리마인드'], ['change', '일정 변경'], ['chat', '채팅'], ['rate', '평가 요청']];
  return (
    <div className="page narrow">
      <TopBar title="알림 설정" />
      <div className="list card" style={{ padding: '0 16px' }}>
        {rows.map(([k, label]) => <div key={k} className="listrow"><span className="grow">{label}</span><Switch on={s.settings.notif[k] !== false} onChange={(v) => a.setNotifSetting(k, v)} label={`${label} 알림`} /></div>)}
        <div className="listrow"><div className="grow"><div>방해 금지</div><div className="cap">수업 시간에는 자동으로 알림을 꺼요</div></div><Switch on={s.settings.quiet} onChange={(v) => a.setSetting('quiet', v)} label="수업 시간 자동 음소거" /></div>
      </div>
    </div>
  );
}
