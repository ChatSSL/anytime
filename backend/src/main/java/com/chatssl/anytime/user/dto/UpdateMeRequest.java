package com.chatssl.anytime.user.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/me. 보낸 항목만 바꾸고, 빼거나 null인 항목은 그대로 둔다.
 * interests를 보내면 목록 전체를 이걸로 바꾼다.
 */
public record UpdateMeRequest(
    String nick,
    @Size(max = 50, message = "학과는 50자까지 쓸 수 있어요") String dept,
    Boolean showDept,
    @Min(0) @Max(7) Integer avatar,
    @Size(min = 1, max = 8, message = "관심사는 1~8개 골라 주세요") List<@Valid InterestItem> interests
) {
}
