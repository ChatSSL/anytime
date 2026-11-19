import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../store';
import { ClassBlock, DAYS, Day } from '../types';
import { EXAMPLE_TIMETABLE } from '../data';
import { classConflict, dur, freeSlots, hm, parseClasses, slotKey, slotLabel } from '../lib/time';
import { myRooms } from '../lib/derive';
import { Chip, Icon, Sheet, Switch, Timetable, TopBar } from '../components/ui';

const TIMES = Array.from({ length: 25 }, (_, i) => 540 + i * 30);

function ClassSheet({ open, onClose, init, existing }: { open: boolean; onClose: () => void; init: { day: Day; start: number } | ClassBlock | null; existing?: ClassBlock }) {
  const { a, s } = useApp();
  const [name, setName] = useState(''); const [day, setDay] = useState<Day>(0); const [start, setStart] = useState(540); const [end, setEnd] = useState(630);
  const [key, setKey] = useState('');
  const k = init ? `${'id' in init ? init.id : 'n'}-${init.day}-${init.start}` : '';
  if (open && k !== key) { // 열릴 때마다 초기값을 채운다
    setKey(k);
    if (init) { setDay(init.day); setStart(init.start); setEnd('end' in init ? init.end : Math.min(init.start + 90, 1260)); setName('name' in init ? init.name : ''); }
  }
  const conflict = classConflict(s.classes.filter((c) => c.id !== existing?.id), day, start, end);
  const bad = end <= start;
  const save = () => {
    if (existing) a.removeClass(existing.id);
    a.addClass({ name: name.trim() || '수업', day, start, end }); onClose();
  };
  return (
    <Sheet open={open} onClose={onClose} title={existing ? '수업 수정' : '수업 추가'}>
      <div className="field"><label htmlFor="cn">과목명 (선택)</label><input id="cn" className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="예: 자료구조" /></div>
      <div className="field"><span className="label">요일</span><div className="chips" role="radiogroup" aria-label="요일">{DAYS.map((d, i) => <Chip key={d} on={day === i} onClick={() => setDay(i as Day)}>{d}</Chip>)}</div></div>
      <div className="row">
        <div className="field grow"><label htmlFor="cs">시작</label><select id="cs" className="input" value={start} onChange={(e) => setStart(+e.target.value)}>{TIMES.slice(0, -1).map((t) => <option key={t} value={t}>{hm(t)}</option>)}</select></div>
        <div className="field grow"><label htmlFor="ce">종료</label><select id="ce" className="input" value={end} onChange={(e) => setEnd(+e.target.value)}>{TIMES.slice(1).map((t) => <option key={t} value={t}>{hm(t)}</option>)}</select></div>
      </div>
      {bad && <span className="help err">종료 시간이 시작보다 늦어야 해요.</span>}
      {conflict && !bad && <span className="help err">{conflict.name} 수업과 겹쳐요. 시간을 바꿔주세요.</span>}
      <div className="row">
        {existing && <button className="btn danger m" onClick={() => { a.removeClass(existing.id); onClose(); }}><Icon n="trash" s={18} />삭제</button>}
        <button className="btn grow" disabled={bad || !!conflict} onClick={save}>{existing ? '저장' : '추가'}</button>
      </div>
    </Sheet>
  );
}

function FreeList() {
  const { s, a } = useApp();
  const free = freeSlots(s.classes);
  const total = free.reduce((x, f) => x + f.end - f.start, 0);
  return (
    <div className="col">
      <div className="card" style={{ background: 'var(--free)', color: 'var(--free-ink)' }}>
        <span className="over" style={{ color: 'inherit' }}>이번 주 공강</span>
        <div className="num" style={{ fontSize: 28, fontWeight: 700 }}>총 {dur(total) || '0분'}</div>
      </div>
      {free.length === 0 && <p className="cap">60분 이상 비는 시간이 아직 없어요. 수업을 더 추가하거나 시간을 확인해 보세요.</p>}
      <div className="list card" style={{ padding: '0 16px' }}>
        {free.map((f) => (
          <div key={slotKey(f)} className="listrow">
            <div className="grow"><div className="num" style={{ fontWeight: 700 }}>{slotLabel(f)}</div><div className="cap">공강 {dur(f.end - f.start)} · 이 시간에 만나고 싶어요</div></div>
            <Switch on={s.wanted[slotKey(f)] !== false} onChange={(v) => a.toggleWanted(slotKey(f), v)} label={`${slotLabel(f)} 만나고 싶어요`} />
          </div>
        ))}
      </div>
    </div>
  );
}

function Editor() {
  const { s } = useApp();
  const [open, setOpen] = useState(false); const [init, setInit] = useState<{ day: Day; start: number } | ClassBlock | null>(null); const [ex, setEx] = useState<ClassBlock | undefined>();
  const events = myRooms(s).map((r) => ({ day: r.day, start: r.start, end: r.end, name: r.title }));
  return (
    <>
      <Timetable classes={s.classes} free={freeSlots(s.classes)} events={events}
        onCell={(d, h) => { setEx(undefined); setInit({ day: d as Day, start: h * 60 }); setOpen(true); }}
        onClass={(c) => { setEx(c); setInit(c); setOpen(true); }} />
      <p className="cap">빈 칸을 누르면 수업을 추가하고, 수업을 누르면 수정할 수 있어요.</p>
      <ClassSheet open={open} onClose={() => setOpen(false)} init={init} existing={ex} />
    </>
  );
}

export function TimetableSetup() {
  const nav = useNavigate(); const { s, a } = useApp();
  const [step, setStep] = useState<'method' | 'add' | 'check' | 'saved'>(s.classes.length ? 'check' : 'method');
  const [loading, setLoading] = useState(true);
  const goSaved = () => { setStep('saved'); setLoading(true); setTimeout(() => setLoading(false), 1400); };
  const back = () => (step === 'method' ? nav('/profile') : step === 'add' ? setStep('method') : step === 'check' ? setStep('add') : setStep('check'));
  return (
    <div className="flowpage" style={{ maxWidth: step === 'method' || step === 'saved' ? 480 : 640 }}>
      <TopBar onBack={back} title={step === 'add' ? '수업 추가' : step === 'check' ? '공강 확인' : ''} />
      {step === 'method' && (
        <>
          <div className="body">
            <div className="col" style={{ gap: 6 }}><h1>시간표를 넣어볼까요?</h1><p className="muted">시간표 원본은 나만 볼 수 있어요.</p></div>
            <button className="card flat col" style={{ textAlign: 'left', gap: 4, borderColor: 'var(--primary)' }} onClick={() => setStep('add')}>
              <div className="row between"><h3>직접 입력</h3><span className="chip sm">권장</span></div><p className="cap">빈 칸을 눌러 수업을 하나씩 추가해요.</p></button>
            <button className="card flat col" style={{ textAlign: 'left', gap: 4 }} onClick={() => { a.loadExample(parseClasses(EXAMPLE_TIMETABLE)); setStep('check'); }}>
              <h3>예시 시간표로 둘러보기</h3><p className="cap">시연용 시간표를 채워서 바로 공강 계산을 확인해요.</p></button>
            <div className="card flat col" style={{ gap: 4, opacity: 0.55 }}><div className="row between"><h3>이미지로 불러오기</h3><span className="chip sm plain">예정</span></div><p className="cap">시간표 캡처를 읽어 오는 기능은 다음 단계에서 만들어요.</p></div>
          </div>
        </>
      )}
      {step === 'add' && (
        <>
          <div className="body"><Editor /></div>
          <div className="cta"><button className="btn block" disabled={s.classes.length === 0} onClick={() => setStep('check')}>공강 확인하기 ({s.classes.length}개 수업)</button></div>
        </>
      )}
      {step === 'check' && (
        <>
          <div className="body"><Timetable classes={s.classes} free={freeSlots(s.classes)} /><FreeList /></div>
          <div className="cta"><button className="btn secondary" onClick={() => setStep('add')}>수업 고치기</button><button className="btn" onClick={goSaved}>저장</button></div>
        </>
      )}
      {step === 'saved' && (
        <>
          <div className="body" style={{ justifyContent: 'center', alignItems: 'center', textAlign: 'center', gap: 18 }}>
            {loading ? (<><div className="skel" style={{ width: 96, height: 96, borderRadius: 99 }} /><h2>공강이 맞는 사람과 모임을 찾는 중이에요</h2></>)
              : (<><div className="bigcheck"><Icon n="check" s={48} /></div><h2>저장됐어요</h2><p className="muted">이번 주 공강 기준으로 추천을 만들었어요.</p></>)}
          </div>
          <div className="cta"><button className="btn block" disabled={loading} onClick={() => nav('/home', { replace: true })}>추천 보러 가기</button></div>
        </>
      )}
    </div>
  );
}

export function TimetableEdit() {
  return (
    <div className="page narrow">
      <TopBar title="내 시간표 · 공강" />
      <div className="col" style={{ gap: 16 }}><Editor /><FreeList /></div>
    </div>
  );
}
