import { useEffect, useState } from 'react';
import { Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useApp } from './store';
import { Icon, Toasts } from './components/ui';
import { EmergencySheet } from './components/safety';
import { Welcome } from './screens/Welcome';
import { AuthEmail, AuthCode, AuthDone } from './screens/Auth';
import { ProfileSetup } from './screens/ProfileSetup';
import { TimetableSetup, TimetableEdit } from './screens/Timetable';
import { Home } from './screens/Home';
import { Rooms, RoomDetail, RoomCreate } from './screens/Rooms';
import { PersonDetail } from './screens/Person';
import { ChatList, ChatPage } from './screens/Chat';
import { Me, MannerRules, Privacy, Blocked, Sanction } from './screens/Me';
import { Notifications, NotifSettings } from './screens/Notifications';
import { Review } from './screens/Review';
import { Premium, States } from './screens/Extras';

const TABS = [
  { to: '/home', label: '홈', icon: 'home' },
  { to: '/rooms', label: '모임', icon: 'grid' },
  { to: '/chats', label: '채팅', icon: 'chat' },
  { to: '/me', label: '마이', icon: 'user' },
];

function Shell() {
  const { s } = useApp(); const loc = useLocation();
  const [sos, setSos] = useState(false);
  if (!s.onboarded) return <Navigate to="/welcome" replace />;
  if (!s.verified) return <Navigate to="/auth" replace />;
  if (!s.profile) return <Navigate to="/profile" replace />;
  if (s.classes.length === 0) return <Navigate to="/timetable/setup" replace />;
  const unreadN = s.notifs.filter((n) => !n.read).length;
  const unreadC = s.chats.reduce((a, c) => a + c.unread, 0);
  const showTabs = TABS.some((t) => loc.pathname === t.to);
  return (
    <div className="shell">
      <nav className="side" aria-label="주 메뉴">
        <div className="logo"><b>A</b>nytime</div>
        {TABS.map((t) => (
          <NavLink key={t.to} to={t.to} className={({ isActive }) => (isActive ? 'on' : '')}>
            <Icon n={t.icon} />{t.label === '모임' ? '열린 모임' : t.label === '마이' ? '마이페이지' : t.label}
            {t.to === '/chats' && unreadC > 0 && <i className="dot" />}
          </NavLink>
        ))}
        <NavLink to="/notifications" className={({ isActive }) => (isActive ? 'on' : '')}><Icon n="bell" />알림{unreadN > 0 && <i className="dot" />}</NavLink>
        <div className="sp" />
        <button className="sos" style={{ height: 44, borderRadius: 12 }} onClick={() => setSos(true)}>긴급 신고</button>
        <NavLink to="/me/privacy"><Icon n="lock" />공개 범위</NavLink>
      </nav>
      <main className={`main ${showTabs ? "tabs" : ""}`}><Outlet /></main>
      {showTabs && (
        <nav className="tabbar" aria-label="하단 탭">
          {TABS.map((t) => (
            <NavLink key={t.to} to={t.to} className={({ isActive }) => (isActive ? 'on' : '')}>
              <Icon n={t.icon} />{t.label}{t.to === '/chats' && unreadC > 0 && <i className="dot" />}
            </NavLink>
          ))}
        </nav>
      )}
      <EmergencySheet open={sos} onClose={() => setSos(false)} candidates={[]} />
    </div>
  );
}

function ScrollTop() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  return null;
}

export default function App() {
  return (
    <>
      <ScrollTop />
      <Routes>
        <Route path="/welcome" element={<Welcome />} />
        <Route path="/auth" element={<AuthEmail />} />
        <Route path="/auth/code" element={<AuthCode />} />
        <Route path="/auth/done" element={<AuthDone />} />
        <Route path="/profile" element={<ProfileSetup />} />
        <Route path="/timetable/setup" element={<TimetableSetup />} />
        <Route element={<Shell />}>
          <Route path="/" element={<Navigate to="/home" replace />} />
          <Route path="/home" element={<Home />} />
          <Route path="/rooms" element={<Rooms />} />
          <Route path="/rooms/new" element={<RoomCreate />} />
          <Route path="/rooms/:id" element={<RoomDetail />} />
          <Route path="/person/:id" element={<PersonDetail />} />
          <Route path="/chats" element={<ChatList />} />
          <Route path="/chat/:id" element={<ChatPage />} />
          <Route path="/me" element={<Me />} />
          <Route path="/me/manner" element={<MannerRules />} />
          <Route path="/me/privacy" element={<Privacy />} />
          <Route path="/me/blocked" element={<Blocked />} />
          <Route path="/me/timetable" element={<TimetableEdit />} />
          <Route path="/me/sanction" element={<Sanction />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/notifications/settings" element={<NotifSettings />} />
          <Route path="/review/:roomId" element={<Review />} />
          <Route path="/premium" element={<Premium />} />
          <Route path="/states" element={<States />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <Toasts />
    </>
  );
}
