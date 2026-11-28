package com.chatssl.anytime.dm;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.dm.dto.CreateDmRequest;
import com.chatssl.anytime.dm.dto.DmRequestListResponse;
import com.chatssl.anytime.dm.dto.DmRequestResponse;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.DmRequest;
import com.chatssl.anytime.domain.DmStatus;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.NotifResolution;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.NotificationService;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.DmRequestRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** 13단계: 1:1 만남(DM) 요청 생성, 목록 조회, 수락(DM 채팅방 생성/재사용), 거절 */
@Service
@RequiredArgsConstructor
public class DmRequestService {

    static final int MAX_DAY = 4;
    static final int MINUTES_PER_DAY = 1440;
    static final int SANCTION_SUSPENDED = 2;
    static final int ACTIVITY_MAX = 20;
    static final int MESSAGE_MAX = 200;

    private static final String[] DAYS = {"월", "화", "수", "목", "금"};

    private final DmRequestRepository dmRequestRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 1:1 만남 요청 전송.
     * 이미 DM 채팅방이 있거나 양방향 PENDING 요청이 있으면 409로 거절한다.
     */
    @Transactional
    public DmRequestResponse create(Long userId, CreateDmRequest req) {
        User from = findUser(userId);
        if (from.getSanction() >= SANCTION_SUSPENDED) {
            throw new ApiException(ErrorCode.USER_SUSPENDED);
        }
        if (userId.equals(req.targetUserId())) {
            throw new ApiException(ErrorCode.DM_REQUEST_SELF);
        }

        checkRange(req.day(), req.start(), req.end());
        String activity = cleanOptional(req.activity(), ACTIVITY_MAX, "활동은 20자까지 쓸 수 있어요");
        String message = cleanOptional(req.message(), MESSAGE_MAX, "메시지는 200자까지 쓸 수 있어요");

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        if (hidden.contains(req.targetUserId())) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        User to = userRepository.findById(req.targetUserId())
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (to.getSanction() >= SANCTION_SUSPENDED) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }

        if (!chatMemberRepository.findDmChatIdsBetween(userId, to.getId()).isEmpty()) {
            throw new ApiException(ErrorCode.DM_CHAT_ALREADY_EXISTS);
        }
        if (dmRequestRepository.existsPendingBetween(userId, to.getId())) {
            throw new ApiException(ErrorCode.DM_REQUEST_ALREADY_EXISTS);
        }

        DmRequest saved = dmRequestRepository.save(
            new DmRequest(from, to, req.day(), req.start(), req.end(), activity, message));

        String slotText = formatSlot(req.day(), req.start(), req.end());
        String body = activity == null
            ? from.getNick() + "님이 " + slotText + "에 1:1 만남을 요청했어요"
            : from.getNick() + "님이 " + slotText + "에 " + activity + " 활동을 같이 하고 싶어 해요";
        notificationService.notify(
            to,
            NotifType.REQUEST,
            "1:1 요청이 왔어요",
            body,
            null,
            from,
            true
        );

        return DmRequestResponse.of(saved);
    }

    /**
     * 받은 대기 요청(PENDING)과 보낸 요청 목록 조회. 차단 관계 사용자는 제외한다.
     */
    @Transactional(readOnly = true)
    public DmRequestListResponse list(Long userId) {
        findUser(userId);
        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);

        List<DmRequestResponse> received = dmRequestRepository.findReceivedWithUsers(userId, DmStatus.PENDING).stream()
            .filter(d -> !hidden.contains(d.getFromUser().getId()) && d.getFromUser().getSanction() < SANCTION_SUSPENDED)
            .map(DmRequestResponse::of)
            .toList();

        List<DmRequestResponse> sent = dmRequestRepository.findSentWithUsers(userId).stream()
            .filter(d -> !hidden.contains(d.getToUser().getId()))
            .map(DmRequestResponse::of)
            .toList();

        return new DmRequestListResponse(received, sent);
    }

    /**
     * 1:1 만남 요청 수락.
     * 기존 DM 채팅방이 있으면 재사용하고, 없으면 Chat.dm()과 양쪽 ChatMember를 생성한다.
     * 재사용 여부와 무관하게 합의된 시간 시스템 메시지와 요청 메시지를 남긴다.
     */
    @Transactional
    public DmRequestResponse accept(Long userId, Long requestId) {
        User me = findUser(userId);
        if (me.getSanction() >= SANCTION_SUSPENDED) {
            throw new ApiException(ErrorCode.USER_SUSPENDED);
        }

        DmRequest dmReq = dmRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new ApiException(ErrorCode.DM_REQUEST_NOT_FOUND));
        if (!dmReq.getToUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (dmReq.getStatus() != DmStatus.PENDING) {
            throw new ApiException(ErrorCode.DM_REQUEST_ALREADY_PROCESSED);
        }

        User fromUser = dmReq.getFromUser();
        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        if (hidden.contains(fromUser.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "차단 관계인 사용자의 요청은 수락할 수 없어요");
        }
        if (fromUser.getSanction() >= SANCTION_SUSPENDED) {
            throw new ApiException(ErrorCode.USER_SUSPENDED, "상대방 계정이 이용 정지되어 수락할 수 없어요");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> existingDmIds = chatMemberRepository.findDmChatIdsBetween(fromUser.getId(), userId);
        Chat chat;
        if (!existingDmIds.isEmpty()) {
            chat = chatRepository.findById(existingDmIds.get(0))
                .orElseThrow(() -> new ApiException(ErrorCode.CHAT_NOT_FOUND));
        } else {
            chat = chatRepository.save(Chat.dm());
            ChatMember cmFrom = new ChatMember(chat, fromUser);
            ChatMember cmTo = new ChatMember(chat, me);
            cmTo.setLastReadAt(now);
            chatMemberRepository.save(cmFrom);
            chatMemberRepository.save(cmTo);
        }

        String slotText = formatSlot(dmReq.getDay(), dmReq.getStartMin(), dmReq.getEndMin());
        messageRepository.save(new Message(chat, null, "서로 공강이 겹치는 " + slotText + "에 만나요"));
        if (dmReq.getMessage() != null && !dmReq.getMessage().isBlank()) {
            messageRepository.save(new Message(chat, fromUser, dmReq.getMessage()));
        }

        dmReq.setStatus(DmStatus.ACCEPTED);
        dmReq.setChat(chat);
        dmRequestRepository.save(dmReq);

        resolveRequestNotifications(userId, fromUser.getId(), NotifResolution.ACCEPTED, now);

        String body = dmReq.getActivity() == null
            ? me.getNick() + "님이 " + slotText + " 요청을 수락했어요"
            : me.getNick() + "님이 " + slotText + " " + dmReq.getActivity() + " 요청을 수락했어요";
        notificationService.notify(
            fromUser,
            NotifType.JOIN,
            "요청이 수락됐어요",
            body,
            "/chat/" + chat.getId(),
            me,
            false
        );

        return DmRequestResponse.of(dmReq);
    }

    /**
     * 1:1 만남 요청 거절.
     */
    @Transactional
    public DmRequestResponse decline(Long userId, Long requestId) {
        findUser(userId);
        DmRequest dmReq = dmRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new ApiException(ErrorCode.DM_REQUEST_NOT_FOUND));
        if (!dmReq.getToUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (dmReq.getStatus() != DmStatus.PENDING) {
            throw new ApiException(ErrorCode.DM_REQUEST_ALREADY_PROCESSED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        dmReq.setStatus(DmStatus.DECLINED);
        dmRequestRepository.save(dmReq);

        resolveRequestNotifications(userId, dmReq.getFromUser().getId(), NotifResolution.DECLINED, now);

        return DmRequestResponse.of(dmReq);
    }

    private void resolveRequestNotifications(Long toUserId, Long fromUserId, NotifResolution resolution, LocalDateTime now) {
        List<Notification> pending = notificationRepository.findPendingActionNotifications(
            toUserId, fromUserId, NotifType.REQUEST);
        for (Notification n : pending) {
            n.setResolved(resolution);
            n.setNeedsAction(false);
            if (n.getReadAt() == null) {
                n.setReadAt(now);
            }
            notificationRepository.save(n);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
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

    private static String formatSlot(int day, int startMin, int endMin) {
        return DAYS[day] + " " + formatHm(startMin) + "–" + formatHm(endMin);
    }

    private static String formatHm(int min) {
        return String.format("%02d:%02d", min / 60, min % 60);
    }
}
