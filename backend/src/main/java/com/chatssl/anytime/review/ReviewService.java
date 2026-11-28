package com.chatssl.anytime.review;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Attendance;
import com.chatssl.anytime.domain.AttendanceId;
import com.chatssl.anytime.domain.Rating;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomStatus;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.AttendanceRepository;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.RatingRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.review.dto.PendingReviewResponse;
import com.chatssl.anytime.review.dto.ReviewFormResponse;
import com.chatssl.anytime.review.dto.ReviewResultResponse;
import com.chatssl.anytime.review.dto.SubmitReviewRequest;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.room.dto.RoomResponse;
import com.chatssl.anytime.user.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

/**
 * 16단계: 참석 확인·상호 평가.
 *
 * - 평가는 모임이 끝난 뒤(RoomPhase.ENDED) 7일 안에, 그 모임에 남아 있던 사람만 한 번 낼 수 있다.
 * - 참석 확인과 평가를 한 요청으로 받아 한 트랜잭션에 저장한다 (반만 저장되는 일이 없게).
 * - 참여자 과반이 '왔어요'를 눌러 준 사람은 users.completed가 1 오른다 (과반을 처음 넘길 때 한 번).
 * - 평가 점수는 바로 매너 점수에 넣지 않는다. applied_at = NULL로 저장하고 18단계 크론이 모아서 반영한다.
 *   (피드백 반영: 소규모 모임에서 누가 몇 점을 줬는지 바로 티 나지 않게)
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** 모임이 끝난 뒤 평가할 수 있는 기간 */
    static final int REVIEW_DAYS = 7;
    static final int NOTE_MAX = 60;

    /** 2점 이하일 때 고를 이유. 프론트 Review.tsx의 LOW와 같다 */
    public static final List<String> LOW_REASONS = List.of(
        "시간 약속을 안 지켰어요",
        "불편한 말을 했어요",
        "원치 않는 연락",
        "기타"
    );

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final AttendanceRepository attendanceRepository;
    private final RatingRepository ratingRepository;
    private final BlockRepository blockRepository;
    private final Clock clock;

    /** 평가할 모임 목록 (홈 배너·모임 탭 버튼용) */
    @Transactional(readOnly = true)
    public PendingReviewResponse pending(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        List<RoomMember> mine = roomMemberRepository.findMineBetween(
            userId, RoomStatus.CANCELED, today.minusDays(REVIEW_DAYS), today);
        if (mine.isEmpty()) {
            return new PendingReviewResponse(List.of(), List.of());
        }

        List<Room> rooms = mine.stream().map(RoomMember::getRoom).toList();
        List<Long> roomIds = rooms.stream().map(Room::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        for (RoomMember m : roomMemberRepository.findByRoomIdInAndLeftAtIsNull(roomIds)) {
            counts.merge(m.getId().getRoomId(), 1, Integer::sum);
        }
        Set<Long> reviewed = new HashSet<>(attendanceRepository.findReviewedRoomIds(userId, roomIds));

        List<RoomResponse> pending = new ArrayList<>();
        for (Room r : rooms) {
            int count = counts.getOrDefault(r.getId(), 0);
            RoomPhase phase = RoomPhase.of(r, count, now);
            if (phase != RoomPhase.ENDED || reviewed.contains(r.getId()) || expired(r, now)) {
                continue;
            }
            pending.add(RoomResponse.of(r, count, phase, true, 0, null));
        }
        pending.sort(Comparator.comparing(RoomResponse::meetDate).thenComparingInt(RoomResponse::end).reversed());
        return new PendingReviewResponse(pending, reviewed.stream().sorted().toList());
    }

    /** 평가 화면 정보 */
    @Transactional(readOnly = true)
    public ReviewFormResponse form(Long userId, Long roomId) {
        Room room = roomRepository.findWithHostById(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));
        List<RoomMember> active = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        requireMember(active, userId);

        LocalDateTime now = LocalDateTime.now(clock);
        RoomPhase phase = RoomPhase.of(room, active.size(), now);
        boolean reviewed = attendanceRepository.existsByRoomIdAndConfirmerId(roomId, userId);
        boolean canReview = phase == RoomPhase.ENDED && !reviewed && !expired(room, now);

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        List<UserProfileResponse> others = active.stream()
            .map(RoomMember::getUser)
            .filter(u -> !u.getId().equals(userId) && !hidden.contains(u.getId()))
            .map(UserProfileResponse::of)
            .toList();

        return new ReviewFormResponse(
            room.getId(), room.getTitle(), room.getActivity(), room.getMeetDate(),
            room.getStartMin(), room.getEndMin(), phase, active.size(), others,
            reviewed, canReview, LOW_REASONS);
    }

    /** 참석 확인 + 평가 저장 */
    @Transactional
    public ReviewResultResponse submit(Long userId, Long roomId, SubmitReviewRequest req) {
        // 같은 방에 여러 명이 동시에 내도 과반 계산(completed +1)이 어긋나지 않게 방 단위로 줄 세운다
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));
        List<RoomMember> active = roomMemberRepository.findActiveWithUserByRoomId(roomId);
        User me = requireMember(active, userId);

        LocalDateTime now = LocalDateTime.now(clock);
        RoomPhase phase = RoomPhase.of(room, active.size(), now);
        if (phase == RoomPhase.CANCELLED) {
            throw new ApiException(ErrorCode.ROOM_CANCELLED);
        }
        if (phase != RoomPhase.ENDED) {
            throw new ApiException(ErrorCode.REVIEW_NOT_ENDED);
        }
        if (expired(room, now)) {
            throw new ApiException(ErrorCode.REVIEW_EXPIRED);
        }
        if (attendanceRepository.existsByRoomIdAndConfirmerId(roomId, userId)
            || ratingRepository.existsByRoomIdAndRaterId(roomId, userId)) {
            throw new ApiException(ErrorCode.REVIEW_ALREADY_DONE);
        }

        Map<Long, User> members = new HashMap<>();
        for (RoomMember m : active) {
            members.put(m.getUser().getId(), m.getUser());
        }

        // 1) 온 사람: 나는 항상 포함. 중복은 하나로
        Set<Long> present = new LinkedHashSet<>();
        present.add(userId);
        for (Long id : req.presentUserIds()) {
            if (id == null || !members.containsKey(id)) {
                throw new ApiException(ErrorCode.REVIEW_TARGET_INVALID);
            }
            present.add(id);
        }

        // 2) 평가: 온 사람(나 제외) 모두를 정확히 한 번씩
        Set<Long> toRate = new HashSet<>(present);
        toRate.remove(userId);
        Set<Long> rated = new HashSet<>();
        for (SubmitReviewRequest.RatingItem item : req.ratings()) {
            Long target = item.targetUserId();
            if (userId.equals(target)) {
                throw new ApiException(ErrorCode.RATING_SELF);
            }
            if (!toRate.contains(target)) {
                throw new ApiException(ErrorCode.REVIEW_TARGET_INVALID);
            }
            if (!rated.add(target)) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "한 사람은 한 번만 평가할 수 있어요");
            }
            int score = item.score();
            if (score < 1 || score > 5) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "점수는 1~5점이에요");
            }
            if (score <= 2 && (item.reason() == null || !LOW_REASONS.contains(item.reason().trim()))) {
                throw new ApiException(ErrorCode.RATING_REASON_REQUIRED);
            }
        }
        if (!rated.equals(toRate)) {
            throw new ApiException(ErrorCode.RATING_MISSING);
        }

        // 3) 참석 확인 저장 + 과반을 처음 넘긴 사람은 completed +1
        int n = active.size();
        for (Long id : present) {
            long before = attendanceRepository.countByRoomIdAndUserId(roomId, id);
            if (attendanceRepository.existsById(new AttendanceId(roomId, id, userId))) {
                continue;
            }
            attendanceRepository.save(new Attendance(room, members.get(id), me));
            if (!isMajority(before, n) && isMajority(before + 1, n)) {
                User u = members.get(id);
                u.setCompleted(u.getCompleted() + 1);
            }
        }

        // 4) 평가 저장 (applied_at = NULL, 크론이 반영)
        String note = cleanNote(req.note());
        int saved = 0;
        for (SubmitReviewRequest.RatingItem item : req.ratings()) {
            int score = item.score();
            String reason = score <= 2 ? item.reason().trim() : null;
            ratingRepository.save(new Rating(room, me, members.get(item.targetUserId()), score, reason, note));
            saved++;
        }

        long mine = attendanceRepository.countByRoomIdAndUserId(roomId, userId);
        return new ReviewResultResponse(roomId, saved, (int) mine, n, isMajority(mine, n));
    }

    /** 참여자 n명 중 과반 (2명이면 2명, 3명이면 2명, 4명이면 3명) */
    static boolean isMajority(long confirmations, int n) {
        return n > 0 && confirmations * 2 > n;
    }

    private boolean expired(Room room, LocalDateTime now) {
        LocalDateTime end = room.getMeetDate().atStartOfDay().plusMinutes(room.getEndMin());
        return now.isAfter(end.plusDays(REVIEW_DAYS));
    }

    private static User requireMember(List<RoomMember> active, Long userId) {
        return active.stream()
            .map(RoomMember::getUser)
            .filter(u -> u.getId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_MEMBER));
    }

    private static String cleanNote(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > NOTE_MAX ? t.substring(0, NOTE_MAX) : t;
    }
}
