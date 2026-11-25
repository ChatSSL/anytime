// 프로필 (서버 8단계)
import { api } from './client';
import type { Profile } from '../types';

export interface InterestItem { name: string; isPublic: boolean }
export interface Me {
  id: number; email: string; school: string; nick: string; dept: string | null; showDept: boolean; avatar: number;
  interests: InterestItem[]; manner: number; completed: number; sanction: 0 | 1 | 2 | 3;
  /** 관심사를 하나 이상 골랐으면 true. 처음 가입한 사용자는 false */
  profileCompleted: boolean;
}
/** 다른 사람 프로필. 이메일·시간표는 없고, 학과는 비공개면 null, 관심사는 공개한 것만 */
export interface UserProfile { id: number; nick: string; dept: string | null; avatar: number; interests: string[]; manner: number; completed: number }
export interface NickCheck { available: boolean; message: string }

// 프론트 화면에는 관심사 공개 여부를 고르는 곳이 없다. 서버에 비공개로 저장된 관심사는 수정할 때도 비공개로 유지한다.
let privateInterests = new Set<string>();
const remember = (me: Me) => { privateInterests = new Set(me.interests.filter((i) => !i.isPublic).map((i) => i.name)); return me; };

/** 서버 응답 → 프론트 Profile */
export const toProfile = (me: Me): Profile => ({
  nick: me.nick, dept: me.dept ?? '', showDept: me.showDept, avatar: me.avatar, interests: me.interests.map((i) => i.name),
});

export const meApi = {
  get: (opts?: { quiet?: boolean }) => api.get<Me>('/me', opts).then(remember),

  /** 닉네임이 겹치면 ApiError(409, 'USER_NICK_TAKEN') */
  update: (p: Profile) => api.patch<Me>('/me', {
    nick: p.nick, dept: p.dept, showDept: p.showDept, avatar: p.avatar,
    interests: p.interests.map((name) => ({ name, isPublic: !privateInterests.has(name) })),
  }).then(remember),

  checkNick: (nick: string) => api.get<NickCheck>(`/users/check-nick?nick=${encodeURIComponent(nick)}`, { quiet: true }),

  /** 다른 사람 프로필. 차단 관계면 404. Person 화면이 추천 목록에 없는 서버 사용자를 열 때 쓴다 */
  user: (id: number) => api.get<UserProfile>(`/users/${id}`, { quiet: true }),
};
