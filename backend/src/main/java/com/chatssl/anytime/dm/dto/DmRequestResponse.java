package com.chatssl.anytime.dm.dto;

import java.time.LocalDateTime;
import java.util.Locale;

import com.chatssl.anytime.domain.DmRequest;
import com.chatssl.anytime.user.dto.UserProfileResponse;

/** 1:1 요청 단건 응답 */
public record DmRequestResponse(
    Long id,
    UserProfileResponse fromUser,
    UserProfileResponse toUser,
    int day,
    int start,
    int end,
    String activity,
    String message,
    String status,
    Long chatId,
    LocalDateTime createdAt
) {

    public static DmRequestResponse of(DmRequest dm) {
        return new DmRequestResponse(
            dm.getId(),
            UserProfileResponse.of(dm.getFromUser()),
            UserProfileResponse.of(dm.getToUser()),
            dm.getDay(),
            dm.getStartMin(),
            dm.getEndMin(),
            dm.getActivity(),
            dm.getMessage(),
            dm.getStatus().name().toLowerCase(Locale.ROOT),
            dm.getChat() == null ? null : dm.getChat().getId(),
            dm.getCreatedAt()
        );
    }
}
