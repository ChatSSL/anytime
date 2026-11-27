package com.chatssl.anytime.room;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
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
import com.chatssl.anytime.user.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

/** 11단계: 모임 생성·목록·상세 및 동적 상태 계산 */
@Service
@RequiredArgsConstructor
public class RoomService {

    static final int MAX_DAY = 4;
    static final int MINUTES_PER_DAY = 1440;
    static final int MIN_CAP = 2;
    static final int MAX_CAP = 20;
    static final int SANCTION_SUSPENDED = 2;

    private static final Comparator<SlotItem> SLOT_ORDER =
        Comparator.comparing(SlotItem::day).thenComparing(SlotItem::start).thenComparing(SlotItem::end);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final Clock clock;

    /**
     * 모임방 생성.
     * Room, 방장 RoomMember, 모임방 Chat(Chat.forRoom), 방장 ChatMember, 시작 시스템 메시지를 원자적으로 저장한다.
     */
    @Transactional
    public RoomDetailResponse create(Long userId, CreateRoomRequest req) {
        User host = findUser(userId);
        if (host.getSanction() >= SANCTION_SUSPENDED) {
            throw new ApiException(ErrorCode.USER_SUSPENDED);
        }

        String title = cleanRequired(req.title(), 50, "제목을 입력해 주세요", "제목은 50자까지 쓸 수 있어요");
        String activity = cleanRequired(req.activity(), 20, "활동을 골라 주세요", "활동은 20자까지 쓸 수 있어요");
        String place = cleanRequired(req.place(), 50, "장소를 입력해 주세요", "장소는 50자까지 쓸 수 있어요");
        String topic = cleanOptional(req.topic(), 200, "소개는 200자까지 쓸 수 있어요");
        checkRange(req.day(), req.start(), req.end());
        if (req.cap() < MIN_CAP || req.cap() > MAX_CAP) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "정원은 2~8명 사이로 정해 주세요");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate meetDate = nextMeetDate(req.day(), req.start(), now);

        Room room = roomRepository.save(
            new Room(host, title, activity, req.day(), req.start(), req.end(), meetDate, place, req.cap(), topic));
        roomMemberRepository.save(new RoomMember(room, host));

        Chat chat = chatRepository.save(Chat.forRoom(room));
        ChatMember chatMember = new ChatMember(chat, host);
        chatMember.setLastReadAt(now);
        chatMemberRepository.save(chatMember);
        messageRepository.save(new Message(chat, null, host.getNick() + "님이 모임방을 열었어요"));

        RoomPhase phase = RoomPhase.of(room, 1, now);
        SlotItem best = bestOverlap(room, sortedWanted(host));
        int fit = best == null ? 0 : fitPercent(room, best);

        return RoomDetailResponse.of(
            room, 1, phase, true, true, chat.getId(), fit, best, List.of(UserProfileResponse.of(host)));
    }

    /**
     * 모임 목록 조회.
     * 요일, 활동, 장소, 시작 시각, 최소 정원, 내 공강 겹침(fitOnly), 지정 시간대(start/end), 내 참여 모임(mine)으로 필터링한다.
     * 차단 관계인 사용자가 방장인 모임은 제외한다.
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> list(
        Long userId,
        Integer day,
        List<String> activities,
        List<String> places,
        Integer afterMin,
        Integer minCap,
        Integer start,
        Integer end,
        Boolean fitOnly,
        Boolean mine
    ) {
        if (day != null && (day < 0 || day > MAX_DAY)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "요일은 월~금 중에서 골라 주세요");
        }
        if (start != null && (start < 0 || start >= MINUTES_PER_DAY)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "시간은 00:00~24:00 사이로 정해 주세요");
        }
        if (end != null && (end <= 0 || end > MINUTES_PER_DAY)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "시간은 00:00~24:00 사이로 정해 주세요");
        }
        if (start != null && end != null && start >= end) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "종료 시간이 시작보다 늦어야 해요");
        }

        User me = findUser(userId);
        List<SlotItem> wanted = sortedWanted(me);
        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        LocalDateTime now = LocalDateTime.now(clock);

        Set<String> actSet = normalizeFilter(activities);
        Set<String> placeSet = normalizeFilter(places);

        List<Room> rooms = roomRepository.findUpcoming(RoomStatus.OPEN, now.toLocalDate());
        Map<Long, Set<Long>> members = activeMembers(rooms);

        List<RoomResponse> out = new ArrayList<>();
        for (Room r : rooms) {
            if (hidden.contains(r.getHost().getId())) {
                continue;
            }
            if (day != null && !r.getDay().equals(day)) {
                continue;
            }
            if (!actSet.isEmpty() && !actSet.contains(r.getActivity())) {
                continue;
            }
            if (!placeSet.isEmpty() && !placeSet.contains(r.getPlace())) {
                continue;
            }
            if (afterMin != null && r.getStartMin() < afterMin) {
                continue;
            }
            if (minCap != null && r.getCap() < minCap) {
                continue;
            }
            if (start != null && r.getEndMin() <= start) {
                continue;
            }
            if (end != null && r.getStartMin() >= end) {
                continue;
            }

            Set<Long> memberIds = members.getOrDefault(r.getId(), Set.of());
            boolean joined = memberIds.contains(userId);
            if (Boolean.TRUE.equals(mine) && !joined) {
                continue;
            }

            SlotItem best = bestOverlap(r, wanted);
            int fit = best == null ? 0 : fitPercent(r, best);
            if (Boolean.TRUE.equals(fitOnly) && fit == 0 && !joined) {
                continue;
            }

            RoomPhase phase = RoomPhase.of(r, memberIds.size(), now);
            out.add(RoomResponse.of(r, memberIds.size(), phase, joined, fit, best));
        }

        out.sort(Comparator.comparing(RoomResponse::meetDate)
            .thenComparingInt(RoomResponse::start)
            .thenComparing(RoomResponse::id));
        return out;
    }

    /**
     * 모임 상세 조회.
     * 동적 상태(RoomPhase)와 마스킹된 참여자 목록(차단 관계 제외)을 반환한다.
     */
    @Transactional(readOnly = true)
    public RoomDetailResponse detail(Long userId, Long roomId) {
        User me = findUser(userId);
        Room room = roomRepository.findWithHostById(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        List<RoomMember> activeMembers = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        int memberCount = activeMembers.size();
        boolean joined = activeMembers.stream().anyMatch(m -> m.getUser().getId().equals(userId));

        if (!joined && hidden.contains(room.getHost().getId())) {
            throw new ApiException(ErrorCode.ROOM_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        RoomPhase phase = RoomPhase.of(room, memberCount, now);
        Long chatId = chatRepository.findByRoomId(roomId).map(Chat::getId).orElse(null);
        SlotItem best = bestOverlap(room, sortedWanted(me));
        int fit = best == null ? 0 : fitPercent(room, best);

        List<UserProfileResponse> visibleMembers = activeMembers.stream()
            .map(RoomMember::getUser)
            .filter(u -> !hidden.contains(u.getId()))
            .map(UserProfileResponse::of)
            .toList();

        return RoomDetailResponse.of(
            room,
            memberCount,
            phase,
            joined,
            room.getHost().getId().equals(userId),
            chatId,
            fit,
            best,
            visibleMembers
        );
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private Map<Long, Set<Long>> activeMembers(List<Room> rooms) {
        Map<Long, Set<Long>> out = new HashMap<>();
        if (rooms.isEmpty()) {
            return out;
        }
        List<Long> ids = rooms.stream().map(Room::getId).toList();
        for (RoomMember m : roomMemberRepository.findByRoomIdInAndLeftAtIsNull(ids)) {
            out.computeIfAbsent(m.getId().getRoomId(), k -> new HashSet<>()).add(m.getId().getUserId());
        }
        return out;
    }

    private static LocalDate nextMeetDate(int day, int startMin, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        int todayDay = today.getDayOfWeek().getValue() - 1;
        LocalDate candidate = today.plusDays((day - todayDay + 7) % 7);
        if (candidate.equals(today) && !candidate.atStartOfDay().plusMinutes(startMin).isAfter(now)) {
            return candidate.plusWeeks(1);
        }
        return candidate;
    }

    private static List<SlotItem> sortedWanted(User user) {
        return user.getWantedSlots().stream().map(SlotItem::from).sorted(SLOT_ORDER).toList();
    }

    private static SlotItem bestOverlap(Room r, List<SlotItem> wanted) {
        SlotItem best = null;
        int bestLen = 0;
        for (SlotItem f : wanted) {
            Slot slot = f.toSlot();
            if (!slot.overlaps(r.getDay(), r.getStartMin(), r.getEndMin())) {
                continue;
            }
            int s = Math.max(f.start(), r.getStartMin());
            int e = Math.min(f.end(), r.getEndMin());
            if (e - s > bestLen) {
                bestLen = e - s;
                best = new SlotItem(r.getDay(), s, e);
            }
        }
        return best;
    }

    private static int fitPercent(Room r, SlotItem overlap) {
        int dur = r.getEndMin() - r.getStartMin();
        return (int) Math.round(((double) (overlap.end() - overlap.start()) / dur) * 100);
    }

    private static void checkRange(int day, int start, int end) {
        if (day < 0 || day > MAX_DAY) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "요일은 월~금 중에서 골라 주세요");
        }
        if (start < 0 || end > MINUTES_PER_DAY) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "시간은 00:00~24:00 사이로 정해 주세요");
        }
        if (start >= end) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "종료 시간이 시작보다 늦어야 해요");
        }
    }

    private static String cleanRequired(String raw, int maxLen, String emptyMsg, String tooLongMsg) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, emptyMsg);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new ApiException(ErrorCode.INVALID_INPUT, tooLongMsg);
        }
        return trimmed;
    }

    private static String cleanOptional(String raw, int maxLen, String tooLongMsg) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new ApiException(ErrorCode.INVALID_INPUT, tooLongMsg);
        }
        return trimmed;
    }

    private static Set<String> normalizeFilter(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim());
            }
        }
        return out;
    }
}
