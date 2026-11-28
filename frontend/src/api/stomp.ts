// 실시간 채팅 STOMP 클라이언트 (서버 14단계).
// - 엔드포인트: /ws/chat (네이티브 WebSocket, SockJS 미사용)
// - CONNECT 네이티브 헤더에 Authorization: Bearer <accessToken> 전송 (토큰 원문 로깅 금지)
// - 구독: /topic/chat.{chatId}, /user/queue/errors
// - 송신: /app/chat.send ({ chatId, text })
import { Client, IFrame, IMessage, StompSubscription } from '@stomp/stompjs';
import { refreshAccessToken, tokens } from './client';

export interface StompChatMessage {
  id: number;
  chatId: number;
  senderId: number | null;
  senderNick: string | null;
  system: boolean;
  text: string;
  createdAt: string | null;
}

export interface StompErrorPayload {
  code: string;
  message: string;
}

type ChatListener = (msg: StompChatMessage) => void;
type ErrorListener = (err: StompErrorPayload) => void;

const chatListeners = new Map<number, Set<ChatListener>>();
const errorListeners = new Set<ErrorListener>();
const activeSubs = new Map<number, StompSubscription>();
let errorSub: StompSubscription | null = null;
let pendingPublishes: { chatId: number; text: string }[] = [];
let client: Client | null = null;
let needsRefresh = false;

function buildWsUrl(): string {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${location.host}/ws/chat`;
}

function subscribeTopic(c: Client, chatId: number) {
  if (activeSubs.has(chatId) || !c.connected) return;
  const sub = c.subscribe(`/topic/chat.${chatId}`, (frame: IMessage) => {
    try {
      const payload = JSON.parse(frame.body) as StompChatMessage;
      chatListeners.get(chatId)?.forEach((fn) => fn(payload));
    } catch {
      // 잘못된 JSON 프레임은 무시
    }
  });
  activeSubs.set(chatId, sub);
}

function subscribeErrors(c: Client) {
  if (errorSub || !c.connected) return;
  errorSub = c.subscribe('/user/queue/errors', (frame: IMessage) => {
    try {
      const payload = JSON.parse(frame.body) as StompErrorPayload;
      errorListeners.forEach((fn) => fn(payload));
    } catch {
      // 잘못된 JSON 프레임은 무시
    }
  });
}

function ensureClient(): Client {
  if (client) return client;
  const c = new Client({
    brokerURL: buildWsUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: async () => {
      if (needsRefresh) {
        needsRefresh = false;
        const ok = await refreshAccessToken();
        if (!ok) {
          c.deactivate();
          return;
        }
      }
      const t = tokens.get();
      if (!t) {
        c.deactivate();
        return;
      }
      c.connectHeaders = { Authorization: `Bearer ${t.accessToken}` };
    },
    onConnect: () => {
      activeSubs.clear();
      errorSub = null;
      subscribeErrors(c);
      for (const chatId of chatListeners.keys()) {
        subscribeTopic(c, chatId);
      }
      if (pendingPublishes.length > 0) {
        const queued = pendingPublishes;
        pendingPublishes = [];
        for (const item of queued) {
          c.publish({
            destination: '/app/chat.send',
            body: JSON.stringify(item),
          });
        }
      }
    },
    onWebSocketClose: () => {
      activeSubs.clear();
      errorSub = null;
    },
    onStompError: (frame: IFrame) => {
      const detail = `${frame.headers['message'] ?? ''} ${frame.body ?? ''}`;
      if (detail.includes('AUTH_TOKEN_EXPIRED') || detail.includes('AUTH_TOKEN_INVALID')) {
        needsRefresh = true;
      }
    },
  });
  client = c;
  return c;
}

function ensureConnected() {
  if (!tokens.get()) return;
  const c = ensureClient();
  if (!c.active) {
    c.activate();
  }
}

export const chatStomp = {
  /** /topic/chat.{chatId} 구독. 반환되는 정리 함수로 구독 해제한다. */
  subscribeChat(chatId: number, listener: ChatListener): () => void {
    let set = chatListeners.get(chatId);
    if (!set) {
      set = new Set();
      chatListeners.set(chatId, set);
    }
    set.add(listener);
    ensureConnected();
    if (client?.connected) {
      subscribeErrors(client);
      subscribeTopic(client, chatId);
    }
    return () => {
      const current = chatListeners.get(chatId);
      if (!current) return;
      current.delete(listener);
      if (current.size === 0) {
        chatListeners.delete(chatId);
        const sub = activeSubs.get(chatId);
        if (sub) {
          try { sub.unsubscribe(); } catch { /* 이미 끊긴 세션 무시 */ }
          activeSubs.delete(chatId);
        }
      }
    };
  },

  /** /user/queue/errors 에러 수신 리스너 등록 */
  onError(listener: ErrorListener): () => void {
    errorListeners.add(listener);
    return () => {
      errorListeners.delete(listener);
    };
  },

  /** /app/chat.send 로 메시지 발행 (연결 직전이면 onConnect 시 즉시 전송) */
  publishMessage(chatId: number, text: string) {
    const trimmed = text.trim();
    if (!trimmed || !tokens.get()) return;
    ensureConnected();
    if (client?.connected) {
      client.publish({
        destination: '/app/chat.send',
        body: JSON.stringify({ chatId, text: trimmed }),
      });
    } else {
      if (pendingPublishes.length >= 20) pendingPublishes.shift();
      pendingPublishes.push({ chatId, text: trimmed });
    }
  },

  /** 로그아웃/세션 만료 시 연결 및 대기 큐 정리 */
  disconnect() {
    pendingPublishes = [];
    activeSubs.clear();
    errorSub = null;
    needsRefresh = false;
    if (client) {
      const target = client;
      client = null;
      target.deactivate();
    }
  },
};
