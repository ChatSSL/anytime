export type Day = 0 | 1 | 2 | 3 | 4;
export const DAYS = ['월', '화', '수', '목', '금'] as const;

export interface ClassBlock { id: string; name: string; day: Day; start: number; end: number }
export interface Slot { day: Day; start: number; end: number }

export interface User {
  id: string; nick: string; dept?: string; color: string;
  interests: string[]; manner: number; meetups: number; classes: ClassBlock[];
}

export interface Room {
  id: string; title: string; activity: string;
  day: Day; start: number; end: number; place: string;
  cap: number; members: string[]; hostId: string; topic: string; official?: boolean;
}

export interface Msg {
  id: string; from: 'me' | 'sys' | string; text: string; time: string;
  ai?: { questions: string[]; mission: string; done: boolean };
}
export interface Chat { id: string; kind: 'room' | 'dm'; roomId?: string; userId?: string; messages: Msg[]; unread: number; link?: string }

export type NotifType = 'reco' | 'join' | 'request' | 'reminder' | 'change' | 'cancel' | 'rate';
export interface Notif {
  id: string; type: NotifType; title: string; body: string; time: string; read: boolean;
  link?: string; userId?: string; needsAction?: boolean; resolved?: 'accepted' | 'declined';
}

export interface Report { id: string; targetId: string; reason: string; detail: string; emergency: boolean; roomId?: string }
export interface Profile { nick: string; dept: string; showDept: boolean; avatar: number; interests: string[] }

export interface AppState {
  v: number;
  onboarded: boolean; verified: boolean; email: string; school: string;
  profile: Profile | null;
  classes: ClassBlock[];
  wanted: Record<string, boolean>;          // key = "day-start-end" -> 만나고 싶은 공강
  rooms: Room[]; chats: Chat[]; notifs: Notif[];
  blocks: string[]; reports: Report[];
  attendance: Record<string, string[]>;     // roomId -> 실제 참석 확인한 userIds
  rated: string[];                          // 평가를 마친 roomId
  cancelLog: { roomId: string; reason: string; at: string }[];
  manner: number; completed: number;
  clock: { day: Day; min: number };
  settings: { notif: Record<string, boolean>; ads: boolean; quiet: boolean; premium: boolean };
  sentRequests: string[];
  sanction: 0 | 1 | 2 | 3;
}
