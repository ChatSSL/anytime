package com.chatssl.anytime.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.user.dto.MeResponse;
import com.chatssl.anytime.user.dto.NickCheckResponse;
import com.chatssl.anytime.user.dto.UpdateMeRequest;
import com.chatssl.anytime.user.dto.UserProfileResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "프로필", description = "내 프로필 조회·수정과 다른 사용자 프로필 (8단계)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필",
        description = "이메일, 비공개 관심사까지 본인에게만 주는 정보. "
            + "profileCompleted=false면 아직 프로필 만들기(닉네임·관심사)를 안 한 사용자다.")
    @GetMapping("/me")
    public MeResponse me(@CurrentUserId Long userId) {
        return userService.getMe(userId);
    }

    @Operation(summary = "내 프로필 수정",
        description = "보낸 항목만 바꾼다. 닉네임 2~10자(띄어쓰기 불가), 겹치면 409 USER_NICK_TAKEN. "
            + "interests는 1~8개, 보내면 목록 전체를 바꾼다(isPublic 생략 시 공개). 학과를 비우면 학과 공개도 꺼진다.")
    @PatchMapping("/me")
    public MeResponse updateMe(@CurrentUserId Long userId, @Valid @RequestBody UpdateMeRequest request) {
        return userService.updateMe(userId, request);
    }

    @Operation(summary = "닉네임 중복 확인",
        description = "프로필 화면의 '중복 확인' 버튼용. 저장은 하지 않는다. 내가 지금 쓰는 닉네임은 available=true.")
    @GetMapping("/users/check-nick")
    public NickCheckResponse checkNick(@CurrentUserId Long userId, @RequestParam String nick) {
        return userService.checkNick(userId, nick);
    }

    @Operation(summary = "다른 사용자 프로필",
        description = "닉네임, 공개한 학과·관심사, 매너 점수, 완료한 모임 수만 준다. 이메일·시간표는 주지 않는다. "
            + "차단 관계(어느 쪽이든)면 404 USER_NOT_FOUND.")
    @GetMapping("/users/{id}")
    public UserProfileResponse user(@CurrentUserId Long userId, @PathVariable Long id) {
        return userService.getProfile(userId, id);
    }
}
