// 이메일 인증 = 로그인 (서버 5·7단계)
import { api, tokens } from './client';

export interface SendCodeResponse { expiresIn: number; resendAfter: number }
export interface LoginResponse { userId: number; newUser: boolean; accessToken: string; refreshToken: string; expiresIn: number }

export const authApi = {
  /** 학교 이메일로 6자리 인증번호 발송 */
  sendCode: (email: string) => api.post<SendCodeResponse>('/auth/email', { email }, { quiet: true, auth: false }),

  /** 인증번호 확인. 성공하면 토큰을 저장한다 */
  verify: async (email: string, code: string) => {
    const res = await api.post<LoginResponse>('/auth/email/verify', { email, code }, { quiet: true, auth: false });
    tokens.set(res);
    return res;
  },

  /** 리프레시 토큰을 서버에서 폐기하고 내 토큰도 지운다. 서버가 꺼져 있어도 로그아웃은 된다 */
  logout: async () => {
    const t = tokens.get();
    tokens.clear();
    if (t) await api.post('/auth/logout', { refreshToken: t.refreshToken }, { quiet: true, auth: false }).catch(() => {});
  },

  isLoggedIn: () => tokens.get() !== null,
};
