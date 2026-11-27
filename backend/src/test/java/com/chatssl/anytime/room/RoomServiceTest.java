package com.chatssl.anytime.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatKind;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.Interest;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomStatus;
import com.chatssl.anytime.domain.Slot;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.room.dto.CreateRoomRequest;
import com.chatssl.anytime.room.dto.RoomDetailResponse;
import com.chatssl.anytime.room.dto.RoomResponse;
import com.chatssl.anytime.timetable.dto.SlotItem;

class RoomServiceTest {

    /** 2026-11-25 수요일 10:40 */
    private static final LocalDate WED = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime NOW = WED.atTime(10, 40);

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomMemberRepository roomMembers = mock(RoomMemberRepository.class);
    private final ChatRepository chats = mock(ChatRepository.class);
    private final ChatMemberRepository chatMembers = mock(ChatMemberRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final RoomService service = new RoomService(
        rooms, roomMembers, chats, chatMembers, messages, users, blocks, clock);

    private User me;
    private User hostA;
    private User blockedHost;

    @BeforeEach
    void setUp() {
        me = user(1L, "데모유저", "소프트웨어학과", true);
        me.getWantedSlots().add(new Slot(2, 720, 840)); // 수 12:00-14:00
        me.getWantedSlots().add(new Slot(4, 720, 840)); // 금 12:00-14:00

        hostA = user(2L, "민트초코", "컴퓨터공학과", false);
        hostA.getInterests().add(new Interest("보드게임", true));
        hostA.getInterests().add(new Interest("비공개취미", false));

        blockedHost = user(3L, "차단유저", "경영학과", true);

        when(users.findById(1L)).thenReturn(Optional.of(me));
        when(users.findById(2L)).thenReturn(Optional.of(hostA));
        when(users.findById(3L)).thenReturn(Optional.of(blockedHost));
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(3L));

        when(rooms.save(any(Room.class))).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(100L);
            }
            return r;
        });
        when(chats.save(any(Chat.class))).thenAnswer(inv -> {
            Chat c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(200L);
            }
            return c;
        });
        when(roomMembers.save(any(RoomMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatMembers.save(any(ChatMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 모임_생성_시_방과_채팅방_멤버_시스템메시지를_함께_생성한다() {
        CreateRoomRequest req = new CreateRoomRequest(
            " 점심 보드게임 ", " 보드게임 ", 2, 720, 810, " 학생회관 1층 라운지 ", 4, " 가볍게 한 판 ");

        RoomDetailResponse res = service.create(1L, req);

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.title()).isEqualTo("점심 보드게임");
        assertThat(res.activity()).isEqualTo("보드게임");
        assertThat(res.place()).isEqualTo("학생회관 1층 라운지");
        assertThat(res.topic()).isEqualTo("가볍게 한 판");
        assertThat(res.meetDate()).isEqualTo(WED); // 오늘(수) 12:00 > 10:40
        assertThat(res.status()).isEqualTo(RoomPhase.OPEN);
        assertThat(res.memberCount()).isEqualTo(1);
        assertThat(res.joined()).isTrue();
        assertThat(res.isHost()).isTrue();
        assertThat(res.chatId()).isEqualTo(200L);
        assertThat(res.fit()).isEqualTo(100);
        assertThat(res.slot()).isEqualTo(new SlotItem(2, 720, 810));
        assertThat(res.members()).hasSize(1);
        assertThat(res.members().get(0).nick()).isEqualTo("데모유저");

        ArgumentCaptor<Chat> chatCap = ArgumentCaptor.forClass(Chat.class);
        verify(chats).save(chatCap.capture());
        assertThat(chatCap.getValue().getKind()).isEqualTo(ChatKind.ROOM);
        assertThat(chatCap.getValue().getRoom().getId()).isEqualTo(100L);

        ArgumentCaptor<ChatMember> cmCap = ArgumentCaptor.forClass(ChatMember.class);
        verify(chatMembers).save(cmCap.capture());
        assertThat(cmCap.getValue().getUser().getId()).isEqualTo(1L);
        assertThat(cmCap.getValue().getLastReadAt()).isEqualTo(NOW);

        ArgumentCaptor<Message> msgCap = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(msgCap.capture());
        assertThat(msgCap.getValue().isSystem()).isTrue();
        assertThat(msgCap.getValue().getText()).contains("데모유저");
    }

    @Test
    void 오늘_이미_시작_시간이_지난_요일로_생성하면_다음주_날짜로_잡는다() {
        CreateRoomRequest req = new CreateRoomRequest(
            "아침 산책", "산책", 2, 540, 600, "중앙 잔디", 4, null);

        RoomDetailResponse res = service.create(1L, req);

        assertThat(res.meetDate()).isEqualTo(WED.plusWeeks(1));
        assertThat(res.status()).isEqualTo(RoomPhase.OPEN);
    }

    @Test
    void 정지된_사용자는_모임을_만들_수_없다() {
        me.setSanction(2);
        CreateRoomRequest req = new CreateRoomRequest("제목", "산책", 2, 720, 780, "중앙 잔디", 4, null);

        assertCode(() -> service.create(1L, req), ErrorCode.USER_SUSPENDED);
    }

    @Test
    void 잘못된_시간이나_정원이면_INVALID_INPUT() {
        assertCode(() -> service.create(1L,
            new CreateRoomRequest("제목", "산책", 2, 780, 720, "중앙 잔디", 4, null)), ErrorCode.INVALID_INPUT);
        assertCode(() -> service.create(1L,
            new CreateRoomRequest("제목", "산책", 5, 720, 780, "중앙 잔디", 4, null)), ErrorCode.INVALID_INPUT);
        assertCode(() -> service.create(1L,
            new CreateRoomRequest("제목", "산책", 2, 720, 780, "중앙 잔디", 1, null)), ErrorCode.INVALID_INPUT);
    }

    @Test
    void 목록_조회는_차단한_방장을_제외하고_동적_상태와_필터를_적용한다() {
        Room r1 = room(10L, hostA, "수 12시 보드게임", "보드게임", 2, 720, 810, WED, "학생회관", 4);
        Room r2 = room(11L, hostA, "수 11시 산책", "산책", 2, 660, 720, WED, "중앙 잔디", 2);
        Room r3 = room(12L, blockedHost, "차단 유저 모임", "보드게임", 2, 720, 810, WED, "학생회관", 4);
        Room r4 = room(13L, hostA, "금 12시 보드게임", "보드게임", 4, 720, 810, WED.plusDays(2), "학생회관", 6);

        when(rooms.findUpcoming(RoomStatus.OPEN, WED)).thenReturn(List.of(r4, r2, r1, r3));
        when(roomMembers.findByRoomIdInAndLeftAtIsNull(anyCollection())).thenReturn(List.of(
            new RoomMember(r1, hostA),
            new RoomMember(r2, hostA),
            new RoomMember(r2, me),
            new RoomMember(r3, blockedHost),
            new RoomMember(r4, hostA)
        ));

        List<RoomResponse> all = service.list(1L, null, null, null, null, null, null, null, null, null);
        // r3(차단 방장)는 빠지고, 날짜·시작 순 정렬: r2(수 11:00) -> r1(수 12:00) -> r4(금 12:00)
        assertThat(all).extracting(RoomResponse::id).containsExactly(11L, 10L, 13L);
        assertThat(all.get(0).status()).isEqualTo(RoomPhase.FULL); // r2: 2/2명
        assertThat(all.get(0).joined()).isTrue();
        assertThat(all.get(1).status()).isEqualTo(RoomPhase.OPEN); // r1: 수 12:00 (10:40 기준 open)
        assertThat(all.get(1).fit()).isEqualTo(100);

        // 요일 + 활동 + 공강 겹침 필터 (fitOnly=true여도 내가 참여한 r2는 포함)
        List<RoomResponse> wedBoard = service.list(
            1L, 2, List.of("보드게임"), null, null, null, null, null, true, null);
        assertThat(wedBoard).extracting(RoomResponse::id).containsExactly(10L);

        // 내 참여 모임 필터
        List<RoomResponse> mine = service.list(1L, null, null, null, null, null, null, null, null, true);
        assertThat(mine).extracting(RoomResponse::id).containsExactly(11L);

        // 지정 시간대 겹침 필터 (11:30~12:30 -> r2(11:00-12:00)와 r1(12:00-13:30) 둘 다 겹침)
        List<RoomResponse> slotFiltered = service.list(
            1L, 2, null, null, null, null, 690, 750, null, null);
        assertThat(slotFiltered).extracting(RoomResponse::id).containsExactly(11L, 10L);
    }

    @Test
    void 상세_조회는_동적_상태와_마스킹된_참여자_목록을_주며_차단_사용자를_뺀다() {
        Room r = room(10L, hostA, "수 11시반 모임", "보드게임", 2, 690, 780, WED, "학생회관", 4);
        Chat chat = Chat.forRoom(r);
        chat.setId(55L);

        when(rooms.findWithHostById(10L)).thenReturn(Optional.of(r));
        when(chats.findByRoomId(10L)).thenReturn(Optional.of(chat));
        when(roomMembers.findActiveWithUserByRoomId(10L)).thenReturn(List.of(
            new RoomMember(r, hostA),
            new RoomMember(r, blockedHost)
        ));

        RoomDetailResponse res = service.detail(1L, 10L);

        // 수 11:30 시작이고 현재 10:40 → 50분 전이므로 SOON
        assertThat(res.status()).isEqualTo(RoomPhase.SOON);
        assertThat(res.chatId()).isEqualTo(55L);
        assertThat(res.isHost()).isFalse();
        assertThat(res.joined()).isFalse();
        // 실제 참여 인원은 2명이지만, 차단된 사용자는 참여자 목록에서 숨긴다
        assertThat(res.memberCount()).isEqualTo(2);
        assertThat(res.members()).hasSize(1);
        // 비공개 학과(null)와 비공개 관심사는 마스킹된다
        assertThat(res.members().get(0).nick()).isEqualTo("민트초코");
        assertThat(res.members().get(0).dept()).isNull();
        assertThat(res.members().get(0).interests()).containsExactly("보드게임");
        // 공강 겹침: 수 12:00-14:00 vs 11:30-13:00 → 60/90 = 67%
        assertThat(res.fit()).isEqualTo(67);
        assertThat(res.slot()).isEqualTo(new SlotItem(2, 720, 780));
    }

    @Test
    void 차단한_방장의_모임_상세는_미참여_시_404() {
        Room r = room(12L, blockedHost, "차단 유저 모임", "보드게임", 2, 720, 810, WED, "학생회관", 4);
        when(rooms.findWithHostById(12L)).thenReturn(Optional.of(r));
        when(roomMembers.findActiveWithUserByRoomId(12L)).thenReturn(List.of(new RoomMember(r, blockedHost)));

        assertCode(() -> service.detail(1L, 12L), ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void 없는_모임_상세는_404() {
        when(rooms.findWithHostById(999L)).thenReturn(Optional.empty());
        assertCode(() -> service.detail(1L, 999L), ErrorCode.ROOM_NOT_FOUND);
    }

    private static User user(Long id, String nick, String dept, boolean showDept) {
        User u = new User("hash-" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setDept(dept);
        u.setShowDept(showDept);
        u.setManner(new BigDecimal("36.8"));
        return u;
    }

    private static Room room(Long id, User host, String title, String activity, int day, int start, int end,
                             LocalDate meetDate, String place, int cap) {
        Room r = new Room(host, title, activity, day, start, end, meetDate, place, cap, null);
        r.setId(id);
        return r;
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo(code);
    }
}
