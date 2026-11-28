package com.chatssl.anytime.roomchange;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.CancelLog;
import com.chatssl.anytime.domain.ChangeStatus;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMemberId;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.NotifResolution;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomChange;
import com.chatssl.anytime.domain.RoomChangeVote;
import com.chatssl.anytime.domain.RoomChangeVoteId;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.NotificationService;
import com.chatssl.anytime.repository.CancelLogRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.RoomChangeRepository;
import com.chatssl.anytime.repository.RoomChangeVoteRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.roomchange.dto.ChangeResponse;
import com.chatssl.anytime.roomchange.dto.ProposeChangeRequest;

import lombok.RequiredArgsConstructor;

/**
 * 15단계: 방장의 일정 변경 제안과 참여자 투표.
 *
 * 1) 방장이 새 요일·시간(·장소)을 제안하면 PENDING으로 저장하고, 다른 참여자에게 '답변 필요' 알림을 보낸다.
 * 2) 참여자는 '그래도 참여할게요'(accept=true) 또는 '이번엔 빠질게요'(accept=false)로 답한다.
 *    빠지면 모임에서 나간다. 방장 사정으로 바뀐 것이므로 매너 점수는 깎지 않고 cancel_log에 0점으로만 남긴다.
 * 3) 답해야 하는 사람이 모두 답하면 일정을 확정(APPLIED)하고 rooms에 반영한다.
 *    답해야 하는 사람 = 지금 참여 중인 사람 중 방장과 제안 뒤에 들어온 사람을 뺀 사람.
 *    다른 참여자가 없으면 제안 즉시 확정한다.
 * 4) 모임이 시작되기 전까지 다 답하지 않으면 18단계 크론이 REJECTED로 정리한다(일정은 그대로).
 */
@Service
@RequiredArgsConstructor
public class RoomChangeService {

    static final int MAX_DAY = 4;
    static final int MINUTES_PER_DAY = 1440;
    static final int PLACE_MAX = 50;
    static final String DECLINE_REASON = "일정 변경으로 불참";
    private static final String[] DAY_NAMES = {"월", "화", "수", "목", "금"};

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomChangeRepository roomChangeRepository;
    private final RoomChangeVoteRepository voteRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final CancelLogRepository cancelLogRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    /** 방장: 일정 변경 제안 */
    @Transactional
    public ChangeResponse propose(Long userId, Long roomId, ProposeChangeRequest req) {
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));
        List<RoomMember> active = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        User host = memberUser(active, userId);
        if (!room.getHost().getId().equals(userId)) {
            throw new ApiException(ErrorCode.CHANGE_HOST_ONLY);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        requireChangeable(RoomPhase.of(room, active.size(), now));
        if (!roomChangeRepository.findByRoomIdAndStatus(roomId, ChangeStatus.PENDING).isEmpty()) {
            throw new ApiException(ErrorCode.CHANGE_ALREADY_PENDING);
        }

        int day = req.day();
        int start = req.start();
        int end = req.end();
        checkRange(day, start, end);
        String place = cleanPlace(req.place());
        if (place != null && place.equals(room.getPlace())) {
            place = null;
        }
        if (day == room.getDay() && start == room.getStartMin() && end == room.getEndMin() && place == null) {
            throw new ApiException(ErrorCode.CHANGE_SAME);
        }

        RoomChange change = roomChangeRepository.save(new RoomChange(room, day, start, end, place));
        String what = schedule(day, start, end, place == null ? room.getPlace() : place);

        Optional<Chat> chat = chatRepository.findByRoomId(roomId);
        chat.ifPresent(c -> messageRepository.save(new Message(c, null,
            host.getNick() + "님이 일정을 " + what + "(으)로 바꾸자고 했어요. 참여 여부를 알려 주세요")));

        List<User> voters = requiredVoters(room, change, active);
        if (voters.isEmpty()) {
            apply(room, change, active, now, chat);
            return toResponse(change, room, active, Map.of(), userId);
        }
        for (User u : voters) {
            notificationService.notify(u, NotifType.CHANGE, "일정이 바뀌었어요",
                host.getNick() + "님이 '" + room.getTitle() + "' 일정을 " + what + "(으)로 바꾸자고 해요. 참여 여부를 알려 주세요",
                link(roomId), host, true);
        }
        return toResponse(change, room, active, Map.of(), userId);
    }

    /** 이 모임에서 답변을 기다리는 일정 변경. 없으면 empty */
    @Transactional(readOnly = true)
    public Optional<ChangeResponse> pending(Long userId, Long roomId) {
        Room room = roomRepository.findWithHostById(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));
        List<RoomMember> active = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        memberUser(active, userId);
        return roomChangeRepository.findByRoomIdAndStatus(roomId, ChangeStatus.PENDING).stream()
            .findFirst()
            .map(c -> toResponse(c, room, active, votesOf(c), userId));
    }

    /** 참여자: 동의 또는 불참 */
    @Transactional
    public ChangeResponse vote(Long userId, Long changeId, boolean accept) {
        RoomChange change = roomChangeRepository.findById(changeId)
            .orElseThrow(() -> new ApiException(ErrorCode.CHANGE_NOT_FOUND));
        Long roomId = change.getRoom().getId();
        // 마지막 두 사람이 동시에 답해도 확정이 한 번만 일어나게 방 단위로 줄 세운다
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));
        if (change.getStatus() != ChangeStatus.PENDING) {
            throw new ApiException(ErrorCode.CHANGE_ALREADY_DECIDED);
        }
        List<RoomMember> active = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        User me = memberUser(active, userId);
        if (room.getHost().getId().equals(userId)) {
            throw new ApiException(ErrorCode.CHANGE_HOST_NO_VOTE);
        }
        if (voteRepository.existsById(new RoomChangeVoteId(changeId, userId))) {
            throw new ApiException(ErrorCode.CHANGE_ALREADY_VOTED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        requireChangeable(RoomPhase.of(room, active.size(), now));

        voteRepository.save(new RoomChangeVote(change, me, accept));
        resolveMyNotification(userId, roomId, accept, now);

        Optional<Chat> chat = chatRepository.findByRoomId(roomId);
        List<RoomMember> remaining = active;
        if (!accept) {
            remaining = leave(room, me, active, now, chat);
        }

        Map<Long, Boolean> votes = votesOf(change);
        votes.put(userId, accept);
        List<User> voters = requiredVoters(room, change, remaining);
        boolean allAnswered = voters.stream().allMatch(u -> votes.containsKey(u.getId()));
        if (allAnswered) {
            apply(room, change, remaining, now, chat);
        }
        return toResponse(change, room, remaining, votes, userId);
    }

    // ---------------------------------------------------------------- 내부

    /** 답해야 하는 사람: 방장이 아니고, 제안이 나오기 전부터 참여 중이던 사람 */
    private static List<User> requiredVoters(Room room, RoomChange change, List<RoomMember> active) {
        LocalDateTime proposedAt = change.getCreatedAt();
        return active.stream()
            .filter(m -> !m.getUser().getId().equals(room.getHost().getId()))
            .filter(m -> proposedAt == null || m.getJoinedAt() == null || !m.getJoinedAt().isAfter(proposedAt))
            .map(RoomMember::getUser)
            .toList();
    }

    /** 일정 확정: rooms에 반영하고 남은 참여자 모두에게 알린다 */
    private void apply(Room room, RoomChange change, List<RoomMember> active, LocalDateTime now, Optional<Chat> chat) {
        room.setDay(change.getNewDay());
        room.setStartMin(change.getNewStart());
        room.setEndMin(change.getNewEnd());
        room.setMeetDate(nextMeetDate(change.getNewDay(), change.getNewStart(), now));
        if (change.getNewPlace() != null) {
            room.setPlace(change.getNewPlace());
        }
        change.setStatus(ChangeStatus.APPLIED);

        String what = schedule(room.getDay(), room.getStartMin(), room.getEndMin(), room.getPlace());
        chat.ifPresent(c -> messageRepository.save(new Message(c, null, "일정이 " + what + "(으)로 확정됐어요")));
        for (RoomMember m : active) {
            notificationService.notify(m.getUser(), NotifType.CHANGE, "일정이 확정됐어요",
                "'" + room.getTitle() + "' 일정이 " + what + "(으)로 확정됐어요", link(room.getId()));
        }
    }

    /** '이번엔 빠질게요': 매너 점수는 그대로, cancel_log에 0점으로 기록하고 채팅방에서도 나간다 */
    private List<RoomMember> leave(Room room, User me, List<RoomMember> active, LocalDateTime now, Optional<Chat> chat) {
        RoomMember mine = active.stream()
            .filter(m -> m.getUser().getId().equals(me.getId()))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_MEMBER));
        mine.setLeftAt(now);
        mine.setLeaveReason(DECLINE_REASON);
        roomMemberRepository.save(mine);
        cancelLogRepository.save(new CancelLog(me, room, DECLINE_REASON, BigDecimal.ZERO));
        chat.ifPresent(c -> {
            chatMemberRepository.findById(new ChatMemberId(c.getId(), me.getId()))
                .ifPresent(chatMemberRepository::delete);
            messageRepository.save(new Message(c, null, me.getNick() + "님이 바뀐 일정에는 참여하지 않기로 했어요"));
        });
        return active.stream().filter(m -> m != mine).toList();
    }

    private void resolveMyNotification(Long userId, Long roomId, boolean accept, LocalDateTime now) {
        notificationRepository
            .findByUserIdAndTypeAndLinkAndNeedsActionTrueAndResolvedIsNull(userId, NotifType.CHANGE, link(roomId))
            .forEach(n -> {
                n.setResolved(accept ? NotifResolution.ACCEPTED : NotifResolution.DECLINED);
                if (n.getReadAt() == null) {
                    n.setReadAt(now);
                }
            });
    }

    private Map<Long, Boolean> votesOf(RoomChange change) {
        Map<Long, Boolean> out = new HashMap<>();
        if (change.getId() == null) {
            return out;
        }
        for (RoomChangeVote v : voteRepository.findByChangeId(change.getId())) {
            out.put(v.getId().getUserId(), v.isAccepted());
        }
        return out;
    }

    private static ChangeResponse toResponse(
        RoomChange c, Room room, List<RoomMember> active, Map<Long, Boolean> votes, Long viewerId
    ) {
        List<User> voters = requiredVoters(room, c, active);
        int accepted = (int) voters.stream().filter(u -> Boolean.TRUE.equals(votes.get(u.getId()))).count();
        boolean isHost = room.getHost().getId().equals(viewerId);
        Boolean myVote = votes.get(viewerId);
        boolean canVote = c.getStatus() == ChangeStatus.PENDING && !isHost && myVote == null
            && active.stream().anyMatch(m -> m.getUser().getId().equals(viewerId));
        return new ChangeResponse(
            c.getId(), room.getId(), c.getStatus(),
            c.getNewDay(), c.getNewStart(), c.getNewEnd(), c.getNewPlace(),
            room.getDay(), room.getStartMin(), room.getEndMin(), room.getPlace(), room.getMeetDate(),
            voters.size(), accepted, myVote, canVote, isHost);
    }

    private static User memberUser(List<RoomMember> active, Long userId) {
        return active.stream()
            .map(RoomMember::getUser)
            .filter(u -> u.getId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_MEMBER));
    }

    /** 시작 30분 전 마감 이후·종료·취소된 모임은 바꿀 수 없다 */
    private static void requireChangeable(RoomPhase phase) {
        switch (phase) {
            case OPEN, SOON, FULL -> { }
            case CLOSED -> throw new ApiException(ErrorCode.ROOM_CLOSED, "시작 30분 전부터는 일정을 바꿀 수 없어요");
            case ENDED -> throw new ApiException(ErrorCode.ROOM_ENDED);
            case CANCELLED -> throw new ApiException(ErrorCode.ROOM_CANCELLED);
            default -> throw new ApiException(ErrorCode.CONFLICT);
        }
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

    private static String cleanPlace(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (t.length() > PLACE_MAX) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "장소는 50자까지 쓸 수 있어요");
        }
        return t;
    }

    /** RoomService.nextMeetDate와 같은 규칙: 이번 주 그 요일, 이미 지났으면 다음 주 */
    static LocalDate nextMeetDate(int day, int startMin, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        int todayDay = today.getDayOfWeek().getValue() - 1;
        LocalDate candidate = today.plusDays((day - todayDay + 7) % 7);
        if (candidate.equals(today) && !candidate.atStartOfDay().plusMinutes(startMin).isAfter(now)) {
            return candidate.plusWeeks(1);
        }
        return candidate;
    }

    static String schedule(int day, int start, int end, String place) {
        return DAY_NAMES[day] + " " + hm(start) + "–" + hm(end) + " · " + place;
    }

    private static String hm(int min) {
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    private static String link(Long roomId) {
        return "/rooms/" + roomId;
    }
}
