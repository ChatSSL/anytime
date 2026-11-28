package com.chatssl.anytime.safety;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Block;
import com.chatssl.anytime.domain.BlockId;
import com.chatssl.anytime.domain.Report;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ReportRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.safety.dto.BlockedUserResponse;
import com.chatssl.anytime.safety.dto.CreateReportRequest;
import com.chatssl.anytime.safety.dto.ReportResponse;
import com.chatssl.anytime.user.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

/**
 * 17단계: 차단·차단 해제·차단 목록, 신고.
 *
 * 차단은 blocks에 넣기만 하면 된다. 추천(10단계)·1:1 요청(13단계)이 BlockRepository.findHiddenUserIds로
 * 양방향 필터를 이미 하고 있어서, 여기서 따로 지울 것은 없다.
 */
@Service
@RequiredArgsConstructor
public class SafetyService {

    /** 프론트 components/safety.tsx REPORT_REASONS + 긴급 신고. 바꾸면 양쪽을 같이 바꾼다 */
    static final Set<String> REPORT_REASONS = Set.of(
        "원치 않는 연락 또는 만남 강요",
        "괴롭힘·스토킹",
        "차별·혐오 표현",
        "사기·불법 행위",
        "동의 없는 광고·스팸",
        "종교·정치·다단계 반복 권유",
        "기타",
        "긴급 신고"
    );
    static final int DETAIL_MAX = 1000;
    /** 같은 사람을 다시 신고하려면 이만큼 지나야 한다 (긴급 신고는 예외) */
    static final int REPORT_COOLDOWN_HOURS = 24;

    private final BlockRepository blockRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;

    /** 내가 차단한 사람, 최근 차단한 순 */
    @Transactional(readOnly = true)
    public List<BlockedUserResponse> blocks(Long userId) {
        return blockRepository.findByBlockerWithUser(userId).stream()
            .map(b -> new BlockedUserResponse(UserProfileResponse.of(b.getBlocked()), b.getCreatedAt()))
            .toList();
    }

    /** 차단. 이미 차단했으면 그대로 성공 (버튼을 두 번 눌러도 에러가 나지 않게) */
    @Transactional
    public BlockedUserResponse block(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new ApiException(ErrorCode.BLOCK_SELF);
        }
        User me = findUser(userId);
        User target = findUser(targetUserId);

        Block block = blockRepository.findById(new BlockId(userId, targetUserId))
            .orElseGet(() -> blockRepository.save(new Block(me, target)));
        LocalDateTime blockedAt = block.getCreatedAt() != null ? block.getCreatedAt() : LocalDateTime.now(clock);
        return new BlockedUserResponse(UserProfileResponse.of(target), blockedAt);
    }

    /** 차단 해제. 차단한 적이 없어도 성공 */
    @Transactional
    public void unblock(Long userId, Long targetUserId) {
        blockRepository.findById(new BlockId(userId, targetUserId))
            .ifPresent(blockRepository::delete);
    }

    /**
     * 신고. 운영팀만 조회하므로 상대에게 알림은 보내지 않는다.
     * 긴급 신고는 직접 연락·추가 모임 연결을 막기 위해 바로 차단까지 한다.
     */
    @Transactional
    public ReportResponse report(Long userId, CreateReportRequest req) {
        if (userId.equals(req.targetUserId())) {
            throw new ApiException(ErrorCode.REPORT_SELF);
        }
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (!REPORT_REASONS.contains(reason)) {
            throw new ApiException(ErrorCode.REPORT_REASON_INVALID);
        }
        String detail = clean(req.detail());
        boolean emergency = req.emergency() != null && req.emergency();

        User me = findUser(userId);
        User target = findUser(req.targetUserId());
        Room room = req.roomId() == null ? null
            : roomRepository.findById(req.roomId()).orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!emergency && reportRepository.existsByReporterIdAndTargetIdAndCreatedAtAfter(
                userId, target.getId(), now.minusHours(REPORT_COOLDOWN_HOURS))) {
            throw new ApiException(ErrorCode.REPORT_TOO_SOON);
        }

        Report saved = reportRepository.save(new Report(me, target, room, reason, detail, emergency));

        boolean blocked = false;
        if (emergency) {
            block(userId, target.getId());
            blocked = true;
        }
        return new ReportResponse(saved.getId(), emergency, blocked);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > DETAIL_MAX) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "상세 내용은 1000자까지 쓸 수 있어요");
        }
        return trimmed;
    }
}
