package com.chatssl.anytime.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.chatssl.anytime.auth.EmailCrypto;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Interest;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.user.dto.InterestItem;
import com.chatssl.anytime.user.dto.MeResponse;
import com.chatssl.anytime.user.dto.UpdateMeRequest;
import com.chatssl.anytime.user.dto.UserProfileResponse;

class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final EmailCrypto crypto = new EmailCrypto("test-hash-key", "test-enc-key");
    private final UserService service = new UserService(users, blocks, crypto);

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = user(1L, "데모유저", crypto.encrypt("me@dankook.ac.kr"));
        me.setDept("소프트웨어학과");
        other = user(2L, "민트초코", "SEED:u1@dankook.ac.kr");
        other.setDept("컴퓨터공학과");
        other.getInterests().add(new Interest("코딩", true));
        other.getInterests().add(new Interest("비밀취미", false));
        when(users.findById(1L)).thenReturn(Optional.of(me));
        when(users.findById(2L)).thenReturn(Optional.of(other));
        when(users.existsByNick("민트초코")).thenReturn(true);
        when(blocks.findHiddenUserIds(any())).thenReturn(Set.of());
    }

    @Test
    void 내_프로필에는_복호화한_이메일이_들어간다() {
        MeResponse res = service.getMe(1L);
        assertThat(res.email()).isEqualTo("me@dankook.ac.kr");
        assertThat(res.profileCompleted()).isFalse();
    }

    @Test
    void 시드_사용자_이메일은_복호화하지_않는다() {
        assertThat(service.getMe(2L).email()).isEqualTo("u1@dankook.ac.kr");
    }

    @Test
    void 프로필을_수정하면_관심사가_통째로_바뀌고_중복은_합친다() {
        MeResponse res = service.updateMe(1L, new UpdateMeRequest(" 새닉네임 ", null, null, 3,
            List.of(new InterestItem("코딩", null), new InterestItem(" 코딩 ", false), new InterestItem("산책", false))));

        assertThat(res.nick()).isEqualTo("새닉네임");
        assertThat(res.avatar()).isEqualTo(3);
        assertThat(res.dept()).isEqualTo("소프트웨어학과"); // 안 보낸 항목은 그대로
        assertThat(res.interests()).containsExactly(new InterestItem("산책", false), new InterestItem("코딩", true));
        assertThat(res.profileCompleted()).isTrue();
    }

    @Test
    void 남이_쓰는_닉네임은_409() {
        assertCode(() -> service.updateMe(1L, new UpdateMeRequest("민트초코", null, null, null, null)),
            ErrorCode.USER_NICK_TAKEN);
    }

    @Test
    void 동시에_같은_닉네임을_저장하면_409() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_users_nick"));
        assertCode(() -> service.updateMe(1L, new UpdateMeRequest("새닉네임", null, null, null, null)),
            ErrorCode.USER_NICK_TAKEN);
    }

    @Test
    void 내_닉네임을_그대로_보내도_된다() {
        assertThat(service.updateMe(1L, new UpdateMeRequest("데모유저", null, null, null, null)).nick())
            .isEqualTo("데모유저");
        assertThat(service.checkNick(1L, "데모유저").available()).isTrue();
    }

    @Test
    void 닉네임_길이와_띄어쓰기를_검사한다() {
        assertCode(() -> service.updateMe(1L, new UpdateMeRequest("a", null, null, null, null)), ErrorCode.INVALID_INPUT);
        assertCode(() -> service.updateMe(1L, new UpdateMeRequest("열한글자닉네임입니다요", null, null, null, null)),
            ErrorCode.INVALID_INPUT);
        assertCode(() -> service.updateMe(1L, new UpdateMeRequest("새 닉네임", null, null, null, null)),
            ErrorCode.INVALID_INPUT);
        assertThat(service.checkNick(1L, "민트초코").available()).isFalse();
        assertThat(service.checkNick(1L, "a").available()).isFalse();
    }

    @Test
    void 학과를_비우면_학과_공개도_꺼진다() {
        MeResponse res = service.updateMe(1L, new UpdateMeRequest(null, "  ", true, null, null));
        assertThat(res.dept()).isNull();
        assertThat(res.showDept()).isFalse();
    }

    @Test
    void 다른_사람_프로필에는_공개한_것만_보인다() {
        UserProfileResponse res = service.getProfile(1L, 2L);
        assertThat(res.dept()).isEqualTo("컴퓨터공학과");
        assertThat(res.interests()).containsExactly("코딩");

        other.setShowDept(false);
        assertThat(service.getProfile(1L, 2L).dept()).isNull();
    }

    @Test
    void 차단_관계면_404() {
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(2L));
        assertCode(() -> service.getProfile(1L, 2L), ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 영구_정지된_사용자는_404() {
        other.setSanction(3);
        assertCode(() -> service.getProfile(1L, 2L), ErrorCode.USER_NOT_FOUND);
    }

    private static User user(Long id, String nick, String emailEnc) {
        User u = new User("hash-" + id, emailEnc, "단국대학교", nick);
        u.setId(id);
        return u;
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo(code);
    }
}
