package com.chatssl.anytime.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.auth.EmailCrypto;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Interest;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.user.dto.InterestItem;
import com.chatssl.anytime.user.dto.MeResponse;
import com.chatssl.anytime.user.dto.NickCheckResponse;
import com.chatssl.anytime.user.dto.UpdateMeRequest;
import com.chatssl.anytime.user.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

/** 8단계: 내 프로필 조회·수정, 닉네임 중복 검사, 다른 사람 프로필 조회 */
@Service
@RequiredArgsConstructor
public class UserService {

    static final int NICK_MIN = 2;
    static final int NICK_MAX = 10;
    /** 영구 정지된 계정은 다른 사람에게 없는 사용자처럼 보인다 */
    private static final int SANCTION_BANNED = 3;
    /** data.sql 시드 사용자는 암호문 대신 'SEED:원문'으로 들어 있다 */
    private static final String SEED_PREFIX = "SEED:";

    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final EmailCrypto emailCrypto;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = findUser(userId);
        return MeResponse.of(user, emailOf(user));
    }

    @Transactional
    public MeResponse updateMe(Long userId, UpdateMeRequest req) {
        User user = findUser(userId);

        if (req.nick() != null) {
            String nick = validNick(req.nick());
            if (!nick.equals(user.getNick())) {
                if (userRepository.existsByNick(nick)) {
                    throw new ApiException(ErrorCode.USER_NICK_TAKEN);
                }
                user.setNick(nick);
            }
        }
        if (req.dept() != null) {
            String dept = req.dept().trim();
            user.setDept(dept.isEmpty() ? null : dept);
        }
        if (req.showDept() != null) {
            user.setShowDept(req.showDept());
        }
        // 학과가 없으면 공개할 것도 없다
        if (user.getDept() == null) {
            user.setShowDept(false);
        }
        if (req.avatar() != null) {
            user.setAvatar(req.avatar());
        }
        if (req.interests() != null) {
            replaceInterests(user, req.interests());
        }

        try {
            // 중복 검사와 저장 사이에 다른 사람이 같은 닉네임을 가져가면 UNIQUE 제약에 걸린다
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.USER_NICK_TAKEN);
        }
        return MeResponse.of(user, emailOf(user));
    }

    /** 프로필 화면의 '중복 확인' 버튼. 내가 이미 쓰는 닉네임은 쓸 수 있다고 답한다 */
    @Transactional(readOnly = true)
    public NickCheckResponse checkNick(Long userId, String rawNick) {
        String nick;
        try {
            nick = validNick(rawNick);
        } catch (ApiException e) {
            return new NickCheckResponse(false, e.getMessage());
        }
        boolean mine = userRepository.findById(userId).map(u -> u.getNick().equals(nick)).orElse(false);
        if (!mine && userRepository.existsByNick(nick)) {
            return new NickCheckResponse(false, ErrorCode.USER_NICK_TAKEN.getMessage());
        }
        return new NickCheckResponse(true, "쓸 수 있는 닉네임이에요");
    }

    /** 다른 사람 프로필. 차단 관계(어느 쪽이든)거나 영구 정지면 없는 사용자처럼 404 */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long viewerId, Long targetId) {
        User target = findUser(targetId);
        if (!targetId.equals(viewerId) && blockRepository.findHiddenUserIds(viewerId).contains(targetId)) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        if (target.getSanction() >= SANCTION_BANNED) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        return UserProfileResponse.of(target);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    /** 앞뒤 공백을 지우고 길이·문자를 검사한 닉네임 */
    static String validNick(String raw) {
        String nick = raw == null ? "" : raw.trim();
        if (nick.length() < NICK_MIN || nick.length() > NICK_MAX) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "닉네임은 " + NICK_MIN + "~" + NICK_MAX + "자로 정해 주세요");
        }
        if (nick.chars().anyMatch(Character::isWhitespace)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "닉네임에는 띄어쓰기를 쓸 수 없어요");
        }
        return nick;
    }

    /** 관심사 목록을 통째로 바꾼다. 앞뒤 공백을 지우고, 같은 이름은 처음 것만 남긴다 */
    private static void replaceInterests(User user, List<InterestItem> items) {
        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (InterestItem item : items) {
            String name = item.name().trim();
            if (!name.isEmpty()) {
                unique.putIfAbsent(name, item.publicOrDefault());
            }
        }
        if (unique.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "관심사를 하나 이상 골라 주세요");
        }
        user.getInterests().clear();
        unique.forEach((name, isPublic) -> user.getInterests().add(new Interest(name, isPublic)));
    }

    private String emailOf(User user) {
        String enc = user.getEmailEnc();
        return enc.startsWith(SEED_PREFIX) ? enc.substring(SEED_PREFIX.length()) : emailCrypto.decrypt(enc);
    }
}
