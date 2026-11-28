// 채팅·1:1 요청 (서버 13단계). 채팅 목록·메시지 이력·읽음 처리·오픈채팅 링크·1:1 DM 요청을 연결한다.
// 실시간 메시지 송신은 14단계(STOMP WebSocket)에서 추가된다.
import { api } from './client';
import type { UserProfile } from './me';
import type { Day, RoomStatus } from '../types';

export interface ServerAiMission {
  questions: string[];
  mission: string;
}

export interface ServerChatMessage {
  id: number;
  chatId: number;
  senderId: number | null;
  senderNick: string | null;
  system: boolean;
  mine: boolean;
  text: string;
  createdAt: string | null;
}

export interface ServerChatMessagesPage {
  chatId: number;
  messages: ServerChatMessage[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ServerChatRoomInfo {
  id: number;
  title: string;
  activity: string;
  day: Day;
  start: number;
  end: number;
  meetDate: string;
  place: string;
  cap: number;
  memberCount: number;
  status: RoomStatus;
}

export interface ServerChatSummary {
  id: number;
  kind: 'room' | 'dm';
  roomId: number | null;
  room: ServerChatRoomInfo | null;
  otherUser: UserProfile | null;
  link: string | null;
  mission: ServerAiMission | null;
  missionDone: boolean;
  lastMessage: ServerChatMessage | null;
  unreadCount: number;
  lastActivityAt: string | null;
}

export interface ServerDmRequest {
  id: number;
  fromUser: UserProfile;
  toUser: UserProfile;
  day: Day;
  start: number;
  end: number;
  activity: string;
  message: string | null;
  status: 'pending' | 'accepted' | 'declined';
  chatId: number | null;
  createdAt: string | null;
}

export interface ServerDmRequestList {
  received: ServerDmRequest[];
  sent: ServerDmRequest[];
}

export interface CreateDmRequestBody {
  targetUserId: number;
  day: number;
  start: number;
  end: number;
  activity?: string;
  message?: string;
}

export const chatsApi = {
  list: (opts?: { quiet?: boolean }) =>
    api.get<ServerChatSummary[]>('/chats', opts),
  messages: (chatId: number, page = 0, size = 50, opts?: { quiet?: boolean }) =>
    api.get<ServerChatMessagesPage>(`/chats/${chatId}/messages?page=${page}&size=${size}`, opts),
  markRead: (chatId: number, opts?: { quiet?: boolean }) =>
    api.post<void>(`/chats/${chatId}/read`, undefined, opts),
  updateLink: (chatId: number, link: string | null) =>
    api.patch<ServerChatSummary>(`/chats/${chatId}/link`, { link }),
};

export const dmRequestsApi = {
  create: (body: CreateDmRequestBody) =>
    api.post<ServerDmRequest>('/dm-requests', body),
  list: (opts?: { quiet?: boolean }) =>
    api.get<ServerDmRequestList>('/dm-requests', opts),
  accept: (id: number) =>
    api.post<ServerDmRequest>(`/dm-requests/${id}/accept`),
  decline: (id: number) =>
    api.post<ServerDmRequest>(`/dm-requests/${id}/decline`),
};
