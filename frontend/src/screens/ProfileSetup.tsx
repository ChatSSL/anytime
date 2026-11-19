import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../store';
import { ACTIVITIES, AVATAR_COLORS, USERS } from '../data';
import { Avatar, Chip, Icon, Switch, TopBar } from '../components/ui';

export function ProfileSetup() {
  const nav = useNavigate(); const { a, s } = useApp();
  const p = s.profile;
  const [step, setStep] = useState(1);
  const [nick, setNick] = useState(p?.nick ?? ''); const [checked, setChecked] = useState(false);
  const [avatar, setAvatar] = useState(p?.avatar ?? 0);
  const [dept, setDept] = useState(p?.dept ?? ''); const [showDept, setShowDept] = useState(p?.showDept ?? true);
  const [picked, setPicked] = useState<string[]>(p?.interests ?? []);
  const [custom, setCustom] = useState(''); const [customOpen, setCustomOpen] = useState(false);
  const taken = USERS.some((u) => u.nick === nick.trim());
  const lenOk = nick.trim().length >= 2 && nick.trim().length <= 10;
  const toggle = (x: string) => setPicked((v) => (v.includes(x) ? v.filter((y) => y !== x) : v.length < 8 ? [...v, x] : v));
  const addCustom = () => { const t = custom.trim(); if (t && picked.length < 8 && !picked.includes(t)) setPicked([...picked, t]); setCustom(''); };
  const finish = () => {
    a.setProfile({ nick: nick.trim(), dept: dept.trim(), showDept: showDept && !!dept.trim(), avatar, interests: picked });
    nav(s.classes.length ? '/home' : '/timetable/setup', { replace: true });
  };
  return (
    <div className="flowpage">
      <TopBar onBack={() => (step === 1 ? nav('/auth/done') : setStep(1))} right={<span className="cap num">{step}/2</span>} />
      {step === 1 ? (
        <>
          <div className="body">
            <h1>나를 소개해요</h1>
            <div className="field"><label htmlFor="nick">닉네임</label>
              <div className="row">
                <input id="nick" className={`input ${checked && lenOk && !taken ? 'ok' : checked && taken ? 'err' : ''}`} maxLength={10} placeholder="2~10자" value={nick} onChange={(e) => { setNick(e.target.value); setChecked(false); }} />
                <button className="btn secondary m" disabled={!lenOk} onClick={() => setChecked(true)} style={{ flex: 'none' }}>중복 확인</button>
              </div>
              {checked && (taken ? <span className="help err">이미 쓰고 있는 닉네임이에요.</span> : <span className="help ok">쓸 수 있는 닉네임이에요.</span>)}
            </div>
            <div className="field"><span className="label">아바타</span>
              <div className="row wrap" role="radiogroup" aria-label="아바타 색">
                {AVATAR_COLORS.map((c, i) => (
                  <button key={c} role="radio" aria-checked={avatar === i} aria-label={`색상 ${i + 1}`} onClick={() => setAvatar(i)} style={{ border: 0, background: 'none', padding: 3, borderRadius: 99, boxShadow: avatar === i ? '0 0 0 2px var(--primary)' : 'none' }}>
                    <Avatar user={{ nick: nick || '나', color: c }} />
                  </button>
                ))}
              </div>
            </div>
            <div className="field"><label htmlFor="dept">학과 (선택)</label>
              <input id="dept" className="input" placeholder="예: 컴퓨터공학과" value={dept} onChange={(e) => setDept(e.target.value)} />
              <div className="row between"><span className="help">다른 학생에게 학과를 보여줄까요?</span><Switch on={showDept} onChange={setShowDept} label="학과 공개" /></div></div>
            <div className="notice"><Icon n="lock" s={18} /><span>실명과 학번은 공개되지 않아요. 다른 학생에게는 닉네임, 학과(선택), 관심 활동만 보여요.</span></div>
          </div>
          <div className="cta"><button className="btn block" disabled={!lenOk || taken} onClick={() => setStep(2)}>다음</button></div>
        </>
      ) : (
        <>
          <div className="body">
            <h1>하고 싶은 활동을 골라요</h1>
            <div className="chips">
              {[...ACTIVITIES, ...picked.filter((x) => !ACTIVITIES.includes(x))].map((x) => <Chip key={x} on={picked.includes(x)} onClick={() => toggle(x)}>{x}</Chip>)}
              <Chip onClick={() => setCustomOpen((v) => !v)} on={customOpen}><Icon n="plus" s={14} />직접 입력</Chip>
            </div>
            {customOpen && (
              <div className="field">
                <div className="row"><input className="input" aria-label="직접 입력" placeholder="예: 카탄 같은 보드게임" value={custom} maxLength={20} onChange={(e) => setCustom(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && addCustom()} />
                  <button className="btn m" onClick={addCustom} style={{ flex: 'none' }}>추가</button></div>
                <span className="help">문장으로 적어도 AI가 비슷한 취향을 알아봐요. "보드게임"과 "카탄"처럼요.</span>
              </div>
            )}
            <p className="cap num" aria-live="polite">{picked.length}/8 선택</p>
          </div>
          <div className="cta"><button className="btn block" disabled={picked.length === 0} onClick={finish}>다음: 시간표 입력</button></div>
        </>
      )}
    </div>
  );
}
