// 신고·차단·알림 (서버 17단계)
import { api } from './client';
import type { UserProfile } from './me';
import type { Notif, NotifType } from '../types';

/** GET /api/blocks 한 줄 */
export interface ServerBlocked { user: UserProfile; blockedAt: string }

export interface CreateReportBody {
  targetUserId: number;
  reason: string;
  detail?: string;
  roomId?: number;
  emergency?: boolean;
}
/** blocked=true면 긴급 신고라 서버가 차단까지 했다 */
export interface ServerReport { id: number; emergency: boolean; blocked: boolean }

export interface ServerNotification {
  id: number;
  type: NotifType;
  title: string;
  body: string;
  link: string | null;
  relatedUserId: number | null;
  needsAction: boolean;
  resolved: 'accepted' | 'declined' | null;
  read: boolean;
  createdAt: string; // 한국 시간 LocalDateTime, 예: 2026-11-28T15:02:11
}
export interface ServerNotificationList { items: ServerNotification[]; unreadCount: number }

type Opts = { quiet?: boolean };

export const safetyApi = {
  blocks: (opts?: Opts) => api.get<ServerBlocked[]>('/blocks', opts),
  block: (targetUserId: number) => api.post<ServerBlocked>('/blocks', { targetUserId }),
  unblock: (targetUserId: number) => api.delete<void>(`/blocks/${targetUserId}`),
  report: (body: CreateReportBody) => api.post<ServerReport>('/reports', body),
};

export const notificationsApi = {
  list: (opts?: Opts) => api.get<ServerNotificationList>('/notifications', opts),
  read: (id: number, opts?: Opts) => api.post<ServerNotification>(`/notifications/${id}/read`, undefined, opts),
  readAll: (opts?: Opts) => api.post<{ updated: number }>('/notifications/read-all', undefined, opts),
};

const pad = (n: number) => String(n).padStart(2, '0');

/**
 * 서버 알림 → 프론트 Notif. 알림 화면은 time에 ':'가 있으면 '오늘'로 묶으므로
 * 오늘이면 '15:02', 아니면 '11월 26일'로 넣는다.
 */
export const toNotif = (n: ServerNotification, now: Date = new Date()): Notif => {
  const [date = '', time = ''] = n.createdAt.split('T');
  const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const [, mm, dd] = date.split('-');
  return {
    id: String(n.id),
    type: n.type,
    title: n.title,
    body: n.body,
    time: date === today ? time.slice(0, 5) : `${Number(mm)}월 ${Number(dd)}일`,
    read: n.read,
    link: n.link ?? undefined,
    userId: n.relatedUserId == null ? undefined : String(n.relatedUserId),
    needsAction: n.needsAction,
    resolved: n.resolved ?? undefined,
  };
};
