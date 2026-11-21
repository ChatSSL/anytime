package com.chatssl.anytime.domain;

import java.util.List;

/** chats.mission JSON. 프론트 Msg.ai의 questions, mission과 같은 모양. */
public record AiMission(List<String> questions, String mission) {
}
