package com.chatssl.anytime.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.chatssl.anytime.review.dto.SubmitReviewRequest.RatingItem;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.room.dto.RoomResponse;
import com.chatssl.anytime.user.dto.UserProfileResponse;

class ReviewServiceTest {

    /** 2026-11-25(수) 10:40. 9:00~10:00 모임은 끝났다 */
    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 25).atTime(10, 40);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomMemberRepository roomMembers = mock(RoomMemberRepository.class);
    private final AttendanceRepository attendance = mock(AttendanceRepository.class);
    private final RatingRepository ratings = mock(RatingRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final ReviewService service = new ReviewService(rooms, roomMembers, attendance, ratings, blocks, clock);

    private User me;
    private User u2;
    private User u3;
    private Room ended;

    @BeforeEach
    void setUp() {
        me = user(1L, "데모유저");
        u2 = user(2L, "민트초코");
        u3 = user(3L, "보드왕");
        ended = room(10L, me, TODAY, 540, 600);

        when(rooms.findByIdForUpdate(10L)).thenReturn(Optional.of(ended));
        when(rooms.findWithHostById(10L)).thenReturn(Optional.of(ended));
        when(roomMembers.findActiveWithUserByRoomId(10L)).thenReturn(members(ended, me, u2, u3));
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of());
        when(attendance.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ratings.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 과반은_참여자_절반_초과() {
        assertThat(ReviewService.isMajority(1, 2)).isFalse();
        assertThat(ReviewService.isMajority(2, 2)).isTrue();
        assertThat(ReviewService.isMajority(2, 3)).isTrue();
        assertThat(ReviewService.isMajority(2, 4)).isFalse();
        assertThat(ReviewService.isMajority(3, 4)).isTrue();
        assertThat(ReviewService.isMajority(0, 0)).isFalse();
    }

    @Test
    void 제출하면_참석_확인과_평가를_저장하고_과반을_넘긴_사람은_완료가_오른다() {
        // u2는 이미 1명이 확인해 줬다 → 내가 확인하면 2/3 과반
        when(attendance.countByRoomIdAndUserId(10L, 2L)).thenReturn(1L);
        // 나는 지금까지 0명 → 내 확인 후 1명
        when(attendance.countByRoomIdAndUserId(10L, 1L)).thenReturn(0L, 1L);

        ReviewResultResponse res = service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L), List.of(new RatingItem(2L, 5, null)), "  재밌었어요  "));

        ArgumentCaptor<Attendance> att = ArgumentCaptor.forClass(Attendance.class);
        verify(attendance, times(2)).save(att.capture());
        assertThat(att.getAllValues()).extracting(Attendance::getId)
            .containsExactly(new AttendanceId(10L, 1L, 1L), new AttendanceId(10L, 2L, 1L));

        ArgumentCaptor<Rating> rt = ArgumentCaptor.forClass(Rating.class);
        verify(ratings).save(rt.capture());
        Rating saved = rt.getValue();
        assertThat(saved.getTarget().getId()).isEqualTo(2L);
        assertThat(saved.getScore()).isEqualTo(5);
        assertThat(saved.getReason()).isNull();
        assertThat(saved.getNote()).isEqualTo("재밌었어요");
        assertThat(saved.getAppliedAt()).isNull();

        assertThat(u2.getCompleted()).isEqualTo(1);
        assertThat(me.getCompleted()).isZero();
        // 매너 점수는 크론이 반영하므로 그대로
        assertThat(me.getManner()).isEqualByComparingTo("36.8");
        assertThat(u2.getManner()).isEqualByComparingTo("36.8");

        assertThat(res.savedRatings()).isEqualTo(1);
        assertThat(res.myConfirmations()).isEqualTo(1);
        assertThat(res.memberCount()).isEqualTo(3);
        assertThat(res.myCompleted()).isFalse();
    }

    @Test
    void 이미_과반이던_사람은_완료가_다시_오르지_않는다() {
        when(attendance.countByRoomIdAndUserId(10L, 2L)).thenReturn(2L);
        when(attendance.countByRoomIdAndUserId(10L, 1L)).thenReturn(1L, 2L);

        ReviewResultResponse res = service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L, 2L), List.of(new RatingItem(2L, 4, null)), null));

        assertThat(u2.getCompleted()).isZero();
        assertThat(me.getCompleted()).isEqualTo(1);
        assertThat(res.myCompleted()).isTrue();
    }

    @Test
    void 아무도_안_왔으면_나만_기록하고_평가는_없다() {
        ReviewResultResponse res = service.submit(1L, 10L, new SubmitReviewRequest(List.of(), List.of(), ""));

        verify(attendance).save(any(Attendance.class));
        verify(ratings, never()).save(any(Rating.class));
        assertThat(res.savedRatings()).isZero();
    }

    @Test
    void 두_점_이하는_목록의_이유가_있어야_한다() {
        assertCode(() -> service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L), List.of(new RatingItem(2L, 2, null)), null)), ErrorCode.RATING_REASON_REQUIRED);
        assertCode(() -> service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L), List.of(new RatingItem(2L, 1, "아무 말")), null)), ErrorCode.RATING_REASON_REQUIRED);
        verify(attendance, never()).save(any(Attendance.class));

        service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L), List.of(new RatingItem(2L, 1, "시간 약속을 안 지켰어요")), null));
        ArgumentCaptor<Rating> rt = ArgumentCaptor.forClass(Rating.class);
        verify(ratings).save(rt.capture());
        assertThat(rt.getValue().getReason()).isEqualTo("시간 약속을 안 지켰어요");
    }

    @Test
    void 왔다고_한_사람을_빠뜨리거나_안_온_사람을_평가하면_400() {
        assertCode(() -> service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L, 3L), List.of(new RatingItem(2L, 4, null)), null)), ErrorCode.RATING_MISSING);
        assertCode(() -> service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L), List.of(new RatingItem(2L, 4, null), new RatingItem(3L, 4, null)), null)),
            ErrorCode.REVIEW_TARGET_INVALID);
        assertCode(() -> service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(99L), List.of(), null)), ErrorCode.REVIEW_TARGET_INVALID);
        assertCode(() -> service.submit(1L, 10L, new SubmitReviewRequest(
            List.of(2L), List.of(new RatingItem(1L, 5, null)), null)), ErrorCode.RATING_SELF);
        verify(attendance, never()).save(any(Attendance.class));
        verify(ratings, never()).save(any(Rating.class));
    }

    @Test
    void 참여자가_아니거나_끝나기_전이거나_이미_냈거나_7일이_지났으면_막는다() {
        assertCode(() -> service.submit(4L, 10L, new SubmitReviewRequest(List.of(), List.of(), null)),
            ErrorCode.ROOM_NOT_MEMBER);

        Room later = room(11L, me, TODAY, 720, 780);
        when(rooms.findByIdForUpdate(11L)).thenReturn(Optional.of(later));
        when(roomMembers.findActiveWithUserByRoomId(11L)).thenReturn(members(later, me, u2));
        assertCode(() -> service.submit(1L, 11L, new SubmitReviewRequest(List.of(), List.of(), null)),
            ErrorCode.REVIEW_NOT_ENDED);

        Room old = room(12L, me, TODAY.minusDays(8), 540, 600);
        when(rooms.findByIdForUpdate(12L)).thenReturn(Optional.of(old));
        when(roomMembers.findActiveWithUserByRoomId(12L)).thenReturn(members(old, me, u2));
        assertCode(() -> service.submit(1L, 12L, new SubmitReviewRequest(List.of(), List.of(), null)),
            ErrorCode.REVIEW_EXPIRED);

        Room canceled = room(13L, me, TODAY, 540, 600);
        canceled.setStatus(RoomStatus.CANCELED);
        when(rooms.findByIdForUpdate(13L)).thenReturn(Optional.of(canceled));
        when(roomMembers.findActiveWithUserByRoomId(13L)).thenReturn(members(canceled, me, u2));
        assertCode(() -> service.submit(1L, 13L, new SubmitReviewRequest(List.of(), List.of(), null)),
            ErrorCode.ROOM_CANCELLED);

        when(attendance.existsByRoomIdAndConfirmerId(10L, 1L)).thenReturn(true);
        assertCode(() -> service.submit(1L, 10L, new SubmitReviewRequest(List.of(), List.of(), null)),
            ErrorCode.REVIEW_ALREADY_DONE);

        assertCode(() -> service.submit(1L, 404L, new SubmitReviewRequest(List.of(), List.of(), null)),
            ErrorCode.ROOM_NOT_FOUND);
        verify(attendance, never()).save(any(Attendance.class));
    }

    @Test
    void 평가_화면은_나와_차단_관계를_뺀_참여자를_준다() {
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(3L));

        ReviewFormResponse res = service.form(1L, 10L);

        assertThat(res.members()).extracting(UserProfileResponse::id).containsExactly(2L);
        assertThat(res.memberCount()).isEqualTo(3);
        assertThat(res.status()).isEqualTo(RoomPhase.ENDED);
        assertThat(res.reviewed()).isFalse();
        assertThat(res.canReview()).isTrue();
        assertThat(res.lowReasons()).contains("기타");

        when(attendance.existsByRoomIdAndConfirmerId(10L, 1L)).thenReturn(true);
        assertThat(service.form(1L, 10L).canReview()).isFalse();
    }

    @Test
    void 평가할_모임은_끝났고_아직_안_낸_것만() {
        Room done = room(10L, me, TODAY, 540, 600);
        Room reviewed = room(11L, u2, TODAY.minusDays(1), 540, 600);
        Room later = room(12L, u2, TODAY, 720, 780);
        Room alone = room(13L, me, TODAY.minusDays(2), 540, 600); // 혼자 남아 자동 취소
        when(roomMembers.findMineBetween(eq(1L), eq(RoomStatus.CANCELED), eq(TODAY.minusDays(7)), eq(TODAY)))
            .thenReturn(List.of(new RoomMember(done, me), new RoomMember(reviewed, me),
                new RoomMember(later, me), new RoomMember(alone, me)));
        when(roomMembers.findByRoomIdInAndLeftAtIsNull(anyCollection())).thenReturn(List.of(
            new RoomMember(done, me), new RoomMember(done, u2),
            new RoomMember(reviewed, me), new RoomMember(reviewed, u2),
            new RoomMember(later, me), new RoomMember(later, u2),
            new RoomMember(alone, me)));
        when(attendance.findReviewedRoomIds(eq(1L), anyCollection())).thenReturn(List.of(11L));

        PendingReviewResponse res = service.pending(1L);

        assertThat(res.pending()).extracting(RoomResponse::id).containsExactly(10L);
        assertThat(res.pending().get(0).joined()).isTrue();
        assertThat(res.reviewedRoomIds()).containsExactly(11L);
    }

    @Test
    void 참여한_모임이_없으면_빈_목록() {
        when(roomMembers.findMineBetween(anyLong(), any(), any(), any())).thenReturn(List.of());
        PendingReviewResponse res = service.pending(1L);
        assertThat(res.pending()).isEmpty();
        assertThat(res.reviewedRoomIds()).isEmpty();
    }

    private static List<RoomMember> members(Room r, User... users) {
        return java.util.Arrays.stream(users).map(u -> new RoomMember(r, u)).toList();
    }

    private static User user(Long id, String nick) {
        User u = new User("hash-" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setManner(new BigDecimal("36.8"));
        return u;
    }

    private static Room room(Long id, User host, LocalDate meetDate, int start, int end) {
        Room r = new Room(host, "보드게임 한 판", "보드게임", meetDate.getDayOfWeek().getValue() - 1,
            start, end, meetDate, "학생회관", 4, null);
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
