package com.chatssl.anytime.chat;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.chat.dto.ChatMessagesPageResponse;
import com.chatssl.anytime.chat.dto.ChatSummaryResponse;
import com.chatssl.anytime.chat.dto.UpdateChatLinkRequest;
import com.chatssl.anytime.common.CurrentUserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "채팅", description = "채팅방 목록·메시지 이력·읽음 처리·오픈채팅 링크 (13단계)")
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "내 채팅방 목록",
        description = "참여 중인 모임방 채팅과 1:1 DM 목록을 최근 활동 순으로 반환한다. 차단 관계인 DM은 제외한다.")
    @GetMapping
    public List<ChatSummaryResponse> list(@CurrentUserId Long userId) {
        return chatService.list(userId);
    }

    @Operation(summary = "채팅 메시지 이력 조회",
        description = "최신순(id DESC)으로 메시지를 페이지 조회하고, 호출자의 lastReadAt을 현재 시각으로 갱신한다.")
    @GetMapping("/{id}/messages")
    public ChatMessagesPageResponse messages(
        @CurrentUserId Long userId,
        @PathVariable Long id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return chatService.getMessages(userId, id, page, size);
    }

    @Operation(summary = "채팅방 읽음 처리",
        description = "호출자의 lastReadAt을 현재 시각으로 갱신한다.")
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@CurrentUserId Long userId, @PathVariable Long id) {
        chatService.markRead(userId, id);
    }

    @Operation(summary = "오픈채팅 링크 설정/제거",
        description = "모임방(ROOM) 채팅의 오픈채팅 링크를 설정한다. 빈 문자열 또는 null을 보내면 링크를 지운다.")
    @PatchMapping("/{id}/link")
    public ChatSummaryResponse updateLink(
        @CurrentUserId Long userId,
        @PathVariable Long id,
        @RequestBody(required = false) @Valid UpdateChatLinkRequest request
    ) {
        return chatService.updateLink(userId, id, request);
    }
}
