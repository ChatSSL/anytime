import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, ClassBlock, Day, Msg, Profile, Room, Slot } from './types';
import { initialState, SEED_VERSION, USERS } from './data';
import { icebreaker } from './lib/ai';
import { hm, slotLabel } from './lib/time';
import { ME_ID, roomChat, userById, wantedSlots } from './lib/derive';
import { ApiError, setApiHandlers, tokens } from './api/client';
import { authApi } from './api/auth';
import { meApi, toProfile } from './api/me';
import { fromServer, sameClasses, sameSlots, Timetable, timetableApi } from './api/timetable';
import { recommendApi } from './api/recommend';
import { roomsApi, ServerRoomDetail, toRoom } from './api/rooms';
import { chatsApi, dmRequestsApi, ServerDmRequest } from './api/chats';
import { chatStomp, StompChatMessage } from './api/stomp';
import { CreateReportBody, notificationsApi, safetyApi, toNotif } from './api/safety';
import { reviewApi, ServerReviewResult, SubmitReviewBody } from './api/review';

const KEY = 'anytime-state';
const uid = () => Math.random().toString(36).slice(2, 9);
const isServerId = (id: string) => /^\d+$/.test(id);

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

  // ---- 시간표 서버 저장 (9단계) ----
  // 수업 수정은 removeClass + addClass를 연달아 부르므로, 바로 보내지 않고 한 틱 모았다가 최종 상태만 보낸다.
  // 요청은 한 번에 하나씩만 보내고, 보내는 동안 또 바뀌면 끝난 뒤 한 번 더 보낸다.
  let synced: Timetable | null = null; // 서버에 마지막으로 저장된 상태
  let timer: ReturnType<typeof setTimeout> | undefined;
  let running = false, dirty = false;

  const applyServer = (t: Timetable) => {
    const { classes, wanted } = fromServer(t);
    update((d) => { d.classes = classes; d.wanted = wanted; });
  };

  // ---- 추천 (10단계) & 모임 (11단계) ----
  // 추천은 내 공강·관심사로 계산하므로, 앱 시작·시간표 저장·프로필 저장 뒤에 다시 받는다. 실패하면 이전 목록을 그대로 둔다
  const loadRecommend = async () => {
    if (!authApi.isLoggedIn()) return;
    const [people, roomRecos] = await Promise.all([
      recommendApi.people({ quiet: true }).catch(() => null),
      recommendApi.rooms({ quiet: true }).catch(() => null),
    ]);
    update((d) => {
      if (people) d.serverPeople = people;
      if (roomRecos) d.serverRoomRecos = roomRecos;
    });
  };

  const loadRooms = async () => {
    if (!authApi.isLoggedIn()) return;
    const rooms = await roomsApi.list(undefined, { quiet: true }).catch(() => null);
    if (rooms) update((d) => { d.serverRooms = rooms.map(toRoom); });
  };

  // ---- 채팅·1:1 DM 요청 (13단계) ----
  const loadChats = async () => {
    if (!authApi.isLoggedIn()) return;
    const chats = await chatsApi.list({ quiet: true }).catch(() => null);
    if (chats) update((d) => { d.serverChats = chats; });
  };

  const loadDmRequests = async () => {
    if (!authApi.isLoggedIn()) return;
    const reqs = await dmRequestsApi.list({ quiet: true }).catch(() => null);
    if (reqs) update((d) => { d.serverDmRequests = reqs; });
  };

  // ---- 신고·차단·알림 (17단계) ----
  const loadNotifs = async () => {
    if (!authApi.isLoggedIn()) return;
    const res = await notificationsApi.list({ quiet: true }).catch(() => null);
    if (res) update((d) => { d.notifs = res.items.map((n) => toNotif(n)); });
  };

  const loadBlocks = async () => {
    if (!authApi.isLoggedIn()) return;
    const list = await safetyApi.blocks({ quiet: true }).catch(() => null);
    if (list) update((d) => { d.serverBlocked = list; d.blocks = list.map((b) => String(b.user.id)); });
  };

  // ---- 참석 확인·평가 (16단계) ----
  /** 평가할 모임(홈 배너)과 이미 평가한 모임 id를 받는다. 평가한 id는 rated에 합쳐서 '모임 평가하기' 버튼을 숨긴다 */
  const loadReviews = async () => {
    if (!authApi.isLoggedIn()) return;
    const res = await reviewApi.pending({ quiet: true }).catch(() => null);
    if (res) update((d) => {
      d.serverPendingReviews = res.pending.map(toRoom);
      const done = res.reviewedRoomIds.map(String).filter((id) => !d.rated.includes(id));
      d.rated.push(...done);
    });
  };

  /** 평가 뒤 완료 모임 수·매너 점수만 다시 받는다 */
  const loadMyStats = async () => {
    const me = await meApi.get({ quiet: true }).catch(() => null);
    if (me) update((d) => { d.manner = me.manner; d.completed = me.completed; });
  };

  /** 차단이 바뀌면 추천·모임·1:1 요청·알림에서 그 사람이 빠지거나 다시 보이므로 같이 다시 받는다 */
  const reloadAfterBlockChange = () =>
    Promise.all([loadBlocks(), loadRecommend(), loadRooms(), loadDmRequests(), loadChats(), loadNotifs()]);

  const flushTimetable = async () => {
    if (running) { dirty = true; return; }
    running = true;
    try {
      let changed = false;
      do {
        dirty = false;
        const s = getState();
        if (!synced || !sameClasses(synced.classes.map((c) => ({ ...c, day: c.day as Day, name: c.name ?? '' })), s.classes)) {
          synced = await timetableApi.putClasses(s.classes); changed = true;
        }
        // 공강은 수업에 따라 바뀌므로 수업을 보낸 뒤 지금 켜져 있는 공강을 계산해서 보낸다
        const wanted = wantedSlots(getState());
        if (!sameSlots(wanted, synced.wanted)) { synced = await timetableApi.putWanted(wanted); changed = true; }
      } while (dirty);
      if (changed) { loadRecommend(); loadRooms(); }
    } catch {
      // 토스트는 client.ts가 띄운다. 화면은 서버에 저장된 마지막 상태로 되돌린다
      if (synced) applyServer(synced);
    } finally {
      running = false;
    }
  };

  const syncTimetable = () => {
    if (!authApi.isLoggedIn()) return;
    clearTimeout(timer);
    timer = setTimeout(flushTimetable, 0);
  };

  /** 서버 시간표로 채운다. 서버가 비어 있고 이 기기에 시간표가 있으면(로그인 전에 만든 것) 서버로 올린다 */
  const loadTimetable = async (): Promise<boolean> => {
    const t = await timetableApi.get({ quiet: true }).catch(() => null);
    if (!t) return getState().classes.length > 0;
    synced = t;
    if (t.classes.length === 0 && getState().classes.length > 0) {
      syncTimetable();
      return true;
    }
    applyServer(t);
    return t.classes.length > 0;
  };

  return {
    finishOnboarding: () => update((d) => { d.onboarded = true; }),
    verify: (email: string, school: string) => update((d) => { d.verified = true; d.email = email; d.school = school; }),
    /** 로그인했으면 서버(PATCH /me)에 먼저 저장하고, 서버가 정리한 값으로 바꾼다. 실패하면 ApiError를 던진다 */
    setProfile: async (p: Profile) => {
      const saved = authApi.isLoggedIn() ? toProfile(await meApi.update(p)) : p;
      update((d) => { d.profile = saved; });
      loadRecommend(); // 관심사가 바뀌면 추천 점수도 바뀐다
    },
    /** GET /me, GET /me/timetable. 서버에 프로필·시간표가 있으면(다른 기기에서 만들었거나 다시 로그인) 그걸로 채운다 */
    loadMe: async (): Promise<{ profileCompleted: boolean; hasClasses: boolean } | null> => {
      if (!authApi.isLoggedIn()) return null;
      let me;
      try {
        me = await meApi.get({ quiet: true });
      } catch (e) {
        if (e instanceof ApiError && (e.status === 404 || e.code === 'USER_NOT_FOUND')) {
          chatStomp.disconnect();
          tokens.clear();
          update((d) => {
            d.verified = false;
            d.profile = null;
            d.myUserId = undefined;
            d.serverPeople = undefined;
            d.serverRooms = undefined;
            d.serverRoomRecos = undefined;
            d.serverChats = undefined;
            d.serverDmRequests = undefined;
            d.serverBlocked = undefined;
            d.serverPendingReviews = undefined;
          });
          toast('계정 정보가 초기화되어 다시 로그인이 필요해요', 'info');
        }
        return null;
      }
      update((d) => {
        d.myUserId = me.id;
        d.school = me.school; d.email = me.email;
        d.manner = me.manner; d.completed = me.completed; d.sanction = me.sanction;
        d.profile = me.profileCompleted ? toProfile(me) : null;
      });
      const hasClasses = await loadTimetable();
      await Promise.all([loadRecommend(), loadRooms(), loadChats(), loadDmRequests(), loadNotifs(), loadBlocks(), loadReviews()]);
      return { profileCompleted: me.profileCompleted, hasClasses };
    },
    loadRecommend,
    loadRooms,
    loadChats,
    loadDmRequests,
    loadNotifs,
    loadBlocks,
    // 시간표는 화면에 바로 반영하고 서버 저장(PUT /me/classes, /me/wanted)은 뒤에서 한다. 실패하면 서버 상태로 되돌린다
    addClass: (c: Omit<ClassBlock, 'id'>) => { update((d) => { d.classes.push({ ...c, id: uid() }); }); syncTimetable(); },
    removeClass: (id: string) => { update((d) => { d.classes = d.classes.filter((c) => c.id !== id); }); syncTimetable(); },
    loadExample: (classes: ClassBlock[]) => { update((d) => { d.classes = classes; }); syncTimetable(); },
    toggleWanted: (key: string, v: boolean) => { update((d) => { d.wanted[key] = v; }); syncTimetable(); },

    join: async (roomId: string): Promise<ServerRoomDetail | undefined> => {
      if (authApi.isLoggedIn() && isServerId(roomId)) {
        const detail = await roomsApi.join(Number(roomId));
        await Promise.all([loadRooms(), loadRecommend(), loadChats()]);
        toast('합류했어요. 내 시간표에 일정이 등록됐어요', 'ok');
        return detail;
      }
      update((d) => {
        const r = d.rooms.find((x) => x.id === roomId); if (!r || r.members.includes(ME_ID)) return;
        r.members.push(ME_ID);
        const c = ensureRoomChat(d, r);
        c.messages.push({ id: uid(), from: 'sys', text: `${d.profile?.nick ?? '나'}님이 합류했어요`, time: clockStr(d) });
      });
      toast('합류했어요. 내 시간표에 일정이 등록됐어요', 'ok');
      return undefined;
    },
    leave: async (roomId: string, reason: string, penalty: boolean): Promise<ServerRoomDetail | undefined> => {
      if (authApi.isLoggedIn() && isServerId(roomId)) {
        const detail = await roomsApi.leave(Number(roomId), reason);
        const [me] = await Promise.all([
          meApi.get({ quiet: true }).catch(() => null),
          loadRooms(),
          loadRecommend(),
          loadChats(),
        ]);
        if (me) {
          update((d) => { d.manner = me.manner; d.completed = me.completed; d.sanction = me.sanction; });
        }
        toast('참여를 취소했어요', 'info');
        return detail;
      }
      update((d) => {
        const r = d.rooms.find((x) => x.id === roomId); if (!r) return;
        r.members = r.members.filter((m) => m !== ME_ID);
        d.cancelLog.push({ roomId, reason, at: `${'월화수목금'[d.clock.day]} ${clockStr(d)}` });
        if (penalty) d.manner = Math.max(30, Math.round((d.manner - 0.5) * 10) / 10);
        const c = roomChat(d, roomId);
        if (c) c.messages.push({ id: uid(), from: 'sys', text: `${d.profile?.nick ?? '나'}님이 참여를 취소했어요`, time: clockStr(d) });
      });
      toast('참여를 취소했어요', 'info');
      return undefined;
    },
    cancelRoom: async (roomId: string): Promise<ServerRoomDetail | undefined> => {
      if (authApi.isLoggedIn() && isServerId(roomId)) {
        const detail = await roomsApi.cancel(Number(roomId));
        await Promise.all([loadRooms(), loadRecommend(), loadChats()]);
        toast('모임을 취소했어요', 'info');
        return detail;
      }
      update((d) => {
        const r = d.rooms.find((x) => x.id === roomId); if (!r) return;
        r.status = 'cancelled';
        const c = roomChat(d, roomId);
        if (c) c.messages.push({ id: uid(), from: 'sys', text: `${d.profile?.nick ?? '방장'}님이 모임을 취소했어요`, time: clockStr(d) });
      });
      toast('모임을 취소했어요', 'info');
      return undefined;
    },
    transferHost: async (roomId: string, userId: string): Promise<ServerRoomDetail | undefined> => {
      if (authApi.isLoggedIn() && isServerId(roomId) && isServerId(userId)) {
        const detail = await roomsApi.transferHost(Number(roomId), Number(userId));
        await Promise.all([loadRooms(), loadRecommend(), loadChats()]);
        toast('방장을 넘겼어요', 'ok');
        return detail;
      }
      update((d) => {
        const r = d.rooms.find((x) => x.id === roomId); if (!r) return;
        r.hostId = userId;
        const targetNick = userById(d, userId)?.nick ?? '새 방장';
        const c = roomChat(d, roomId);
        if (c) c.messages.push({ id: uid(), from: 'sys', text: `${targetNick}님이 새 방장이 되었어요`, time: clockStr(d) });
      });
      toast('방장을 넘겼어요', 'ok');
      return undefined;
    },
    createRoom: async (p: Pick<Room, 'title' | 'activity' | 'day' | 'start' | 'end' | 'place' | 'cap' | 'topic'>): Promise<string> => {
      if (authApi.isLoggedIn()) {
        const created = await roomsApi.create({
          title: p.title, activity: p.activity, day: p.day, start: p.start, end: p.end,
          place: p.place, cap: p.cap, topic: p.topic || undefined,
        });
        await Promise.all([loadRooms(), loadRecommend(), loadChats()]);
        return String(created.id);
      }
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
    requestDm: async (userId: string, slot: Slot, activity: string, message: string): Promise<void> => {
      if (authApi.isLoggedIn() && isServerId(userId)) {
        await dmRequestsApi.create({
          targetUserId: Number(userId),
          day: slot.day,
          start: slot.start,
          end: slot.end,
          activity,
          message: message || undefined,
        });
        await loadDmRequests();
        toast('요청을 보냈어요', 'ok');
        return;
      }
      const label = slotLabel(slot);
      update((d) => { d.sentRequests.push(userId); });
      toast('요청을 보냈어요', 'ok');
      setTimeout(() => update((d) => {
        const u = userById(d, userId); if (!u) return;
        d.notifs.unshift({ id: uid(), type: 'join', title: '요청이 수락됐어요', body: `${u.nick}님이 ${label} ${activity} 요청을 수락했어요`, time: clockStr(d), read: false, link: `/chat/dm-${userId}` });
        let c = d.chats.find((x) => x.kind === 'dm' && x.userId === userId);
        if (!c) { c = { id: `dm-${userId}`, kind: 'dm', userId, unread: 1, messages: [] }; d.chats.push(c); }
        c.messages.push({ id: uid(), from: 'sys', text: `서로 공강이 겹치는 ${label}에 만나요`, time: clockStr(d) });
        if (message) c.messages.push({ id: uid(), from: ME_ID, text: message, time: clockStr(d) });
        c.messages.push({ id: uid(), from: userId, text: '좋아요! 장소는 어디가 편하세요?', time: clockStr(d) });
        c.unread += 1;
      }), 1600);
    },
    acceptDmRequest: async (requestId: number): Promise<ServerDmRequest> => {
      const res = await dmRequestsApi.accept(requestId);
      await Promise.all([loadDmRequests(), loadChats()]);
      toast('수락했어요. 채팅으로 장소를 정해 보세요', 'ok');
      return res;
    },
    declineDmRequest: async (requestId: number): Promise<ServerDmRequest> => {
      const res = await dmRequestsApi.decline(requestId);
      await loadDmRequests();
      toast('거절했어요', 'info');
      return res;
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
      if (authApi.isLoggedIn() && isServerId(chatId)) {
        chatStomp.publishMessage(Number(chatId), t);
        return;
      }
      update((d) => {
        const c = d.chats.find((x) => x.id === chatId); if (!c) return;
        c.messages.push({ id: uid(), from: ME_ID, text: t, time: clockStr(d) });
      });
      // 시연용(로그인 전): 상대가 잠깐 뒤에 답한다.
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
    applyIncomingMessage: (msg: StompChatMessage, viewing: boolean) => {
      update((d) => {
        if (!d.serverChats) return;
        const idx = d.serverChats.findIndex((c) => c.id === msg.chatId);
        if (idx < 0) return;
        const c = d.serverChats[idx];
        const isMine = msg.senderId !== null && d.myUserId !== undefined && msg.senderId === d.myUserId;
        const isDuplicate = c.lastMessage?.id === msg.id;
        c.lastMessage = { ...msg, mine: isMine };
        if (msg.createdAt) c.lastActivityAt = msg.createdAt;
        if (viewing || isMine) {
          c.unreadCount = 0;
        } else if (!isDuplicate) {
          c.unreadCount += 1;
        }
        d.serverChats.sort((x, y) => {
          const tx = x.lastActivityAt ?? '';
          const ty = y.lastActivityAt ?? '';
          return ty.localeCompare(tx) || y.id - x.id;
        });
      });
    },
    setMission: (chatId: string, msgId: string, done: boolean) => update((d) => {
      const m = d.chats.find((x) => x.id === chatId)?.messages.find((x) => x.id === msgId);
      if (m?.ai) m.ai.done = done;
    }),
    setLink: async (chatId: string, link: string): Promise<string | null> => {
      const trimmed = link.trim();
      if (authApi.isLoggedIn() && isServerId(chatId)) {
        const updated = await chatsApi.updateLink(Number(chatId), trimmed || null);
        update((d) => {
          if (d.serverChats) {
            const idx = d.serverChats.findIndex((x) => x.id === updated.id);
            if (idx >= 0) d.serverChats[idx] = updated;
          }
        });
        toast(updated.link ? '오픈채팅 링크를 저장했어요' : '오픈채팅 링크를 지웠어요', 'ok');
        return updated.link;
      }
      update((d) => { const c = d.chats.find((x) => x.id === chatId); if (c) c.link = trimmed || undefined; });
      return trimmed || null;
    },
    markChatRead: async (chatId: string): Promise<void> => {
      if (authApi.isLoggedIn() && isServerId(chatId)) {
        update((d) => {
          const c = d.serverChats?.find((x) => x.id === Number(chatId));
          if (c) c.unreadCount = 0;
        });
        await chatsApi.markRead(Number(chatId), { quiet: true }).catch(() => null);
        return;
      }
      update((d) => { const c = d.chats.find((x) => x.id === chatId); if (c) c.unread = 0; });
    },

    // 알림 읽음: 화면은 바로 바꾸고 서버(POST /notifications/...)는 뒤에서. 실패하면 서버 목록으로 다시 맞춘다
    readNotif: (id: string) => {
      update((d) => { const n = d.notifs.find((x) => x.id === id); if (n) n.read = true; });
      if (authApi.isLoggedIn() && isServerId(id)) {
        notificationsApi.read(Number(id), { quiet: true }).catch(() => loadNotifs());
      }
    },
    readAllNotifs: () => {
      update((d) => { d.notifs.forEach((n) => { n.read = true; }); });
      if (authApi.isLoggedIn()) notificationsApi.readAll({ quiet: true }).catch(() => loadNotifs());
    },

    loadReviews,
    /**
     * 참석 확인 + 평가 제출 (16단계, 서버 모임).
     * 성공하면 결과를 돌려주고, 실패하면 client.ts가 토스트를 띄우고 null.
     * 점수는 서버 크론이 모아서 반영하므로 여기서 매너 점수를 직접 바꾸지 않는다.
     */
    submitReview: async (roomId: string, body: SubmitReviewBody): Promise<ServerReviewResult | null> => {
      let res: ServerReviewResult;
      try { res = await reviewApi.submit(Number(roomId), body); } catch { return null; }
      update((d) => {
        if (!d.rated.includes(roomId)) d.rated.push(roomId);
        d.attendance[roomId] = body.presentUserIds.map(String);
      });
      await Promise.all([loadReviews(), loadMyStats()]);
      return res;
    },
    attend: (roomId: string, ids: string[]) => update((d) => { d.attendance[roomId] = ids; }),
    rate: (roomId: string, avg: number) => update((d) => {
      if (!d.rated.includes(roomId)) { d.rated.push(roomId); d.completed += 1; }
      d.manner = Math.min(45, Math.round((d.manner + (avg >= 4 ? 0.2 : avg >= 3 ? 0.05 : 0)) * 10) / 10);
    }),

    /** 차단 (17단계). 성공하면 true. 실패하면 client.ts가 토스트를 띄우고 false */
    block: async (userId: string): Promise<boolean> => {
      if (authApi.isLoggedIn() && isServerId(userId)) {
        try { await safetyApi.block(Number(userId)); } catch { return false; }
        await reloadAfterBlockChange();
      } else {
        update((d) => { if (!d.blocks.includes(userId)) d.blocks.push(userId); });
      }
      toast('차단했어요. 서로 추천에서 제외돼요', 'info');
      return true;
    },
    unblock: async (userId: string): Promise<boolean> => {
      if (authApi.isLoggedIn() && isServerId(userId)) {
        try { await safetyApi.unblock(Number(userId)); } catch { return false; }
        await reloadAfterBlockChange();
      } else {
        update((d) => { d.blocks = d.blocks.filter((b) => b !== userId); });
      }
      toast('차단을 풀었어요', 'info');
      return true;
    },
    /** 신고 (17단계). 긴급 신고면 서버가 차단까지 한다. 성공하면 true */
    report: async (targetId: string, reason: string, detail: string, emergency: boolean, roomId?: string): Promise<boolean> => {
      if (authApi.isLoggedIn() && isServerId(targetId)) {
        const body: CreateReportBody = {
          targetUserId: Number(targetId), reason, emergency,
          detail: detail.trim() || undefined,
          roomId: roomId && isServerId(roomId) ? Number(roomId) : undefined,
        };
        try {
          const res = await safetyApi.report(body);
          if (res.blocked) await reloadAfterBlockChange();
        } catch { return false; }
        return true;
      }
      update((d) => {
        d.reports.push({ id: uid(), targetId, reason, detail, emergency, roomId });
        if (emergency && !d.blocks.includes(targetId)) d.blocks.push(targetId); // 직접 연락과 추가 모임 연결을 먼저 제한
      });
      return true;
    },

    setClock: (day: Day, min: number) => update((d) => { d.clock = { day, min }; }),
    setNotifSetting: (k: string, v: boolean) => update((d) => { d.settings.notif[k] = v; }),
    setSetting: (k: 'ads' | 'quiet' | 'premium', v: boolean) => update((d) => { d.settings[k] = v; }),
    setSanction: (v: 0 | 1 | 2 | 3) => update((d) => { d.sanction = v; }),
    reset: async () => { chatStomp.disconnect(); await authApi.logout(); localStorage.removeItem(KEY); location.hash = '#/'; location.reload(); },
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
      onLoggedOut: () => {
        chatStomp.disconnect();
        update((d) => {
          d.verified = false;
          d.myUserId = undefined;
          d.serverPeople = undefined;
          d.serverRooms = undefined;
          d.serverRoomRecos = undefined;
          d.serverChats = undefined;
          d.serverDmRequests = undefined;
          d.serverBlocked = undefined;
          d.serverPendingReviews = undefined;
        });
        toast('로그인이 만료됐어요. 다시 인증해 주세요', 'info');
      },
    });
    return chatStomp.onError((err) => toast(err.message || '메시지를 보내지 못했어요', 'err'));
  }, [toast, update]);
  // 앱을 켤 때 서버의 내 프로필로 맞춘다 (연동 가이드 '프론트를 고치는 방식' 3번)
  useEffect(() => { a.loadMe(); }, [a]);
  const value = useMemo(() => ({ s, update, toast, toasts, a }), [s, update, toast, toasts, a]);
  return <AppCtx.Provider value={value}>{children}</AppCtx.Provider>;
}

export type { Msg };
