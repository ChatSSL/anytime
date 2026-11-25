package com.chatssl.anytime.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.Interest;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomStatus;
import com.chatssl.anytime.domain.Slot;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.recommend.dto.PersonRecoResponse;
import com.chatssl.anytime.recommend.dto.RoomRecoResponse;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.timetable.dto.SlotItem;

/**
 * 프론트 derive.ts와 결과가 같은지 확인한다.
 * 기대값은 frontend/scripts/recommend-parity.ts를 돌린 결과를 그대로 옮겼다 (시드 data.sql과 같은 데이터, 수 10:40).
 */
class RecommendServiceTest {

    /** 2026-11-25는 수요일. 프론트 시연 시계 기본값(수 10:40)과 같게 맞춘다 */
    private static final LocalDate WED = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime NOW = WED.atTime(10, 40);

    private final UserRepository users = mock(UserRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomMemberRepository members = mock(RoomMemberRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);
    private final RecommendService service = new RecommendService(users, rooms, members, blocks, clock);

    private final Map<Long, User> seed = new HashMap<>();
    private final List<Room> seedRooms = new ArrayList<>();
    private final List<RoomMember> seedMembers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // data.sql 사용자: 관심사, 매너, 만나고 싶은 공강
        user(1, "데모유저", "36.8", List.of("코딩", "보드게임", "카페공부"),
            slot(0, 630, 780), slot(1, 720, 900), slot(2, 720, 840), slot(3, 630, 780), slot(4, 720, 840));
        user(2, "민트초코", "37.2", List.of("보드게임", "카페공부", "코딩"), slot(0, 660, 840), slot(2, 720, 840), slot(4, 720, 840));
        user(3, "라떼한잔", "36.9", List.of("산책", "사진", "전시", "카페공부"), slot(1, 720, 900), slot(2, 720, 900), slot(3, 720, 810));
        user(4, "코딩하는곰", "36.5", List.of("코딩", "스터디", "게임", "카탄 같은 보드게임"), slot(0, 690, 810), slot(2, 720, 780), slot(3, 630, 780));
        user(5, "새벽러닝", "38.0", List.of("운동", "산책", "점심"), slot(1, 720, 840), slot(3, 720, 840), slot(4, 660, 780));
        user(6, "영화광하루", "36.6", List.of("영화", "수다", "전시", "독서"), slot(0, 630, 780), slot(2, 660, 840), slot(4, 720, 810));
        user(7, "카탄마스터", "37.8", List.of("보드게임", "게임", "스터디", "점심"), slot(1, 630, 780), slot(2, 720, 840));
        user(8, "어학당", "36.4", List.of("언어교환", "독서", "카페공부", "수다"), slot(0, 720, 840), slot(2, 660, 840), slot(3, 720, 840));
        user(9, "연필한자루", "36.7", List.of("스케치", "전시", "음악", "산책"), slot(1, 720, 870), slot(4, 660, 810));
        user(10, "운영팀", "40", List.of("점심", "보드게임", "산책"));

        // data.sql 모임 (meet_date = 2026-11-25 기준 가장 가까운 그 요일)
        room(1, 7, 2, 720, 810, 6, false, 7, 4, 2);
        room(2, 3, 2, 750, 810, 4, false, 3, 5);
        room(3, 8, 2, 720, 840, 6, false, 8, 4, 6, 2);
        room(4, 6, 3, 720, 780, 5, false, 6);
        room(5, 10, 3, 690, 750, 8, true, 10);
        room(6, 7, 4, 720, 810, 6, false, 7, 4);
        room(7, 6, 2, 660, 720, 4, false, 6, 8);
        room(8, 4, 2, 720, 780, 3, false, 4, 7, 5);
        room(9, 10, 2, 750, 810, 8, true, 10);
        room(10, 8, 0, 720, 780, 6, false, 1, 8, 3);

        when(users.findById(1L)).thenReturn(Optional.of(seed.get(1L)));
        when(users.findRecommendCandidates(1L, RecommendService.SANCTION_SUSPENDED)).thenAnswer(inv -> seed.values().stream()
            .filter(u -> u.getId() != 1L && u.getSanction() < RecommendService.SANCTION_SUSPENDED && !u.getWantedSlots().isEmpty())
            .toList());
        when(rooms.findUpcoming(RoomStatus.OPEN, WED)).thenReturn(seedRooms);
        when(members.findByRoomIdInAndLeftAtIsNull(anyCollection())).thenReturn(seedMembers);
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of());
    }

    @Test
    void 사람_추천은_derive_ts와_순서_점수_이유가_같다() {
        List<PersonRecoResponse> res = service.people(1L);

        assertThat(res).extracting(p -> p.user().id()).containsExactly(2L, 4L, 7L, 8L, 5L, 3L, 9L, 6L);
        // 소수점 끝자리까지 같아야 한다 (JS number와 Java double은 같은 IEEE 754)
        assertThat(res).extracting(PersonRecoResponse::score).containsExactly(
            1.0372, 1.0365, 0.8578000000000001, 0.6764, 0.663, 0.6619, 0.6509857142857143, 0.6508857142857143);
        assertThat(res).extracting(PersonRecoResponse::reason).containsExactly(
            "월요일 11:00–13:00 공강이 겹쳐요 · 보드게임 취향이 비슷해요",
            "월요일 11:30–13:00 공강이 겹쳐요 · 보드게임 취향이 비슷해요",
            "화요일 12:00–13:00 공강이 겹쳐요 · 보드게임 취향이 비슷해요",
            "월요일 12:00–13:00 공강이 겹쳐요 · 카페공부 취향이 비슷해요",
            "화요일 12:00–14:00 공강이 겹쳐요 · 카페공부 취향이 비슷해요",
            "화요일 12:00–15:00 공강이 겹쳐요 · 카페공부 취향이 비슷해요",
            "화요일 12:00–14:30 공강이 겹쳐요 · 카페공부 취향이 비슷해요",
            "월요일 10:30–13:00 공강이 겹쳐요 · 카페공부 취향이 비슷해요");

        PersonRecoResponse mint = res.get(0);
        assertThat(mint.slots()).containsExactly(new SlotItem(0, 660, 780), new SlotItem(2, 720, 840), new SlotItem(4, 720, 840));
        assertThat(mint.shared()).containsExactly("보드게임", "게임", "카페공부", "코딩");
        assertThat(res.get(2).slots()).containsExactly(new SlotItem(1, 720, 780), new SlotItem(2, 720, 840));
    }

    @Test
    void 운영팀처럼_공강이_없는_사람과_나는_추천하지_않는다() {
        assertThat(service.people(1L)).extracting(p -> p.user().id()).doesNotContain(1L, 10L);
    }

    @Test
    void 차단은_양방향으로_사람과_그_사람이_방장인_모임을_뺀다() {
        // 2는 내가 차단, 7은 나를 차단 (findHiddenUserIds가 양쪽을 다 준다)
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(2L, 7L));

        assertThat(service.people(1L)).extracting(p -> p.user().id()).containsExactly(4L, 8L, 5L, 3L, 9L, 6L);
        assertThat(service.rooms(1L)).extracting(r -> r.room().id()).containsExactly(3L, 2L, 9L, 5L, 4L);
    }

    @Test
    void 모임_추천은_derive_ts와_순서가_같다() {
        List<RoomRecoResponse> res = service.rooms(1L);

        // 7: 수 11:00 시작이라 10:40엔 30분 전 마감, 8: 정원 3/3, 10: 내가 참여 중 → 빠진다
        assertThat(res).extracting(r -> r.room().id()).containsExactly(1L, 3L, 2L, 9L, 5L, 4L, 6L);
        assertThat(res).extracting(RoomRecoResponse::status).containsOnly(RoomPhase.OPEN);
        assertThat(res).extracting(RoomRecoResponse::fit).containsOnly(100);
        assertThat(res.get(0).slot()).isEqualTo(new SlotItem(2, 720, 810));
        assertThat(res.get(0).room().memberCount()).isEqualTo(3);
        assertThat(res.get(0).room().hostNick()).isEqualTo("카탄마스터");
    }

    @Test
    void 공강과_겹치지_않는_모임은_뒤로_가고_겹침_비율을_반올림한다() {
        // 수 11:30–12:30: 내 수요일 공강(12:00–14:00)과 30분 겹침 → 50%
        room(11, 3, 2, 690, 750, 4, false, 3, 5);
        // 목 09:00–10:00: 내 목요일 공강(10:30–13:00)과 안 겹침 → 0%, 맨 뒤
        room(12, 3, 3, 540, 600, 4, false, 3, 5);

        List<RoomRecoResponse> res = service.rooms(1L);

        RoomRecoResponse half = res.stream().filter(r -> r.room().id() == 11L).findFirst().orElseThrow();
        assertThat(half.fit()).isEqualTo(50);
        assertThat(half.slot()).isEqualTo(new SlotItem(2, 720, 750));
        assertThat(res.get(res.size() - 1).room().id()).isEqualTo(12L);
        assertThat(res.get(res.size() - 1).fit()).isZero();
        assertThat(res.get(res.size() - 1).slot()).isNull();
    }

    @Test
    void 방_상태는_지금_시각_기준으로_계산한다() {
        Room r = seedRooms.get(0); // 수 12:00–13:30, 3명, 정원 6
        assertThat(RoomPhase.of(r, 3, WED.atTime(10, 59))).isEqualTo(RoomPhase.OPEN);
        assertThat(RoomPhase.of(r, 3, WED.atTime(11, 0))).isEqualTo(RoomPhase.SOON);
        assertThat(RoomPhase.of(r, 3, WED.atTime(11, 30))).isEqualTo(RoomPhase.CLOSED);
        assertThat(RoomPhase.of(r, 6, WED.atTime(10, 0))).isEqualTo(RoomPhase.FULL);
        assertThat(RoomPhase.of(r, 1, WED.atTime(12, 0))).isEqualTo(RoomPhase.CANCELLED);
        assertThat(RoomPhase.of(r, 3, WED.atTime(13, 30))).isEqualTo(RoomPhase.ENDED);
        // 운영팀 모임은 혼자여도 취소되지 않는다
        assertThat(RoomPhase.of(seedRooms.get(4), 1, WED.plusDays(1).atTime(11, 40))).isEqualTo(RoomPhase.CLOSED);
    }

    @Test
    void 내_공강이_없으면_사람_추천은_비어_있다() {
        seed.get(1L).getWantedSlots().clear();
        assertThat(service.people(1L)).isEmpty();
    }

    @Test
    void 비공개_관심사는_상대_점수와_공통_취향에_쓰지_않는다() {
        User mint = seed.get(2L);
        mint.getInterests().clear();
        mint.getInterests().add(new Interest("보드게임", false));

        PersonRecoResponse res = service.people(1L).stream().filter(p -> p.user().id() == 2L).findFirst().orElseThrow();
        assertThat(res.shared()).isEmpty();
        assertThat(res.reason()).isEqualTo("월요일 11:00–13:00 공강이 겹쳐요");
        assertThat(res.user().interests()).isEmpty();
    }

    @Test
    void 관심사_개념_묶기와_유사도() {
        assertThat(InterestConcepts.concepts(List.of("카탄 같은 보드게임"))).containsExactly("보드게임", "게임");
        assertThat(InterestConcepts.similarity(List.of("코딩"), List.of("개발"))).isEqualTo(1.0);
        assertThat(InterestConcepts.similarity(List.of("요리"), List.of("뜨개질"))).isZero();
    }

    // ---- 시드 만들기 ----

    private static Slot slot(int day, int start, int end) {
        return new Slot(day, start, end);
    }

    private void user(long id, String nick, String manner, List<String> interests, Slot... wanted) {
        User u = new User("hash" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setManner(new BigDecimal(manner));
        interests.forEach(name -> u.getInterests().add(new Interest(name, true)));
        u.getWantedSlots().addAll(List.of(wanted));
        seed.put(id, u);
    }

    /** day 요일의 2026-11-25 이후(포함) 가장 가까운 날짜 (data.sql의 meet_date 계산과 같다) */
    private static LocalDate nextDate(int day) {
        int today = WED.getDayOfWeek().getValue() - 1; // 0=월
        return WED.plusDays((day - today + 7) % 7);
    }

    private void room(long id, long hostId, int day, int start, int end, int cap, boolean official, long... memberIds) {
        Room r = new Room(seed.get(hostId), "모임" + id, "활동", day, start, end, nextDate(day), "장소", cap, null);
        r.setId(id);
        r.setOfficial(official);
        seedRooms.add(r);
        for (long m : memberIds) {
            seedMembers.add(new RoomMember(r, seed.get(m)));
        }
    }
}
