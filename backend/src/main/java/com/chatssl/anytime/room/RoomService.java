package com.chatssl.anytime.room;

import java.math.BigDecimal;
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
import com.chatssl.anytime.domain.CancelLog;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.ChatMemberId;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomMemberId;
import com.chatssl.anytime.domain.RoomStatus;
import com.chatssl.anytime.domain.Slot;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.NotificationService;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.CancelLogRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.room.dto.CreateRoomRequest;
import com.chatssl.anytime.room.dto.LeaveRoomRequest;
import com.chatssl.anytime.room.dto.RoomDetailResponse;
import com.chatssl.anytime.room.dto.RoomResponse;
import com.chatssl.anytime.timetable.dto.SlotItem;
import com.chatssl.anytime.user.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

/** 11~12단계: 모임 생성·목록·상세, 합류·취소·방장 위임 및 동적 상태 계산 */
@Service
@RequiredArgsConstructor
public class RoomService {

    static final int MAX_DAY = 4;
    static final int MINUTES_PER_DAY = 1440;
    static final int MIN_CAP = 2;
    static final int MAX_CAP = 20;
    static final int SANCTION_SUSPENDED = 2;
    static final int LATE_CANCEL_MINUTES = 60;
    static final int LEAVE_REASON_MAX = 100;
    static final BigDecimal LATE_CANCEL_PENALTY = new BigDecimal("0.5");
    static final BigDecimal MIN_MANNER = new BigDecimal("30.0");
    static final String PENALTY_REASON = "그냥 못 가요";
    static final String DEFAULT_LEAVE_REASON = "단순 취소";

    private static final Comparator<SlotItem> SLOT_ORDER =
        Comparator.comparing(SlotItem::day).thenComparing(SlotItem::start).thenComparing(SlotItem::end);
    private static final Comparator<RoomMember> MEMBER_JOIN_ORDER =
        Comparator.comparing(RoomMember::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(m -> m.getUser().getId());

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final CancelLogRepository cancelLogRepository;
    private final NotificationService notificationService;
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
        checkOverlappingActiveRoom(userId, meetDate, req.start(), req.end(), null);

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
        boolean joined = activeMembers.stream().anyMatch(m -> m.getUser().getId().equals(userId));

        if (!joined && hidden.contains(room.getHost().getId())) {
            throw new ApiException(ErrorCode.ROOM_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Long chatId = chatRepository.findByRoomId(roomId).map(Chat::getId).orElse(null);
        return buildDetailResponse(room, me, activeMembers, hidden, chatId, now);
    }

    /**
     * 모임 합류 (12단계).
     * 비관적 락으로 정원 초과를 막고, 나갔던 참여자는 leftAt=null로 되돌린다.
     */
    @Transactional
    public RoomDetailResponse join(Long userId, Long roomId) {
        User user = findUser(userId);
        if (user.getSanction() >= SANCTION_SUSPENDED) {
            throw new ApiException(ErrorCode.USER_SUSPENDED);
        }

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));

        User host = room.getHost();
        if (hidden.contains(host.getId())) {
            throw new ApiException(ErrorCode.ROOM_NOT_FOUND);
        }

        List<RoomMember> activeMembers = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        boolean blockedMember = activeMembers.stream()
            .anyMatch(m -> !m.getUser().getId().equals(host.getId()) && hidden.contains(m.getUser().getId()));
        if (blockedMember) {
            throw new ApiException(ErrorCode.FORBIDDEN, "차단 관계인 참여자가 있어 합류할 수 없어요");
        }

        RoomMember existing = roomMemberRepository.findById(new RoomMemberId(roomId, userId)).orElse(null);
        boolean alreadyActive = (existing != null && existing.isActive())
            || activeMembers.stream().anyMatch(m -> m.getUser().getId().equals(userId));
        if (alreadyActive) {
            throw new ApiException(ErrorCode.ROOM_ALREADY_JOINED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int currentCount = activeMembers.size();
        RoomPhase phase = RoomPhase.of(room, currentCount, now);
        if (!phase.joinable()) {
            throw phaseError(phase);
        }
        if (currentCount >= room.getCap()) {
            throw new ApiException(ErrorCode.ROOM_FULL);
        }
        checkOverlappingActiveRoom(userId, room.getMeetDate(), room.getStartMin(), room.getEndMin(), roomId);

        RoomMember savedMember;
        if (existing != null) {
            existing.setLeftAt(null);
            existing.setLeaveReason(null);
            existing.setJoinedAt(now);
            savedMember = roomMemberRepository.save(existing);
        } else {
            RoomMember created = new RoomMember(room, user);
            created.setJoinedAt(now);
            savedMember = roomMemberRepository.save(created);
        }

        Chat chat = chatRepository.findByRoomId(roomId).orElse(null);
        Long chatId = chat == null ? null : chat.getId();
        if (chat != null) {
            ChatMemberId cmId = new ChatMemberId(chatId, userId);
            if (!chatMemberRepository.existsById(cmId)) {
                ChatMember cm = new ChatMember(chat, user);
                cm.setLastReadAt(now);
                chatMemberRepository.save(cm);
            }
            messageRepository.save(new Message(chat, null, user.getNick() + "님이 합류했어요"));
        }

        notificationService.notify(
            host,
            NotifType.JOIN,
            "새 참여자가 합류했어요",
            user.getNick() + "님이 '" + room.getTitle() + "' 모임에 합류했어요",
            "/rooms/" + roomId,
            user,
            false
        );

        List<RoomMember> updatedMembers = new ArrayList<>(activeMembers);
        updatedMembers.add(savedMember);
        return buildDetailResponse(room, user, updatedMembers, hidden, chatId, now);
    }

    /**
     * 모임 참여 취소 (12단계).
     * 시작 전 60분 이내에 "그냥 못 가요"로 취소하면 매너 점수 0.5 차감(하한 30.0)과 CancelLog(0.5)를 남긴다.
     * 방장이 나가면 가장 먼저 합류한 남은 참여자에게 방장을 넘기고, 남은 참여자가 없으면 모임을 취소한다.
     */
    @Transactional
    public RoomDetailResponse leave(Long userId, Long roomId, LeaveRoomRequest req) {
        User user = findUser(userId);
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));

        List<RoomMember> activeMembers = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        RoomMember leaving = activeMembers.stream()
            .filter(m -> m.getUser().getId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_MEMBER));

        LocalDateTime now = LocalDateTime.now(clock);
        RoomPhase phase = RoomPhase.of(room, activeMembers.size(), now);
        if (phase == RoomPhase.ENDED) {
            throw new ApiException(ErrorCode.ROOM_ENDED);
        }
        if (phase == RoomPhase.CANCELLED) {
            throw new ApiException(ErrorCode.ROOM_CANCELLED);
        }

        LocalDateTime startDateTime = room.getMeetDate().atStartOfDay().plusMinutes(room.getStartMin());
        boolean within60BeforeStart = now.isBefore(startDateTime)
            && !now.isBefore(startDateTime.minusMinutes(LATE_CANCEL_MINUTES));

        String rawReason = req == null ? null : req.reason();
        String trimmedReason = rawReason == null ? "" : rawReason.trim();
        if (trimmedReason.length() > LEAVE_REASON_MAX) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "사유는 100자까지 쓸 수 있어요");
        }
        String reason;
        if (within60BeforeStart) {
            if (trimmedReason.isEmpty()) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "취소 사유를 골라 주세요");
            }
            reason = trimmedReason;
        } else {
            reason = trimmedReason.isEmpty() ? DEFAULT_LEAVE_REASON : trimmedReason;
        }

        boolean penalize = within60BeforeStart && PENALTY_REASON.equals(reason);
        BigDecimal penalty = penalize ? LATE_CANCEL_PENALTY : BigDecimal.ZERO;
        if (penalize) {
            user.setManner(user.getManner().subtract(LATE_CANCEL_PENALTY).max(MIN_MANNER));
        }
        cancelLogRepository.save(new CancelLog(user, room, reason, penalty));

        leaving.setLeftAt(now);
        leaving.setLeaveReason(reason);
        roomMemberRepository.save(leaving);

        Chat chat = chatRepository.findByRoomId(roomId).orElse(null);
        Long chatId = chat == null ? null : chat.getId();
        if (chat != null) {
            chatMemberRepository.findById(new ChatMemberId(chatId, userId))
                .ifPresent(chatMemberRepository::delete);
            messageRepository.save(new Message(chat, null, user.getNick() + "님이 참여를 취소했어요"));
        }

        List<RoomMember> remaining = activeMembers.stream()
            .filter(m -> !m.getUser().getId().equals(userId))
            .sorted(MEMBER_JOIN_ORDER)
            .toList();

        if (room.getHost().getId().equals(userId)) {
            if (remaining.isEmpty()) {
                room.setStatus(RoomStatus.CANCELED);
            } else {
                User nextHost = remaining.get(0).getUser();
                room.setHost(nextHost);
                if (chat != null) {
                    messageRepository.save(new Message(chat, null, nextHost.getNick() + "님이 새 방장이 되었어요"));
                }
                notificationService.notify(
                    nextHost,
                    NotifType.CHANGE,
                    "방장이 되었어요",
                    "'" + room.getTitle() + "' 모임의 새 방장이 되었어요",
                    "/rooms/" + roomId,
                    user,
                    false
                );
            }
        }

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        return buildDetailResponse(room, user, remaining, hidden, chatId, now);
    }

    /**
     * 방장 모임 취소 (12단계).
     * 방장만 호출할 수 있으며, 남은 참여자들에게 취소 알림을 보낸다.
     */
    @Transactional
    public RoomDetailResponse cancel(Long userId, Long roomId) {
        User host = findUser(userId);
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.getHost().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        List<RoomMember> activeMembers = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        LocalDateTime now = LocalDateTime.now(clock);
        RoomPhase phase = RoomPhase.of(room, activeMembers.size(), now);
        if (room.getStatus() == RoomStatus.CANCELED || phase == RoomPhase.CANCELLED) {
            throw new ApiException(ErrorCode.ROOM_CANCELLED);
        }
        if (room.getStatus() == RoomStatus.DONE || phase == RoomPhase.ENDED) {
            throw new ApiException(ErrorCode.ROOM_ENDED);
        }

        room.setStatus(RoomStatus.CANCELED);

        Chat chat = chatRepository.findByRoomId(roomId).orElse(null);
        Long chatId = chat == null ? null : chat.getId();
        if (chat != null) {
            messageRepository.save(new Message(chat, null, host.getNick() + "님이 모임을 취소했어요"));
        }

        for (RoomMember member : activeMembers) {
            User target = member.getUser();
            if (!target.getId().equals(userId)) {
                notificationService.notify(
                    target,
                    NotifType.CANCEL,
                    "모임이 취소됐어요",
                    "'" + room.getTitle() + "' 모임이 방장에 의해 취소됐어요",
                    "/rooms/" + roomId,
                    host,
                    false
                );
            }
        }

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        return buildDetailResponse(room, host, activeMembers, hidden, chatId, now);
    }

    /**
     * 방장 위임 (12단계).
     * 현재 참여 중이고 정지되지 않은 다른 참여자에게 방장을 넘긴다.
     */
    @Transactional
    public RoomDetailResponse transferHost(Long userId, Long roomId, Long targetUserId) {
        User host = findUser(userId);
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.getHost().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        List<RoomMember> activeMembers = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        LocalDateTime now = LocalDateTime.now(clock);
        RoomPhase phase = RoomPhase.of(room, activeMembers.size(), now);
        if (room.getStatus() == RoomStatus.CANCELED || phase == RoomPhase.CANCELLED) {
            throw new ApiException(ErrorCode.ROOM_CANCELLED);
        }
        if (room.getStatus() == RoomStatus.DONE || phase == RoomPhase.ENDED) {
            throw new ApiException(ErrorCode.ROOM_ENDED);
        }

        if (targetUserId == null || targetUserId.equals(userId)) {
            throw new ApiException(ErrorCode.ROOM_HOST_TRANSFER_INVALID, "다른 참여자에게만 방장을 넘길 수 있어요");
        }
        User target = activeMembers.stream()
            .map(RoomMember::getUser)
            .filter(u -> u.getId().equals(targetUserId))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_HOST_TRANSFER_INVALID, "현재 참여 중인 사용자에게만 방장을 넘길 수 있어요"));
        if (target.getSanction() >= SANCTION_SUSPENDED) {
            throw new ApiException(ErrorCode.ROOM_HOST_TRANSFER_INVALID, "이용이 정지된 사용자에게는 방장을 넘길 수 없어요");
        }

        room.setHost(target);

        Chat chat = chatRepository.findByRoomId(roomId).orElse(null);
        Long chatId = chat == null ? null : chat.getId();
        if (chat != null) {
            messageRepository.save(new Message(chat, null, host.getNick() + "님이 " + target.getNick() + "님에게 방장을 넘겼어요"));
        }

        notificationService.notify(
            target,
            NotifType.CHANGE,
            "방장이 되었어요",
            "'" + room.getTitle() + "' 모임의 새 방장이 되었어요",
            "/rooms/" + roomId,
            host,
            false
        );

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        return buildDetailResponse(room, host, activeMembers, hidden, chatId, now);
    }

    private RoomDetailResponse buildDetailResponse(
        Room room,
        User viewer,
        List<RoomMember> activeMembers,
        Set<Long> hidden,
        Long chatId,
        LocalDateTime now
    ) {
        int memberCount = activeMembers.size();
        boolean joined = activeMembers.stream().anyMatch(m -> m.getUser().getId().equals(viewer.getId()));
        RoomPhase phase = RoomPhase.of(room, memberCount, now);
        SlotItem best = bestOverlap(room, sortedWanted(viewer));
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
            room.getHost().getId().equals(viewer.getId()),
            chatId,
            fit,
            best,
            visibleMembers
        );
    }

    private static ApiException phaseError(RoomPhase phase) {
        return switch (phase) {
            case CANCELLED -> new ApiException(ErrorCode.ROOM_CANCELLED);
            case ENDED -> new ApiException(ErrorCode.ROOM_ENDED);
            case FULL -> new ApiException(ErrorCode.ROOM_FULL);
            case CLOSED -> new ApiException(ErrorCode.ROOM_CLOSED);
            default -> new ApiException(ErrorCode.CONFLICT);
        };
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private void checkOverlappingActiveRoom(Long userId, LocalDate meetDate, int startMin, int endMin, Long excludeRoomId) {
        List<RoomMember> overlaps = roomMemberRepository.findActiveOverlapping(
            userId, RoomStatus.OPEN, meetDate, startMin, endMin);
        for (RoomMember m : overlaps) {
            Room other = m.getRoom();
            if (excludeRoomId != null && other.getId().equals(excludeRoomId)) {
                continue;
            }
            throw new ApiException(
                ErrorCode.CONFLICT,
                "이미 참여 중인 모임(" + other.getTitle() + ")과 시간이 겹쳐요"
            );
        }
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
