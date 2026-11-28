package com.chatssl.anytime.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.Block;
import com.chatssl.anytime.domain.BlockId;
import com.chatssl.anytime.domain.Report;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ReportRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.safety.dto.BlockedUserResponse;
import com.chatssl.anytime.safety.dto.CreateReportRequest;
import com.chatssl.anytime.safety.dto.ReportResponse;

class SafetyServiceTest {

    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 25).atTime(10, 40);

    private final BlockRepository blocks = mock(BlockRepository.class);
    private final ReportRepository reports = mock(ReportRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final SafetyService service = new SafetyService(blocks, reports, users, rooms, clock);

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = user(1L, "데모유저");
        other = user(2L, "민트초코");
        when(users.findById(1L)).thenReturn(Optional.of(me));
        when(users.findById(2L)).thenReturn(Optional.of(other));
        when(users.findById(99L)).thenReturn(Optional.empty());
        when(blocks.findById(any(BlockId.class))).thenReturn(Optional.empty());
        when(blocks.save(any(Block.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reports.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });
    }

    @Test
    void 차단하면_blocks에_저장하고_상대_프로필을_돌려준다() {
        BlockedUserResponse res = service.block(1L, 2L);

        ArgumentCaptor<Block> saved = ArgumentCaptor.forClass(Block.class);
        verify(blocks).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new BlockId(1L, 2L));
        assertThat(res.user().id()).isEqualTo(2L);
        assertThat(res.user().nick()).isEqualTo("민트초코");
        assertThat(res.blockedAt()).isEqualTo(NOW);
    }

    @Test
    void 이미_차단했으면_다시_저장하지_않고_성공한다() {
        Block existing = new Block(me, other);
        when(blocks.findById(new BlockId(1L, 2L))).thenReturn(Optional.of(existing));

        service.block(1L, 2L);

        verify(blocks, never()).save(any());
    }

    @Test
    void 본인과_없는_사람은_차단할_수_없다() {
        assertCode(() -> service.block(1L, 1L), ErrorCode.BLOCK_SELF);
        assertCode(() -> service.block(1L, 99L), ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 차단_해제는_있으면_지우고_없어도_성공한다() {
        Block existing = new Block(me, other);
        when(blocks.findById(new BlockId(1L, 2L))).thenReturn(Optional.of(existing));

        service.unblock(1L, 2L);
        service.unblock(1L, 3L);

        verify(blocks).delete(existing);
    }

    @Test
    void 차단_목록은_차단한_사람의_공개_프로필로_돌려준다() {
        Block b = new Block(me, other);
        b.setCreatedAt(NOW.minusDays(1));
        when(blocks.findByBlockerWithUser(1L)).thenReturn(List.of(b));

        List<BlockedUserResponse> res = service.blocks(1L);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).user().nick()).isEqualTo("민트초코");
        assertThat(res.get(0).blockedAt()).isEqualTo(NOW.minusDays(1));
    }

    @Test
    void 신고는_사유를_검사하고_저장한다() {
        ReportResponse res = service.report(1L, new CreateReportRequest(2L, "괴롭힘·스토킹", "  반복 연락  ", null, false));

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo("괴롭힘·스토킹");
        assertThat(saved.getValue().getDetail()).isEqualTo("반복 연락");
        assertThat(saved.getValue().isEmergency()).isFalse();
        assertThat(res.id()).isEqualTo(500L);
        assertThat(res.blocked()).isFalse();
        verify(blocks, never()).save(any());
    }

    @Test
    void 목록에_없는_사유와_본인_신고는_막는다() {
        assertCode(() -> service.report(1L, new CreateReportRequest(2L, "그냥", null, null, false)),
            ErrorCode.REPORT_REASON_INVALID);
        assertCode(() -> service.report(1L, new CreateReportRequest(1L, "기타", null, null, false)),
            ErrorCode.REPORT_SELF);
    }

    @Test
    void 같은_사람을_24시간_안에_다시_신고하면_429() {
        when(reports.existsByReporterIdAndTargetIdAndCreatedAtAfter(eq(1L), eq(2L), eq(NOW.minusHours(24))))
            .thenReturn(true);

        assertCode(() -> service.report(1L, new CreateReportRequest(2L, "기타", null, null, false)),
            ErrorCode.REPORT_TOO_SOON);
    }

    @Test
    void 긴급_신고는_24시간_제한이_없고_바로_차단한다() {
        when(reports.existsByReporterIdAndTargetIdAndCreatedAtAfter(any(), any(), any())).thenReturn(true);

        ReportResponse res = service.report(1L, new CreateReportRequest(2L, "긴급 신고", "위협", null, true));

        assertThat(res.emergency()).isTrue();
        assertThat(res.blocked()).isTrue();
        verify(blocks).save(any(Block.class));
    }

    @Test
    void 없는_모임으로_신고하면_404() {
        when(rooms.findById(7L)).thenReturn(Optional.empty());

        assertCode(() -> service.report(1L, new CreateReportRequest(2L, "기타", null, 7L, false)),
            ErrorCode.ROOM_NOT_FOUND);
    }

    private static User user(Long id, String nick) {
        User u = new User("hash-" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setManner(new BigDecimal("36.8"));
        return u;
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo(code);
    }
}
