import { useState } from 'react';
import { useApp } from '../store';
import { Empty, Icon, Skeleton, Switch, TopBar } from '../components/ui';

/** 확장 단계 화면: 광고 배너 자리, 프리미엄, 제휴 쿠폰 */
export function Premium() {
  const { s, a } = useApp();
  return (
    <div className="page narrow">
      <TopBar title="프리미엄 · 광고" right={<span className="pill soon">확장 예정</span>} />
      <div className="col" style={{ gap: 16 }}>
        <div className="card col">
          <h2>프리미엄</h2>
          <ul style={{ margin: 0, paddingLeft: 18 }}><li>광고 비노출</li><li>내 모임방 상단 노출</li><li>동시에 열 수 있는 모임 수 확대</li><li>장소·인원 규모 세부 필터</li><li>AI 미션 추가</li></ul>
          <p className="num" style={{ fontSize: 22, fontWeight: 700 }}>00원<span className="cap"> / 월</span></p>
          <button className="btn block" onClick={() => a.setSetting('premium', !s.settings.premium)}>{s.settings.premium ? '프리미엄 끄기 (시연)' : '프리미엄 미리보기 (시연)'}</button>
        </div>
        <div className="notice lime col" style={{ gap: 4 }}><b>항상 무료</b><span style={{ fontWeight: 400 }}>공강 매칭과 추천, 추천받는 사람·모임 수, 프로필 열람, 모임 합류</span></div>
        <div className="card col"><h3>광고는 이렇게 보여요</h3>
          <div className="ad"><b>광고</b><span>모임과 추천 화면을 가리지 않는 자리에만 보여요. 추천 순위에는 영향이 없어요.</span></div>
          <div className="row between"><span>홈에 광고 배너 보기</span><Switch on={s.settings.ads} onChange={(v) => a.setSetting('ads', v)} label="광고 배너 표시" /></div></div>
        <div className="col"><h3>제휴 상점 쿠폰 (사용자 규모 확보 후)</h3>
          <div className="coupon"><b style={{ fontSize: 18 }}>근처 카페 10% 할인</b><span className="num">오늘 12:00–15:00 · 정문 앞 100m</span><span className="num" style={{ fontWeight: 700, letterSpacing: '.1em' }}>ANYTIME-0000</span></div></div>
      </div>
    </div>
  );
}

/** 빈 화면, 로딩, 오류, 폼 검증 상태를 한곳에서 확인하는 모음 */
export function States() {
  const { toast } = useApp();
  const [offline, setOffline] = useState(false);
  const errs: [string, string][] = [['네트워크가 끊겼어요', '와이파이나 데이터 연결을 확인하고 다시 시도해 주세요.'], ['서버에 문제가 생겼어요', '잠시 뒤 다시 열어 주세요. 계속되면 문의해 주세요.'], ['볼 수 없는 화면이에요', '이 화면을 볼 권한이 없어요. 다른 계정으로 로그인했는지 확인해 주세요.'], ['학교 인증이 만료됐어요', '학교 이메일로 인증을 다시 해주세요.']];
  return (
    <div className="page">
      <TopBar title="상태 화면 모음" />
      {offline && <div className="notice warn" style={{ marginBottom: 12 }}><Icon n="info" s={18} />오프라인이에요. 연결되면 자동으로 다시 불러와요.</div>}
      <div className="col" style={{ gap: 24 }}>
        <section className="col"><h2>로딩</h2><div className="grid2"><div className="card col"><Skeleton h={90} /><Skeleton h={18} w="60%" /></div><div className="card col"><Skeleton h={18} /><Skeleton h={14} w="80%" /><Skeleton h={14} w="50%" /></div><div className="card row"><Skeleton h={44} w={44} /><div className="grow col" style={{ gap: 6 }}><Skeleton h={14} /><Skeleton h={14} w="70%" /></div></div></div></section>
        <section className="col"><h2>빈 상태</h2><div className="grid2">
          <div className="card"><Empty title="아직 추천이 없어요" text="시간표를 더 채우면 추천이 늘어요." /></div>
          <div className="card"><Empty title="열린 모임이 없어요" text="운영팀이 연 모임을 먼저 둘러보세요." action={<span className="pill mine">운영팀이 연 모임</span>} /></div>
          <div className="card"><Empty title="새 알림이 없어요" /></div><div className="card"><Empty title="차단한 사용자가 없어요" /></div><div className="card"><Empty title="아직 채팅이 없어요" /></div></div></section>
        <section className="col"><h2>오류</h2><div className="grid2">{errs.map(([t, d]) => <div key={t} className="card col" style={{ gap: 6 }}><h3 style={{ color: 'var(--danger)' }}>{t}</h3><p className="cap">{d}</p><button className="btn secondary s" style={{ alignSelf: 'flex-start' }}>다시 시도</button></div>)}</div></section>
        <section className="col"><h2>폼 검증</h2><div className="grid2">
          <div className="field"><label htmlFor="v1">이메일</label><input id="v1" className="input err" defaultValue="hello@" /><span className="help err">아이디는 영문, 숫자, 점, 밑줄, 하이픈만 쓸 수 있어요.</span></div>
          <div className="field"><label htmlFor="v2">닉네임</label><input id="v2" className="input err" defaultValue="민트초코" /><span className="help err">이미 쓰고 있는 닉네임이에요.</span></div>
          <div className="field"><label htmlFor="v3">수업 시간</label><input id="v3" className="input err" defaultValue="수 10:30–12:00" /><span className="help err">데이터베이스 수업과 겹쳐요. 시간을 바꿔주세요.</span></div></div></section>
        <section className="col"><h2>피드백</h2><div className="row wrap">
          <button className="btn secondary m" onClick={() => toast('저장했어요', 'ok')}>성공 토스트</button><button className="btn secondary m" onClick={() => toast('저장하지 못했어요. 연결을 확인해 주세요', 'err')}>오류 토스트</button>
          <button className="btn secondary m" onClick={() => setOffline(!offline)}>오프라인 배너 {offline ? '끄기' : '켜기'}</button></div></section>
      </div>
    </div>
  );
}
