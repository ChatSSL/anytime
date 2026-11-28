package com.chatssl.anytime.dm.dto;

import java.util.List;

/** GET /api/dm-requests 응답 (받은 대기 요청 + 보낸 요청) */
public record DmRequestListResponse(
    List<DmRequestResponse> received,
    List<DmRequestResponse> sent
) {
}
