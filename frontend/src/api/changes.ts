// 일정 변경 투표 (서버 15단계)
import { api } from './client';

export interface ServerChange {
  id: number;
  roomId: number;
  status: 'PENDING' | 'APPLIED' | 'REJECTED';
  newDay: number;
  newStart: number;
  newEnd: number;
  /** null이면 장소는 그대로 */
  newPlace: string | null;
  currentDay: number;
  currentStart: number;
  currentEnd: number;
  currentPlace: string;
  currentMeetDate: string;
  /** 답해야 하는 사람 수 (방장·제안 뒤에 들어온 사람 제외) */
  required: number;
  accepted: number;
  myVote: boolean | null;
  canVote: boolean;
  isHost: boolean;
}

export interface ProposeChangeBody { day: number; start: number; end: number; place?: string }

export const changesApi = {
  propose: (roomId: number, body: ProposeChangeBody) =>
    api.post<ServerChange>(`/rooms/${roomId}/changes`, body),
  /** 없으면 서버가 204를 주고, client.ts가 undefined로 돌려준다 */
  pending: async (roomId: number, opts?: { quiet?: boolean }): Promise<ServerChange | null> =>
    (await api.get<ServerChange | undefined>(`/rooms/${roomId}/changes/pending`, opts)) ?? null,
  vote: (changeId: number, accept: boolean) =>
    api.post<ServerChange>(`/changes/${changeId}/vote`, { accept }),
};
