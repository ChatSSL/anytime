import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, ClassBlock, Day, Msg, Profile, Room } from './types';
import { initialState, SEED_VERSION, USERS } from './data';
import { icebreaker } from './lib/ai';
import { hm } from './lib/time';
import { ME_ID, roomChat, userById } from './lib/derive';
import { setApiHandlers } from './api/client';
import { authApi } from './api/auth';

const KEY = 'anytime-state';
const uid = () => Math.random().toString(36).slice(2, 9);

function load(): AppState {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) { const s = JSON.parse(raw); if (s && s.v === SEED_VERSION) return s; }
  } catch { /* 저장소를 못 쓰는 환경에서는 처음 상태로 시작 */ }
  return initialState();
}

type Draft = (d: AppState) => void;
interface Toast { id: number; text: string; kind: 'ok' | 'err' | 'info' }

interface Ctx {
  s: AppState;
  update: (fn: Draft) => void;
  toast: (text: string, kind?: Toast['kind']) => void;
  toasts: Toast[];
  a: ReturnType<typeof makeActions>;
}
const AppCtx = createContext<Ctx>(null as never);
export const useApp = () => useContext(AppCtx);

const clockStr = (d: AppState) => hm(d.clock.min);

function makeActions(update: (fn: Draft) => void, getState: () => AppState, toast: Ctx['toast']) {
  const ensureRoomChat = (d: AppState, room: Room) => {
    let c = roomChat(d, room.id);
    if (!c) {
      const members = room.members.map((id) => userById(d, id)!).filter(Boolean);
      const ice = icebreaker(room, members.filter((m) => m.id !== 'staff'));
      c = {
        id: `chat-${room.id}`, kind: 'room', roomId: room.id, unread: 0,
        messages: [
          { id: uid(), from: 'sys', text: `${userById(d, room.hostId)?.nick ?? '운영팀'}님이 모임방을 열었어요`, time: clockStr(d) },
          { id: uid(), from: 'sys', text: `일정이 ${room.members.length}명의 시간표에 등록됐어요 · ${hm(room.start)}–${hm(room.end)}`, time: clockStr(d) },
          { id: uid(), from: 'ai', text: 'AI 대화 주제', time: clockStr(d), ai: { ...ice, done: false } },
        ],
      };
      d.chats.push(c);
    }
    return c;
  };

  return {
    finishOnboarding: () => update((d) => { d.onboarded = true; }),
    verify: (email: string, school: string) => update((d) => { d.verified = true; d.email = email; d.school = school; }),
    setProfile: (p: Profile) => update((d) => { d.profile = p; }),
    addClass: (c: Omit<ClassBlock, 'id'>) => update((d) => { d.classes.push({ ...c, id: uid() }); }),
    removeClass: (id: string) => update((d) => { d.classes = d.classes.filter((c) => c.id !== id); }),
    loadExample: (classes: ClassBlock[]) => update((d) => { d.classes = classes; }),
    toggleWanted: (key: string, v: boolean) => update((d) => { d.wanted[key] = v; }),

    join: (roomId: string) => {
      update((d) => {
        const r = d.rooms.find((x) => x.id === roomId); if (!r || r.members.includes(ME_ID)) return;
        r.members.push(ME_ID);
        const c = ensureRoomChat(d, r);
        c.messages.push({ id: uid(), from: 'sys', text: `${d.profile?.nick ?? '나'}님이 합류했어요`, time: clockStr(d) });
      });
      toast('합류했어요. 내 시간표에 일정이 등록됐어요', 'ok');
    },
    leave: (roomId: string, reason: string, penalty: boolean) => {
      update((d) => {
        const r = d.rooms.find((x) => x.id === roomId); if (!r) return;
        r.members = r.members.filter((m) => m !== ME_ID);
        d.cancelLog.push({ roomId, reason, at: `${'월화수목금'[d.clock.day]} ${clockStr(d)}` });
        if (penalty) d.manner = Math.max(30, Math.round((d.manner - 0.5) * 10) / 10);
        const c = roomChat(d, roomId);
        if (c) c.messages.push({ id: uid(), from: 'sys', text: `${d.profile?.nick ?? '나'}님이 참여를 취소했어요`, time: clockStr(d) });
      });
      toast('참여를 취소했어요', 'info');
    },
    createRoom: (p: Pick<Room, 'title' | 'activity' | 'day' | 'start' | 'end' | 'place' | 'cap' | 'topic'>): string => {
      const id = `r-${uid()}`;
      update((d) => {
        const r: Room = { ...p, id, members: [ME_ID], hostId: ME_ID };
        d.rooms.push(r);
        ensureRoomChat(d, r);
      });
      return id;
    },
    /** 방장이 시간을 바꾸면 참여자에게 알림이 가고, 참여 여부를 다시 확인한다. (시연용: 다른 방장의 변경을 흉내) */
    simulateHostChange: (roomId: string) => {
      update((d) => {
        const r = d.rooms.find((x) => x.id === roomId); if (!r) return;
        const old = `${hm(r.start)}–${hm(r.end)}`;
        r.start += 30; r.end += 30;
        const host = userById(d, r.hostId)?.nick ?? '방장';
        const c = roomChat(d, roomId);
        if (c) c.messages.push({ id: uid(), from: 'sys', text: `${host}님이 시간을 ${hm(r.start)}으로 바꿨어요 (기존 ${old})`, time: clockStr(d) });
        d.notifs.unshift({ id: uid(), type: 'change', title: '일정이 바뀌었어요', body: `${host}님이 ${r.title} 시간을 ${hm(r.start)}으로 바꿨어요. 참여 여부를 다시 알려주세요`, time: clockStr(d), read: false, link: `/rooms/${roomId}`, needsAction: true });
      });
    },
    resolveChange: (roomId: string, stay: boolean) => {
      update((d) => {
        const n = d.notifs.find((x) => x.type === 'change' && x.needsAction && !x.resolved && x.link === `/rooms/${roomId}`);
        if (n) { n.resolved = stay ? 'accepted' : 'declined'; n.read = true; n.needsAction = false; }
        if (!stay) {
          const r = d.rooms.find((x) => x.id === roomId);
          if (r) r.members = r.members.filter((m) => m !== ME_ID);
          d.cancelLog.push({ roomId, reason: '일정 변경으로 불참', at: `${'월화수목금'[d.clock.day]} ${clockStr(d)}` });
        }
      });
      toast(stay ? '참여를 유지했어요' : '이번엔 빠졌어요', 'info');
    },

    ensureDm: (userId: string): string => {
      let id = '';
      update((d) => {
        let c = d.chats.find((x) => x.kind === 'dm' && x.userId === userId);
        if (!c) {
          const u = userById(d, userId);
          c = { id: `dm-${userId}`, kind: 'dm', userId, unread: 0, messages: [
            { id: uid(), from: 'sys', text: `${u?.nick}님과 연결됐어요`, time: clockStr(d) },
          ] };
          d.chats.push(c);
        }
        id = c.id;
      });
      return `dm-${userId}`;
    },
    requestDm: (userId: string, slot: string, activity: string, message: string) => {
      update((d) => { d.sentRequests.push(userId); });
      toast('요청을 보냈어요', 'ok');
      setTimeout(() => update((d) => {
        const u = userById(d, userId); if (!u) return;
        d.notifs.unshift({ id: uid(), type: 'join', title: '요청이 수락됐어요', body: `${u.nick}님이 ${slot} ${activity} 요청을 수락했어요`, time: clockStr(d), read: false, link: `/chat/dm-${userId}` });
        let c = d.chats.find((x) => x.kind === 'dm' && x.userId === userId);
        if (!c) { c = { id: `dm-${userId}`, kind: 'dm', userId, unread: 1, messages: [] }; d.chats.push(c); }
        c.messages.push({ id: uid(), from: 'sys', text: `서로 공강이 겹치는 ${slot}에 만나요`, time: clockStr(d) });
        if (message) c.messages.push({ id: uid(), from: ME_ID, text: message, time: clockStr(d) });
        c.messages.push({ id: uid(), from: userId, text: '좋아요! 장소는 어디가 편하세요?', time: clockStr(d) });
        c.unread += 1;
      }), 1600);
    },
    respondRequest: (notifId: string, accept: boolean) => {
      update((d) => {
        const n = d.notifs.find((x) => x.id === notifId); if (!n) return;
        n.resolved = accept ? 'accepted' : 'declined'; n.needsAction = false; n.read = true;
        if (accept && n.userId) {
          if (!d.chats.find((x) => x.kind === 'dm' && x.userId === n.userId)) {
            d.chats.push({ id: `dm-${n.userId}`, kind: 'dm', userId: n.userId, unread: 0, messages: [
              { id: uid(), from: 'sys', text: `서로 공강이 겹치는 수 12:00–14:00에 만나요`, time: clockStr(d) },
            ] });
          }
        }
      });
      toast(accept ? '수락했어요. 채팅으로 장소를 정해 보세요' : '거절했어요', 'info');
    },

    send: (chatId: string, text: string) => {
      const t = text.trim(); if (!t) return;
      update((d) => {
        const c = d.chats.find((x) => x.id === chatId); if (!c) return;
        c.messages.push({ id: uid(), from: ME_ID, text: t, time: clockStr(d) });
      });
      // 시연용: 상대가 잠깐 뒤에 답한다.
      setTimeout(() => update((d) => {
        const c = d.chats.find((x) => x.id === chatId); if (!c) return;
        let from: string | undefined = c.userId;
        if (c.kind === 'room') {
          const r = d.rooms.find((x) => x.id === c.roomId);
          from = r?.members.filter((m) => m !== ME_ID && m !== 'staff')[0];
        }
        if (!from) return;
        const replies = ['좋아요!', '네 그때 봬요 :)', '저도 그 시간 괜찮아요', '도착하면 말해줄게요'];
        c.messages.push({ id: uid(), from, text: replies[c.messages.length % replies.length], time: clockStr(d) });
      }), 1400);
    },
    setMission: (chatId: string, msgId: string, done: boolean) => update((d) => {
      const m = d.chats.find((x) => x.id === chatId)?.messages.find((x) => x.id === msgId);
      if (m?.ai) m.ai.done = done;
    }),
    setLink: (chatId: string, link: string) => update((d) => { const c = d.chats.find((x) => x.id === chatId); if (c) c.link = link; }),
    markChatRead: (chatId: string) => update((d) => { const c = d.chats.find((x) => x.id === chatId); if (c) c.unread = 0; }),

    readNotif: (id: string) => update((d) => { const n = d.notifs.find((x) => x.id === id); if (n) n.read = true; }),
    readAllNotifs: () => update((d) => { d.notifs.forEach((n) => { n.read = true; }); }),

    attend: (roomId: string, ids: string[]) => update((d) => { d.attendance[roomId] = ids; }),
    rate: (roomId: string, avg: number) => update((d) => {
      if (!d.rated.includes(roomId)) { d.rated.push(roomId); d.completed += 1; }
      d.manner = Math.min(45, Math.round((d.manner + (avg >= 4 ? 0.2 : avg >= 3 ? 0.05 : 0)) * 10) / 10);
    }),

    block: (userId: string) => { update((d) => { if (!d.blocks.includes(userId)) d.blocks.push(userId); }); toast('차단했어요. 서로 추천에서 제외돼요', 'info'); },
    unblock: (userId: string) => update((d) => { d.blocks = d.blocks.filter((b) => b !== userId); }),
    report: (targetId: string, reason: string, detail: string, emergency: boolean, roomId?: string) => update((d) => {
      d.reports.push({ id: uid(), targetId, reason, detail, emergency, roomId });
      if (emergency && !d.blocks.includes(targetId)) d.blocks.push(targetId); // 직접 연락과 추가 모임 연결을 먼저 제한
    }),

    setClock: (day: Day, min: number) => update((d) => { d.clock = { day, min }; }),
    setNotifSetting: (k: string, v: boolean) => update((d) => { d.settings.notif[k] = v; }),
    setSetting: (k: 'ads' | 'quiet' | 'premium', v: boolean) => update((d) => { d.settings[k] = v; }),
    setSanction: (v: 0 | 1 | 2 | 3) => update((d) => { d.sanction = v; }),
    reset: async () => { await authApi.logout(); localStorage.removeItem(KEY); location.hash = '#/'; location.reload(); },
    initial: () => initialState(),
    users: USERS,
  };
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [s, setS] = useState<AppState>(load);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const ref = useRef(s); ref.current = s;

  useEffect(() => {
    try { localStorage.setItem(KEY, JSON.stringify(s)); } catch { /* 저장 실패는 무시 */ }
  }, [s]);

  const update = useCallback((fn: Draft) => setS((prev) => { const d = structuredClone(prev); fn(d); return d; }), []);
  const toast = useCallback((text: string, kind: Toast['kind'] = 'ok') => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, text, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 2600);
  }, []);
  const a = useMemo(() => makeActions(update, () => ref.current, toast), [update, toast]);

  // 서버 에러는 토스트로, 리프레시까지 만료되면 인증 화면으로 (App이 verified=false면 /auth로 보낸다)
  useEffect(() => {
    setApiHandlers({
      onError: (message) => toast(message, 'err'),
      onLoggedOut: () => { update((d) => { d.verified = false; }); toast('로그인이 만료됐어요. 다시 인증해 주세요', 'info'); },
    });
  }, [toast, update]);
  const value = useMemo(() => ({ s, update, toast, toasts, a }), [s, update, toast, toasts, a]);
  return <AppCtx.Provider value={value}>{children}</AppCtx.Provider>;
}

export type { Msg };
