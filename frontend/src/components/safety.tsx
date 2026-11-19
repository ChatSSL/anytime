import { useState } from 'react';
import { useApp } from '../store';
import { User } from '../types';
import { Avatar, Icon, Modal, Sheet } from './ui';

export const REPORT_REASONS = [
  '원치 않는 연락 또는 만남 강요', '괴롭힘·스토킹', '차별·혐오 표현', '사기·불법 행위',
  '동의 없는 광고·스팸', '종교·정치·다단계 반복 권유', '기타',
];

/** 신고·차단 진입 시트. 프로필, 채팅, 모임방 어디서든 같은 시트를 쓴다. */
export function SafetyMenu({ open, onClose, target, roomId }: { open: boolean; onClose: () => void; target?: User; roomId?: string }) {
  const [view, setView] = useState<'menu' | 'report' | 'done' | 'block'>('menu');
  const [reason, setReason] = useState(''); const [detail, setDetail] = useState(''); const [file, setFile] = useState('');
  const { a, s } = useApp();
  const close = () => { onClose(); setTimeout(() => { setView('menu'); setReason(''); setDetail(''); setFile(''); }, 200); };
  if (!target) return null;
  const blocked = s.blocks.includes(target.id);
  return (
    <>
      <Sheet open={open && view !== 'block'} onClose={close} title={view === 'menu' ? `${target.nick}님` : view === 'report' ? '신고 사유를 골라주세요' : '신고가 접수됐어요'}>
        {view === 'menu' && (
          <div className="col" style={{ gap: 8 }}>
            <button className="btn secondary block" onClick={() => setView('report')}><Icon n="flag" s={18} />신고하기</button>
            <button className="btn secondary block" disabled={blocked} onClick={() => setView('block')}>{blocked ? '이미 차단했어요' : '차단하기'}</button>
            <button className="btn ghost block" onClick={close}>취소</button>
          </div>
        )}
        {view === 'report' && (
          <div className="col">
            <div className="col" style={{ gap: 4 }} role="radiogroup" aria-label="신고 사유">
              {REPORT_REASONS.map((r) => (
                <label key={r} className="check" style={{ padding: '8px 0' }}>
                  <input type="radio" name="reason" checked={reason === r} onChange={() => setReason(r)} />{r}
                </label>
              ))}
            </div>
            <div className="field"><label htmlFor="rd">상세 내용 (선택)</label>
              <textarea id="rd" className="input" rows={3} maxLength={300} value={detail} onChange={(e) => setDetail(e.target.value)} placeholder="어떤 일이 있었는지 적어주세요" /></div>
            <div className="field"><label htmlFor="rf">증빙 첨부 (선택)</label>
              <input id="rf" type="file" className="input" style={{ paddingTop: 14 }} onChange={(e) => setFile(e.target.files?.[0]?.name ?? '')} />
              {file && <span className="help">{file}</span>}</div>
            <button className="btn block" disabled={!reason} onClick={() => { a.report(target.id, reason, detail, false); setView('done'); }}>신고 접수</button>
          </div>
        )}
        {view === 'done' && (
          <div className="col">
            <div className="bigcheck"><Icon n="check" s={44} /></div>
            <p className="center">신고가 접수됐어요. 신고자의 다른 활동에는 불이익이 없어요.<br /><span className="cap">운영자가 확인한 뒤 경고, 7일 이용 정지, 영구 정지 중 알맞은 조치를 해요.</span></p>
            <button className="btn secondary block" disabled={blocked} onClick={() => setView('block')}>이 사용자 차단하기</button>
            <button className="btn block" onClick={close}>확인</button>
          </div>
        )}
      </Sheet>
      <Modal open={open && view === 'block'} onClose={() => setView('menu')} title={`${target.nick}님을 차단할까요?`}
        actions={<><button className="btn danger block" onClick={() => { a.block(target.id); close(); }}>차단하기</button><button className="btn ghost block" onClick={() => setView('menu')}>취소</button></>}>
        <p className="cap">차단하면 서로 추천에서 제외돼요. 차단 목록은 마이페이지에서 언제든 풀 수 있어요.</p>
      </Modal>
    </>
  );
}

/** 긴급 신고. 위급하면 112가 먼저라는 점을 가장 크게 보여준다. */
export function EmergencySheet({ open, onClose, candidates, roomId }: { open: boolean; onClose: () => void; candidates: User[]; roomId?: string }) {
  const { a } = useApp();
  const [mode, setMode] = useState<'sos' | 'form' | 'done'>('sos');
  const [target, setTarget] = useState(''); const [detail, setDetail] = useState('');
  const close = () => { onClose(); setTimeout(() => { setMode('sos'); setTarget(''); setDetail(''); }, 200); };
  return (
    <Sheet open={open} onClose={close} full title={mode === 'done' ? '긴급 신고가 접수됐어요' : '긴급 신고'}>
      {mode === 'sos' && (
        <div className="col" style={{ gap: 16 }}>
          <div className="notice danger" style={{ fontSize: 22, fontWeight: 700, lineHeight: 1.4, padding: 20 }}>위급하면 112에 먼저 연락하세요</div>
          <a className="btn danger block" style={{ height: 72, fontSize: 20 }} href="tel:112"><Icon n="phone" />112 전화하기</a>
          <p className="cap center">전화 연결이 안 되면 휴대폰의 전화 앱에서 112를 눌러주세요.</p>
          <button className="btn secondary block" onClick={() => setMode('form')}>앱으로 긴급 신고하기</button>
          <p className="cap">앱 신고는 운영자가 24시간 즉시 대응하는 수단이 아니에요. 위급할 때는 외부 기관 연락이 우선이에요. 실제 운영 시간과 처리 기준은 출시 전에 정해요.</p>
        </div>
      )}
      {mode === 'form' && (
        <div className="col">
          <div className="label">신고할 사람</div>
          <div className="col" style={{ gap: 4 }} role="radiogroup" aria-label="신고 대상">
            {candidates.length === 0 && <p className="cap">이 화면에 다른 참여자가 없어요.</p>}
            {candidates.map((u) => (
              <label key={u.id} className="check" style={{ alignItems: 'center', padding: '6px 0' }}>
                <input type="radio" name="t" checked={target === u.id} onChange={() => setTarget(u.id)} /><Avatar user={u} size="sm" />{u.nick}
              </label>
            ))}
          </div>
          <div className="field"><label htmlFor="ed">무슨 일이 있었나요?</label>
            <textarea id="ed" className="input" rows={4} value={detail} onChange={(e) => setDetail(e.target.value)} placeholder="성희롱, 폭력, 스토킹 등 상황을 간단히 적어주세요" /></div>
          <button className="btn danger block" disabled={!target || !detail.trim()} onClick={() => { a.report(target, '긴급 신고', detail, true, roomId); setMode('done'); }}>긴급 신고 접수</button>
        </div>
      )}
      {mode === 'done' && (
        <div className="col">
          <div className="notice danger">상대와의 직접 연락과 추가 모임 연결을 우선 제한했어요. 운영자가 사실관계를 빠르게 확인해요.</div>
          <div className="card flat col" style={{ gap: 6 }}>
            <h3>도움 받을 수 있는 곳</h3>
            <p className="cap">소속 학교의 인권센터, 성평등상담소 같은 교내 상담 기구에서 상담을 받을 수 있어요. 학교 홈페이지에서 연락처를 확인해 주세요.</p>
            <p className="cap">위급한 상황이면 지금 112에 연락하세요.</p>
          </div>
          <a className="btn danger block" href="tel:112"><Icon n="phone" />112 전화하기</a>
          <button className="btn block" onClick={close}>확인</button>
        </div>
      )}
    </Sheet>
  );
}

/** 모임방과 채팅 화면에 항상 떠 있는 긴급 신고 버튼 */
export function SosButton({ candidates, roomId }: { candidates: User[]; roomId?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <div className="sosbar"><button className="sos" onClick={() => setOpen(true)} aria-label="긴급 신고">긴급 신고</button></div>
      <EmergencySheet open={open} onClose={() => setOpen(false)} candidates={candidates} roomId={roomId} />
    </>
  );
}

export function ReportIcon({ target, roomId }: { target?: User; roomId?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button className="iconbtn" aria-label="신고 · 차단" onClick={() => setOpen(true)}><Icon n="flag" /></button>
      <SafetyMenu open={open} onClose={() => setOpen(false)} target={target} roomId={roomId} />
    </>
  );
}
