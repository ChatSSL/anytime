package com.chatssl.anytime.cron;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.domain.ChangeStatus;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Rating;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomChange;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomStatus;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.NotificationService;
import com.chatssl.anytime.repository.AttendanceRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.RatingRepository;
import com.chatssl.anytime.repository.RoomChangeRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.room.RoomPhase;

/**
 * 18단계 크론 작업. CronScheduler가 1분마다 아래 순서로 부른다. 각 메서드는 따로 트랜잭션이다.
 *
 * 1) closeStarted   시작했는데 2명 미만이면 자동 취소(운영팀 모임 제외), 끝났으면 완료(DONE) + 평가 요청 알림
 * 2) rejectStaleChanges  시작 30분 전까지 답이 안 끝난 일정 변경(15단계)은 무산(REJECTED). 일정은 그대로
 * 3) remind         시작 1시간 전 안에 들어온 모임 참여자에게 리마인드 (한 사람에 한 번)
 * 4) applyRatings   모아 둔 평가(16단계, applied_at NULL)를 매너 점수에 반영
 *
 * 30분 전 모집 마감은 RoomPhase가 조회할 때 계산하므로 크론이 할 일이 없다.
 * 시간은 모두 Clock(Asia/Seoul) 기준이라 테스트에서 시각을 고정할 수 있다.
 */
@Service
public class CronService {

    static final int REMIND_BEFORE_MIN = 60;
    /** 이보다 오래된 모임은 상태만 정리하고 알림은 보내지 않는다 (시드 데이터의 지난 모임 등) */
    static final int NOTIFY_CANCEL_WITHIN_HOURS = 24;
    static final int NOTIFY_RATE_WITHIN_DAYS = 7;

    static final BigDecimal MIN_MANNER = new BigDecimal("30.0");
    static final BigDecimal MAX_MANNER = new BigDecimal("45.0");
    /** 점수(1~5)별 매너 온도 변화 */
    static final Map<Integer, BigDecimal> MANNER_DELTA = Map.of(
        5, new BigDecimal("0.2"),
        4, new BigDecimal("0.1"),
        3, BigDecimal.ZERO,
        2, new BigDecimal("-0.2"),
        1, new BigDecimal("-0.5")
    );

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomChangeRepository roomChangeRepository;
    private final RatingRepository ratingRepository;
    private final AttendanceRepository attendanceRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    /** 평가가 들어오고 이 시간(분)이 지나야 매너 점수에 반영. 바로 반영하면 누가 몇 점 줬는지 티가 난다 */
    private final int ratingDelayMinutes;

    public CronService(
        RoomRepository roomRepository,
        RoomMemberRepository roomMemberRepository,
        RoomChangeRepository roomChangeRepository,
        RatingRepository ratingRepository,
        AttendanceRepository attendanceRepository,
        ChatRepository chatRepository,
        MessageRepository messageRepository,
        NotificationRepository notificationRepository,
        NotificationService notificationService,
        Clock clock,
        @Value("${app.cron.rating-delay-minutes:60}") int ratingDelayMinutes
    ) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.roomChangeRepository = roomChangeRepository;
        this.ratingRepository = ratingRepository;
        this.attendanceRepository = attendanceRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.ratingDelayMinutes = ratingDelayMinutes;
    }

    /** 처리한 개수 */
    public record CloseResult(int cancelled, int finished) {
    }

    // ------------------------------------------------------------ 1) 자동 취소·완료

    @Transactional
    public CloseResult closeStarted() {
        LocalDateTime now = LocalDateTime.now(clock);
        int cancelled = 0;
        int finished = 0;
        for (Room candidate : roomRepository.findDueBy(RoomStatus.OPEN, now.toLocalDate())) {
            if (now.isBefore(start(candidate))) {
                continue;
            }
            // 사용자의 합류·나가기와 겹치지 않게 방을 잠그고 다시 확인한다
            Room room = roomRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (room == null || room.getStatus() != RoomStatus.OPEN) {
                continue;
            }
            List<RoomMember> members = roomMemberRepository.findActiveWithUserByRoomId(room.getId());
            if (!room.isOfficial() && members.size() < RoomPhase.MIN_MEMBERS) {
                cancel(room, members, now);
                cancelled++;
            } else if (!now.isBefore(end(room))) {
                finish(room, members, now);
                finished++;
            }
        }
        return new CloseResult(cancelled, finished);
    }

    private void cancel(Room room, List<RoomMember> members, LocalDateTime now) {
        room.setStatus(RoomStatus.CANCELED);
        systemMessage(room, "참여자가 2명이 안 돼서 모임이 자동으로 취소됐어요");
        if (start(room).isBefore(now.minusHours(NOTIFY_CANCEL_WITHIN_HOURS))) {
            return;
        }
        for (RoomMember m : members) {
            notificationService.notify(m.getUser(), NotifType.CANCEL, "모임이 취소됐어요",
                "'" + room.getTitle() + "' 모임에 2명이 모이지 않아 자동으로 취소됐어요. 비슷한 모임을 찾아볼까요?",
                link(room));
        }
    }

    private void finish(Room room, List<RoomMember> members, LocalDateTime now) {
        room.setStatus(RoomStatus.DONE);
        systemMessage(room, "모임이 끝났어요. 함께한 사람을 평가해 주세요");
        if (end(room).isBefore(now.minusDays(NOTIFY_RATE_WITHIN_DAYS))) {
            return;
        }
        for (RoomMember m : members) {
            User u = m.getUser();
            // 크론보다 먼저 평가를 마친 사람에게는 보내지 않는다
            if (attendanceRepository.existsByRoomIdAndConfirmerId(room.getId(), u.getId())) {
                continue;
            }
            notificationService.notify(u, NotifType.RATE, "모임은 어땠나요?",
                "'" + room.getTitle() + "' 모임에서 함께한 사람을 평가해 주세요", "/review/" + room.getId());
        }
    }

    // ------------------------------------------------------------ 2) 일정 변경 무산

    @Transactional
    public int rejectStaleChanges() {
        LocalDateTime now = LocalDateTime.now(clock);
        int rejected = 0;
        for (RoomChange c : roomChangeRepository.findWithRoomByStatus(ChangeStatus.PENDING)) {
            Room room = c.getRoom();
            boolean stillOpen = room.getStatus() == RoomStatus.OPEN;
            LocalDateTime deadline = start(room).minusMinutes(RoomPhase.CLOSE_BEFORE_MIN);
            if (stillOpen && now.isBefore(deadline)) {
                continue;
            }
            c.setStatus(ChangeStatus.REJECTED);
            notificationRepository.clearPendingAction(NotifType.CHANGE, link(room));
            if (stillOpen) {
                systemMessage(room, "모두의 답을 받지 못해 일정 변경이 취소됐어요. 원래 일정대로 진행해요");
            }
            rejected++;
        }
        return rejected;
    }

    // ------------------------------------------------------------ 3) 리마인드

    @Transactional
    public int remind() {
        LocalDateTime now = LocalDateTime.now(clock);
        int sent = 0;
        for (Room room : roomRepository.findUpcoming(RoomStatus.OPEN, now.toLocalDate())) {
            LocalDateTime start = start(room);
            if (!now.isBefore(start) || now.isBefore(start.minusMinutes(REMIND_BEFORE_MIN))) {
                continue;
            }
            List<RoomMember> members = roomMemberRepository.findActiveWithUserByRoomId(room.getId());
            if (!room.isOfficial() && members.size() < RoomPhase.MIN_MEMBERS) {
                continue; // 이대로면 자동 취소될 모임. 리마인드 대신 취소 알림이 간다
            }
            for (RoomMember m : members) {
                User u = m.getUser();
                if (notificationRepository.existsByUserIdAndTypeAndLink(u.getId(), NotifType.REMINDER, link(room))) {
                    continue;
                }
                notificationService.notify(u, NotifType.REMINDER, "곧 모임이 시작돼요",
                    "'" + room.getTitle() + "' 모임이 " + hm(room.getStartMin()) + "에 시작해요 · " + room.getPlace(),
                    link(room));
                sent++;
            }
        }
        return sent;
    }

    // ------------------------------------------------------------ 4) 평가 반영

    @Transactional
    public int applyRatings() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Rating> ready = ratingRepository.findReadyToApply(now.minusMinutes(ratingDelayMinutes));
        Map<User, BigDecimal> deltas = new LinkedHashMap<>();
        for (Rating r : ready) {
            BigDecimal d = MANNER_DELTA.getOrDefault(r.getScore(), BigDecimal.ZERO);
            deltas.merge(r.getTarget(), d, BigDecimal::add);
            r.setAppliedAt(now);
        }
        deltas.forEach((user, d) -> user.setManner(clamp(user.getManner().add(d))));
        return ready.size();
    }

    static BigDecimal clamp(BigDecimal manner) {
        return manner.max(MIN_MANNER).min(MAX_MANNER);
    }

    // ------------------------------------------------------------ 도우미

    private void systemMessage(Room room, String text) {
        Optional<Chat> chat = chatRepository.findByRoomId(room.getId());
        chat.ifPresent(c -> messageRepository.save(new Message(c, null, text)));
    }

    private static LocalDateTime start(Room r) {
        return r.getMeetDate().atStartOfDay().plusMinutes(r.getStartMin());
    }

    private static LocalDateTime end(Room r) {
        return r.getMeetDate().atStartOfDay().plusMinutes(r.getEndMin());
    }

    private static String link(Room r) {
        return "/rooms/" + r.getId();
    }

    private static String hm(int min) {
        return String.format("%02d:%02d", min / 60, min % 60);
    }
}
