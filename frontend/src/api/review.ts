// 참석 확인·상호 평가 (서버 16단계)
import { api } from './client';
import type { UserProfile } from './me';
import type { ServerRoom } from './rooms';
import type { RoomStatus } from '../lib/derive';

/** GET /api/reviews */
export interface ServerPendingReviews {
  /** 끝났고 아직 평가하지 않은 내 모임 (최근 7일) */
  pending: ServerRoom[];
  reviewedRoomIds: number[];
}

/** GET /api/rooms/{id}/review */
export interface ServerReviewForm {
  roomId: number;
  title: string;
  activity: string;
  meetDate: string;
  start: number;
  end: number;
  status: RoomStatus;
  memberCount: number;
  /** 나를 뺀 참여자 (차단 관계 제외) */
  members: UserProfile[];
  reviewed: boolean;
  canReview: boolean;
  lowReasons: string[];
}

export interface SubmitReviewBody {
  presentUserIds: number[];
  ratings: { targetUserId: number; score: number; reason?: string }[];
  note?: string;
}

export interface ServerReviewResult {
  roomId: number;
  savedRatings: number;
  myConfirmations: number;
  memberCount: number;
  myCompleted: boolean;
}

export const reviewApi = {
  pending: (opts?: { quiet?: boolean }) => api.get<ServerPendingReviews>('/reviews', opts),
  form: (roomId: number, opts?: { quiet?: boolean }) => api.get<ServerReviewForm>(`/rooms/${roomId}/review`, opts),
  submit: (roomId: number, body: SubmitReviewBody) => api.post<ServerReviewResult>(`/rooms/${roomId}/review`, body),
};
