import { AppState, Room, User } from './types';
import { parseClasses } from './lib/time';

export const SCHOOLS = [
  { name: '단국대학교', domain: 'dankook.ac.kr' },
  { name: '서울대학교', domain: 'snu.ac.kr' },
  { name: '연세대학교', domain: 'yonsei.ac.kr' },
  { name: '고려대학교', domain: 'korea.ac.kr' },
];

export const ACTIVITIES = ['보드게임', '카페공부', '산책', '점심', '운동', '영화', '게임', '사진', '음악', '독서', '코딩', '전시', '언어교환', '스터디', '수다'];
export const PLACES = ['학생회관 1층 라운지', '도서관 앞 카페', '중앙 잔디', '학생식당', '공대 로비', '정문 앞 광장'];
export const AVATAR_COLORS = ['#5B4BFF', '#1FA971', '#F5A524', '#E5484D', '#2F9BF0', '#B24BFF', '#FF6B8B', '#2B2B4D'];

export const EXAMPLE_TIMETABLE = [
  '월 09:00-10:30 자료구조', '월 13:00-14:30 운영체제', '화 10:30-12:00 인공지능융합플랫폼', '화 15:00-16:30 소프트웨어공학',
  '수 10:30-12:00 데이터베이스', '수 14:00-15:30 네트워크', '목 09:00-10:30 알고리즘', '목 13:00-14:30 클라우드',
  '금 10:30-12:00 운영체제보안', '금 14:00-15:30 캡스톤',
];

const u = (id: string, nick: string, dept: string, color: string, interests: string[], manner: number, meetups: number, cls: string[]): User =>
  ({ id, nick, dept, color, interests, manner, meetups, classes: parseClasses(cls) });

export const USERS: User[] = [
  u('u1', '민트초코', '컴퓨터공학과', '#1FA971', ['보드게임', '카페공부', '코딩'], 37.2, 8, ['월 09:00-11:00', '월 14:00-15:30', '수 10:30-12:00', '수 14:00-15:30', '금 10:30-12:00', '금 14:00-16:00']),
  u('u2', '라떼한잔', '디자인학과', '#F5A524', ['산책', '사진', '전시', '카페공부'], 36.9, 5, ['화 09:00-12:00', '화 15:00-16:30', '수 09:00-12:00', '수 15:00-17:00', '목 10:30-12:00', '목 13:30-15:00']),
  u('u3', '코딩하는곰', '소프트웨어학과', '#2F9BF0', ['코딩', '스터디', '게임', '카탄 같은 보드게임'], 36.5, 11, ['월 10:00-11:30', '월 13:30-15:00', '수 10:30-12:00', '수 13:00-14:30', '목 09:00-10:30', '목 13:00-14:30']),
  u('u4', '새벽러닝', '체육학과', '#E5484D', ['운동', '산책', '점심'], 38.0, 14, ['화 10:30-12:00', '화 14:00-15:30', '목 10:30-12:00', '목 14:00-15:30', '금 09:00-11:00', '금 13:00-14:30']),
  u('u5', '영화광하루', '경영학과', '#B24BFF', ['영화', '수다', '전시', '독서'], 36.6, 6, ['월 09:00-10:30', '월 13:00-14:30', '수 09:00-11:00', '수 14:00-15:30', '금 10:30-12:00', '금 13:30-15:00']),
  u('u6', '카탄마스터', '수학과', '#2B2B4D', ['보드게임', '게임', '스터디', '점심'], 37.8, 17, ['수 10:30-12:00', '수 14:00-16:00', '화 09:00-10:30', '화 13:00-14:30']),
  u('u7', '어학당', '영어영문학과', '#FF6B8B', ['언어교환', '독서', '카페공부', '수다'], 36.4, 4, ['월 10:30-12:00', '월 14:00-15:30', '목 10:30-12:00', '목 14:00-15:30', '수 09:00-11:00', '수 14:00-15:30']),
  u('u8', '연필한자루', '건축학과', '#5B4BFF', ['스케치', '전시', '음악', '산책'], 36.7, 3, ['화 10:30-12:00', '화 14:30-16:00', '금 09:00-11:00', '금 13:30-15:00']),
  u('staff', '운영팀', '', '#14142B', ['점심', '보드게임', '산책'], 40, 30, []),
];

export const ME = 'me';

export const ROOMS: Room[] = [
  { id: 'r1', title: '점심 보드게임 같이 해요', activity: '보드게임', day: 2, start: 720, end: 810, place: '학생회관 1층 라운지', cap: 6, members: ['u6', 'u3', 'u1'], hostId: 'u6', topic: '카탄, 뱅 같은 가벼운 게임' },
  { id: 'r2', title: '공강 산책', activity: '산책', day: 2, start: 750, end: 810, place: '중앙 잔디', cap: 4, members: ['u2', 'u4'], hostId: 'u2', topic: '30분만 가볍게 캠퍼스 한 바퀴' },
  { id: 'r3', title: '카페공부 같이 해요', activity: '카페공부', day: 2, start: 720, end: 840, place: '도서관 앞 카페', cap: 6, members: ['u7', 'u3', 'u5', 'u1'], hostId: 'u7', topic: '각자 공부, 마지막 10분은 수다' },
  { id: 'r4', title: '영화 얘기 수다', activity: '수다', day: 3, start: 720, end: 780, place: '학생회관 1층 라운지', cap: 5, members: ['u5'], hostId: 'u5', topic: '요즘 본 영화 추천 릴레이' },
  { id: 'r5', title: '점심 같이 먹어요', activity: '점심', day: 3, start: 690, end: 750, place: '학생식당', cap: 8, members: ['staff'], hostId: 'staff', topic: '운영팀이 먼저 열어 둔 점심 모임', official: true },
  { id: 'r6', title: '금요일 보드게임', activity: '보드게임', day: 4, start: 720, end: 810, place: '학생회관 1층 라운지', cap: 6, members: ['u6', 'u3'], hostId: 'u6', topic: '신입 환영, 룰은 알려드려요' },
  { id: 'r9', title: '공강 수다', activity: '수다', day: 2, start: 660, end: 720, place: '정문 앞 광장', cap: 4, members: ['u5', 'u7'], hostId: 'u5', topic: '잠깐 쉬어가는 수다' },
  { id: 'r11', title: '게임 한판', activity: '게임', day: 2, start: 720, end: 780, place: '공대 로비', cap: 3, members: ['u3', 'u6', 'u4'], hostId: 'u3', topic: '모바일 게임 협동' },
  { id: 'r12', title: '운영팀 산책', activity: '산책', day: 2, start: 750, end: 810, place: '정문 앞 광장', cap: 8, members: ['staff'], hostId: 'staff', topic: '운영팀과 같이 한 바퀴', official: true },
  { id: 'r7', title: '월요일 카페공부', activity: '카페공부', day: 0, start: 720, end: 780, place: '도서관 앞 카페', cap: 6, members: ['me', 'u7', 'u2'], hostId: 'u7', topic: '시험 대비 조용히' },
];

export const SEED_VERSION = 3;

export function initialState(): AppState {
  return {
    v: SEED_VERSION, onboarded: false, verified: false, email: '', school: '단국대학교',
    profile: null, classes: [], wanted: {},
    rooms: structuredClone(ROOMS),
    chats: [
      { id: 'chat-r7', kind: 'room', roomId: 'r7', unread: 0, messages: [
        { id: 'm1', from: 'sys', text: '어학당님이 모임방을 열었어요', time: '12:00' },
        { id: 'm2', from: 'u7', text: '조용히 공부하다가 끝나기 10분 전에 수다 떨어요!', time: '12:02' },
      ] },
    ],
    notifs: [
      { id: 'n1', type: 'reco', title: '새 추천', body: '수 12:00 공강이 맞는 모임 3개가 열렸어요', time: '10:15', read: false, link: '/rooms' },
      { id: 'n2', type: 'request', title: '1:1 요청이 왔어요', body: '민트초코님이 수 12:00–14:00에 보드게임을 같이 하고 싶어 해요', time: '09:50', read: false, userId: 'u1', needsAction: true },
      { id: 'n3', type: 'rate', title: '평가 요청', body: '월요일 카페공부 모임은 어땠나요?', time: '어제', read: false, link: '/review/r7' },
    ],
    blocks: [], reports: [], attendance: {}, rated: [], cancelLog: [],
    manner: 36.8, completed: 3,
    clock: { day: 2, min: 640 },
    settings: { notif: { reco: true, reminder: true, change: true, chat: true, rate: true }, ads: true, quiet: false, premium: false },
    sentRequests: [], sanction: 0,
  };
}
