import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../store';

const SLIDES = [
  { t: '수업 사이 빈 시간, 혼자 보내지 마세요', d: '같은 학교 학생 중 공강이 겹치는 사람을 찾아드려요.' },
  { t: '시간표만 넣으면 끝', d: '공강이 맞는 사람과 열려 있는 모임을 AI가 골라줘요. 조율 대화는 필요 없어요.' },
  { t: '한 번만 만나도 괜찮아요', d: '가입도 정기 모임도 없어요. 시간표는 비공개, 겹치는 공강만 보여요.' },
];

function Illustration({ i }: { i: number }) {
  // 시간표 블록과 라임색 빈 칸으로만 구성한 단순한 도형 일러스트
  const blocks = [
    [[24, 24, 90, 34], [24, 100, 120, 34], [150, 60, 90, 34]],
    [[24, 24, 70, 34], [110, 24, 70, 34], [24, 110, 70, 34], [110, 110, 70, 34]],
    [[24, 30, 100, 34], [24, 120, 100, 34], [150, 30, 80, 34]],
  ][i];
  const free = [[24, 62, 120, 34], [196, 62, 44, 34], [190, 110, 50, 34]][i] ?? [24, 62, 120, 34];
  return (
    <div className="illu" aria-hidden="true">
      {blocks.map((b, k) => <i key={k} className="b" style={{ left: b[0], top: b[1], width: b[2], height: b[3] }} />)}
      <i className="f" style={{ left: free[0], top: free[1], width: free[2], height: free[3] }} />
    </div>
  );
}

export function Welcome() {
  const nav = useNavigate(); const { a, s } = useApp();
  const [splash, setSplash] = useState(true); const [i, setI] = useState(0);
  useEffect(() => { const t = setTimeout(() => setSplash(false), 1100); return () => clearTimeout(t); }, []);
  if (splash) {
    return (
      <div className="splash" role="img" aria-label="Anytime">
        <div className="col center" style={{ alignItems: 'center', gap: 14 }}>
          <svg width="84" height="84" viewBox="0 0 84 84" aria-hidden="true">
            <circle cx="42" cy="42" r="36" fill="none" stroke="#fff" strokeWidth="6" />
            <path d="M42 22v20l12 8" stroke="#fff" strokeWidth="6" strokeLinecap="round" fill="none" />
            <rect x="52" y="52" width="22" height="14" rx="4" fill="#C8F169" />
          </svg>
          <div style={{ font: '700 36px var(--num)', letterSpacing: '-.02em' }}>Anytime</div>
        </div>
      </div>
    );
  }
  const last = i === SLIDES.length - 1;
  const go = () => { a.finishOnboarding(); nav(s.verified ? '/' : '/auth'); };
  return (
    <div className="flowpage">
      <div className="row" style={{ justifyContent: 'flex-end', minHeight: 56 }}>
        <button className="btn ghost s" onClick={go}>건너뛰기</button>
      </div>
      <div className="body" style={{ justifyContent: 'center', gap: 24 }}>
        <Illustration i={i} />
        <div className="col" style={{ gap: 8 }}>
          <h1 style={{ textWrap: 'balance' as never }}>{SLIDES[i].t}</h1>
          <p className="muted">{SLIDES[i].d}</p>
        </div>
        <div className="dots" aria-label={`${i + 1} / ${SLIDES.length}`}>{SLIDES.map((_, k) => <i key={k} className={k === i ? 'on' : ''} />)}</div>
      </div>
      <div className="cta"><button className="btn block" onClick={() => (last ? go() : setI(i + 1))}>{last ? '학교 이메일로 시작하기' : '다음'}</button></div>
    </div>
  );
}
