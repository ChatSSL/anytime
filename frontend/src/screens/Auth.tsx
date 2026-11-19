import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useApp } from '../store';
import { SCHOOLS } from '../data';
import { Icon, TopBar } from '../components/ui';

const TERMS = [
  { k: 't1', label: '[필수] 이용약관 동의', req: true },
  { k: 't2', label: '[필수] 개인정보 수집·이용 동의', req: true },
  { k: 't3', label: '[선택] 모임 알림 수신 동의', req: false },
];

export function AuthEmail() {
  const nav = useNavigate(); const { s } = useApp();
  const [school, setSchool] = useState(s.school);
  const [local, setLocal] = useState('');
  const [agree, setAgree] = useState<Record<string, boolean>>({});
  const domain = SCHOOLS.find((x) => x.name === school)!.domain;
  const bad = local.length > 0 && !/^[a-zA-Z0-9._-]+$/.test(local);
  const all = TERMS.every((t) => agree[t.k]);
  const ok = local.length > 1 && !bad && TERMS.filter((t) => t.req).every((t) => agree[t.k]);
  return (
    <div className="flowpage">
      <TopBar onBack={() => nav('/welcome')} />
      <div className="body">
        <div className="col" style={{ gap: 6 }}>
          <h1>학교 이메일로 인증해요</h1>
          <p className="muted">인증 정보는 다른 학생에게 공개되지 않아요.</p>
        </div>
        <div className="field"><label htmlFor="school">학교</label>
          <select id="school" className="input" value={school} onChange={(e) => setSchool(e.target.value)}>{SCHOOLS.map((x) => <option key={x.name}>{x.name}</option>)}</select></div>
        <div className="field"><label htmlFor="mail">학교 이메일</label>
          <div className="suffix"><input id="mail" inputMode="email" autoComplete="username" placeholder="아이디" value={local} onChange={(e) => setLocal(e.target.value.trim())} aria-invalid={bad} /><span>@{domain}</span></div>
          {bad ? <span className="help err">영문, 숫자, 점, 밑줄, 하이픈만 쓸 수 있어요.</span> : <span className="help">학교 메일로 인증번호를 보내드려요.</span>}</div>
        <div className="col" style={{ gap: 10 }}>
          <label className="check"><input type="checkbox" checked={all} onChange={(e) => setAgree(Object.fromEntries(TERMS.map((t) => [t.k, e.target.checked])))} /><b>전체 동의</b></label>
          <div className="divider" />
          {TERMS.map((t) => <label key={t.k} className="check"><input type="checkbox" checked={!!agree[t.k]} onChange={(e) => setAgree({ ...agree, [t.k]: e.target.checked })} />{t.label}</label>)}
        </div>
      </div>
      <div className="cta"><button className="btn block" disabled={!ok} onClick={() => nav('/auth/code', { state: { email: `${local}@${domain}`, school } })}>인증번호 받기</button></div>
    </div>
  );
}

export function AuthCode() {
  const nav = useNavigate(); const loc = useLocation(); const { a, toast } = useApp();
  const st = (loc.state ?? {}) as { email?: string; school?: string };
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [err, setErr] = useState(false); const [left, setLeft] = useState(300);
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  useEffect(() => { const t = setInterval(() => setLeft((v) => Math.max(0, v - 1)), 1000); return () => clearInterval(t); }, []);
  useEffect(() => { if (!st.email) nav('/auth', { replace: true }); else refs.current[0]?.focus(); }, []);
  const code = digits.join('');
  const set = (i: number, v: string) => {
    const d = v.replace(/\D/g, '').slice(-1); const n = [...digits]; n[i] = d; setDigits(n); setErr(false);
    if (d && i < 5) refs.current[i + 1]?.focus();
  };
  const submit = () => {
    if (code === '000000' || left === 0) { setErr(true); return; } // 000000은 오류 상태 확인용 시연 값
    a.verify(st.email!, st.school ?? ''); nav('/auth/done');
  };
  const mm = `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
  return (
    <div className="flowpage">
      <TopBar />
      <div className="body">
        <div className="col" style={{ gap: 6 }}><h1>인증번호를 입력해요</h1><p className="muted">{st.email}으로 보낸 6자리 숫자예요.</p></div>
        <div className="otp" role="group" aria-label="인증번호 6자리">
          {digits.map((d, i) => (
            <input key={i} ref={(el) => { refs.current[i] = el; }} inputMode="numeric" autoComplete="one-time-code" aria-label={`${i + 1}번째 숫자`} value={d}
              onChange={(e) => set(i, e.target.value)} onKeyDown={(e) => { if (e.key === 'Backspace' && !d && i > 0) refs.current[i - 1]?.focus(); }}
              style={err ? { boxShadow: 'inset 0 0 0 2px var(--danger)' } : undefined} />
          ))}
        </div>
        {err ? <span className="help err">인증번호가 맞지 않아요. 다시 확인해 주세요.</span> : <span className="help">시연용: 아무 6자리나 통과해요. 000000은 오류 화면을 보여줘요.</span>}
        <div className="row between"><span className="num" style={{ color: left < 30 ? 'var(--danger)' : 'var(--ink)', fontWeight: 700 }}>{mm}</span>
          <button className="btn ghost s" onClick={() => { setLeft(300); setDigits(['', '', '', '', '', '']); toast('인증번호를 다시 보냈어요', 'info'); }}>인증번호 다시 받기</button></div>
      </div>
      <div className="cta"><button className="btn block" disabled={code.length < 6} onClick={submit}>확인</button></div>
    </div>
  );
}

export function AuthDone() {
  const nav = useNavigate();
  return (
    <div className="flowpage">
      <div className="body" style={{ justifyContent: 'center', alignItems: 'center', textAlign: 'center', gap: 20 }}>
        <div className="bigcheck"><Icon n="check" s={48} /></div>
        <h1>인증이 완료됐어요</h1>
        <p className="muted">이제 나를 소개하고 시간표를 넣어볼까요?</p>
      </div>
      <div className="cta"><button className="btn block" onClick={() => nav('/profile', { replace: true })}>다음: 프로필 만들기</button></div>
    </div>
  );
}
