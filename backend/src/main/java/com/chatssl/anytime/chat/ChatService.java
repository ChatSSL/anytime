package com.chatssl.anytime.chat;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.chat.dto.ChatBroadcastMessage;
import com.chatssl.anytime.chat.dto.ChatMessageResponse;
import com.chatssl.anytime.chat.dto.ChatMessagesPageResponse;
import com.chatssl.anytime.chat.dto.ChatRoomInfo;
import com.chatssl.anytime.chat.dto.ChatSummaryResponse;
import com.chatssl.anytime.chat.dto.UpdateChatLinkRequest;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatKind;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.ChatMemberId;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.user.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

/** 13~14단계: 채팅방 목록, 메시지 페이지 조회·읽음 처리, 오픈채팅 링크 저장, 실시간 메시지 저장 */
@Service
@RequiredArgsConstructor
public class ChatService {

    static final int MAX_PAGE_SIZE = 100;
    static final int LINK_MAX = 255;
    static final int MESSAGE_MAX = 1000;

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 내가 참여 중인 채팅방 목록 조회 (ROOM + DM).
     * DM은 양방향 차단 관계인 상대방의 채팅방을 제외한다.
     */
    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> list(Long userId) {
        findUser(userId);
        List<ChatMember> memberships = chatMemberRepository.findByUserIdWithChat(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        List<Long> dmChatIds = new ArrayList<>();
        List<Long> roomIds = new ArrayList<>();
        for (ChatMember cm : memberships) {
            Chat chat = cm.getChat();
            if (chat.getKind() == ChatKind.DM) {
                dmChatIds.add(chat.getId());
            } else if (chat.getRoom() != null) {
                roomIds.add(chat.getRoom().getId());
            }
        }

        Map<Long, User> dmCounterparts = loadDmCounterparts(dmChatIds, userId);
        Map<Long, Integer> roomMemberCounts = loadActiveRoomCounts(roomIds);

        List<ChatMember> visible = new ArrayList<>();
        for (ChatMember cm : memberships) {
            Chat chat = cm.getChat();
            if (chat.getKind() == ChatKind.DM) {
                User other = dmCounterparts.get(chat.getId());
                if (other == null || hidden.contains(other.getId())) {
                    continue;
                }
            }
            visible.add(cm);
        }
        if (visible.isEmpty()) {
            return List.of();
        }

        List<Long> visibleChatIds = visible.stream().map(cm -> cm.getChat().getId()).toList();
        Map<Long, Message> latestMessages = new HashMap<>();
        for (Message m : messageRepository.findLatestByChatIdIn(visibleChatIds)) {
            latestMessages.put(m.getChat().getId(), m);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<ChatSummaryResponse> out = new ArrayList<>(visible.size());
        for (ChatMember cm : visible) {
            Chat chat = cm.getChat();
            ChatRoomInfo roomInfo = null;
            UserProfileResponse otherUser = null;
            if (chat.getKind() == ChatKind.ROOM && chat.getRoom() != null) {
                Room room = chat.getRoom();
                int count = roomMemberCounts.getOrDefault(room.getId(), 0);
                roomInfo = ChatRoomInfo.of(room, count, RoomPhase.of(room, count, now));
            } else if (chat.getKind() == ChatKind.DM) {
                otherUser = UserProfileResponse.of(dmCounterparts.get(chat.getId()));
            }

            Message latest = latestMessages.get(chat.getId());
            ChatMessageResponse lastDto = latest == null ? null : ChatMessageResponse.of(latest, userId);
            long unread = countUnread(chat.getId(), cm.getLastReadAt());
            out.add(ChatSummaryResponse.of(chat, roomInfo, otherUser, lastDto, unread));
        }

        out.sort(Comparator.comparing(ChatSummaryResponse::lastActivityAt,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ChatSummaryResponse::id, Comparator.reverseOrder()));
        return out;
    }

    /**
     * 채팅 메시지 이력 페이지 조회 (최신순: id DESC) 및 마지막 읽은 시각 갱신.
     */
    @Transactional
    public ChatMessagesPageResponse getMessages(Long userId, Long chatId, int page, int size) {
        findUser(userId);
        if (page < 0 || size <= 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "페이지 정보를 확인해 주세요");
        }
        ChatAccess access = verifyAccess(userId, chatId);

        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Page<Message> messagePage = messageRepository.findPageWithSenderByChatId(chatId, PageRequest.of(page, safeSize));

        access.member().setLastReadAt(LocalDateTime.now(clock));
        chatMemberRepository.save(access.member());

        return ChatMessagesPageResponse.of(chatId, messagePage, userId);
    }

    /**
     * 채팅방 읽음 처리 (ChatMember.lastReadAt 갱신).
     */
    @Transactional
    public void markRead(Long userId, Long chatId) {
        findUser(userId);
        ChatAccess access = verifyAccess(userId, chatId);
        access.member().setLastReadAt(LocalDateTime.now(clock));
        chatMemberRepository.save(access.member());
    }

    /**
     * 모임방 오픈채팅 링크 저장/제거.
     * ROOM 채팅방의 활성 참여자만 수정할 수 있으며, 빈 문자열/null은 링크를 제거한다.
     */
    @Transactional
    public ChatSummaryResponse updateLink(Long userId, Long chatId, UpdateChatLinkRequest req) {
        findUser(userId);
        ChatAccess access = verifyAccess(userId, chatId);
        Chat chat = access.chat();
        if (chat.getKind() != ChatKind.ROOM) {
            throw new ApiException(ErrorCode.CHAT_LINK_NOT_ALLOWED);
        }

        String normalized = normalizeLink(req == null ? null : req.link());
        chat.setLink(normalized);
        chatRepository.save(chat);

        LocalDateTime now = LocalDateTime.now(clock);
        ChatRoomInfo roomInfo = null;
        if (chat.getRoom() != null) {
            Room room = chat.getRoom();
            int count = (int) roomMemberRepository.countByRoomIdAndLeftAtIsNull(room.getId());
            roomInfo = ChatRoomInfo.of(room, count, RoomPhase.of(room, count, now));
        }

        List<Message> latest = messageRepository.findLatestByChatIdIn(List.of(chatId));
        ChatMessageResponse lastDto = latest.isEmpty() ? null : ChatMessageResponse.of(latest.get(0), userId);
        long unread = countUnread(chatId, access.member().getLastReadAt());
        return ChatSummaryResponse.of(chat, roomInfo, null, lastDto, unread);
    }

    /**
     * 14단계 STOMP 실시간 메시지 저장.
     * - 제재 상태(sanction >= 2), 채팅방 참여 여부, DM 양방향 차단, 본문 공백/1000자 제한 검증
     * - DB 저장 및 발신자 lastReadAt 갱신이 트랜잭션 커밋까지 성공한 뒤에만 컨트롤러가 브로드캐스트한다.
     */
    @Transactional
    public ChatBroadcastMessage sendMessage(Long userId, Long chatId, String rawText) {
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        if (chatId == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "채팅방 정보를 확인해 주세요");
        }
        User sender = findUser(userId);
        if (sender.getSanction() >= 2) {
            throw new ApiException(ErrorCode.USER_SUSPENDED);
        }
        if (rawText == null || rawText.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "메시지를 입력해 주세요");
        }
        String trimmed = rawText.trim();
        if (trimmed.length() > MESSAGE_MAX) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "메시지는 1000자까지 쓸 수 있어요");
        }

        ChatAccess access = verifyAccess(userId, chatId);
        LocalDateTime now = LocalDateTime.now(clock);
        Message saved = messageRepository.saveAndFlush(new Message(access.chat(), sender, trimmed));
        access.member().setLastReadAt(now);
        chatMemberRepository.save(access.member());

        return ChatBroadcastMessage.of(saved, now);
    }

    private ChatAccess verifyAccess(Long userId, Long chatId) {
        Chat chat = chatRepository.findWithRoomById(chatId)
            .orElseThrow(() -> new ApiException(ErrorCode.CHAT_NOT_FOUND));
        ChatMember member = chatMemberRepository.findById(new ChatMemberId(chatId, userId))
            .orElseThrow(() -> new ApiException(ErrorCode.CHAT_FORBIDDEN));

        if (chat.getKind() == ChatKind.DM) {
            Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
            boolean blockedCounterpart = chatMemberRepository.findByChatIdWithUser(chatId).stream()
                .map(ChatMember::getUser)
                .anyMatch(u -> !u.getId().equals(userId) && hidden.contains(u.getId()));
            if (blockedCounterpart) {
                throw new ApiException(ErrorCode.CHAT_NOT_FOUND);
            }
        }
        return new ChatAccess(chat, member);
    }

    private Map<Long, User> loadDmCounterparts(List<Long> dmChatIds, Long userId) {
        Map<Long, User> counterparts = new HashMap<>();
        if (dmChatIds.isEmpty()) {
            return counterparts;
        }
        for (ChatMember cm : chatMemberRepository.findByChatIdInWithUser(dmChatIds)) {
            if (!cm.getUser().getId().equals(userId)) {
                counterparts.put(cm.getId().getChatId(), cm.getUser());
            }
        }
        return counterparts;
    }

    private Map<Long, Integer> loadActiveRoomCounts(List<Long> roomIds) {
        Map<Long, Set<Long>> byRoom = new HashMap<>();
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        for (RoomMember rm : roomMemberRepository.findByRoomIdInAndLeftAtIsNull(roomIds)) {
            byRoom.computeIfAbsent(rm.getId().getRoomId(), k -> new HashSet<>()).add(rm.getId().getUserId());
        }
        Map<Long, Integer> counts = new HashMap<>();
        byRoom.forEach((k, v) -> counts.put(k, v.size()));
        return counts;
    }

    private long countUnread(Long chatId, LocalDateTime lastReadAt) {
        return lastReadAt == null
            ? messageRepository.countByChatId(chatId)
            : messageRepository.countByChatIdAndCreatedAtAfter(chatId, lastReadAt);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private static String normalizeLink(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > LINK_MAX) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "링크는 255자까지 쓸 수 있어요");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "http:// 또는 https://로 시작하는 링크를 입력해 주세요");
        }
        try {
            URI uri = URI.create(trimmed);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "올바른 링크 주소를 입력해 주세요");
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "올바른 링크 주소를 입력해 주세요");
        }
        return trimmed;
    }

    private record ChatAccess(Chat chat, ChatMember member) {
    }
}
