package com.chatssl.anytime.recommend;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Interest;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomStatus;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.recommend.dto.PersonRecoResponse;
import com.chatssl.anytime.recommend.dto.RoomRecoResponse;
import com.chatssl.anytime.recommend.dto.RoomRecoResponse.RoomSummary;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.timetable.dto.SlotItem;
import com.chatssl.anytime.user.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

/**
 * 10단계: 사람 추천, 모임 추천. 프론트 src/lib/derive.ts의 recommendPeople(), recommendRooms(), roomFit()을 옮겼다.
 * 같은 시드면 프론트와 순서·점수·이유 문장까지 같게 나온다 (RecommendServiceTest가 확인한다).
 *
 * 프론트와 다른 점
 * - 상대의 공강도 상대가 '만나고 싶어요'로 켠 것(wanted_slots)만 쓴다. 프론트는 시드 사용자에게 이 값이 없어서
 *   수업 사이 공강 전체를 썼다. 시드에는 공강 전체를 넣어 두었으므로 결과는 같다.
 * - 상대 관심사는 공개한 것만 쓴다.
 * - 방 상태는 시연용 시계가 아니라 실제 지금 시각(한국 시간)으로 계산한다.
 * - 차단은 양방향이다 (내가 차단한 사람 + 나를 차단한 사람).
 */
@Service
@RequiredArgsConstructor
public class RecommendService {

    /** 이보다 짧게 겹치는 공강은 만나기 어려우니 뺀다 (프론트 intersectAll 기본값) */
    static final int MIN_OVERLAP = 60;
    /** 겹친 시간이 이만큼이면 시간 점수 만점 */
    static final int FULL_OVERLAP = 180;
    static final double TIME_WEIGHT = 0.55;
    static final double TASTE_WEIGHT = 0.45;
    /** 일시 정지(2) 이상은 추천하지 않는다 */
    static final int SANCTION_SUSPENDED = 2;
    static final int MAX_PEOPLE = 50;

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final BlockRepository blockRepository;
    private final Clock clock;

    /** 나와 공강이 60분 이상 겹치는 사람. 점수 높은 순 */
    @Transactional(readOnly = true)
    public List<PersonRecoResponse> people(Long userId) {
        User me = findUser(userId);
        List<SlotItem> mine = FreeTime.sorted(me.getWantedSlots());
        if (mine.isEmpty()) {
            return List.of();
        }
        // 내 관심사는 비공개도 쓴다(나에게만 보이는 결과). 순서는 GET /me와 같은 이름순
        List<String> myInterests = me.getInterests().stream().map(Interest::getName).sorted().toList();
        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);

        List<PersonRecoResponse> out = new ArrayList<>();
        for (User u : userRepository.findRecommendCandidates(userId, SANCTION_SUSPENDED)) {
            if (hidden.contains(u.getId())) {
                continue;
            }
            List<SlotItem> slots = FreeTime.intersectAll(mine, FreeTime.sorted(u.getWantedSlots()), MIN_OVERLAP);
            if (slots.isEmpty()) {
                continue;
            }
            List<String> theirs = u.getInterests().stream().filter(Interest::isPublic).map(Interest::getName).toList();
            List<String> shared = InterestConcepts.shared(myInterests, theirs);
            int minutes = slots.stream().mapToInt(s -> s.end() - s.start()).sum();
            double score = score(minutes, InterestConcepts.similarity(myInterests, theirs), u.getManner().doubleValue());
            out.add(new PersonRecoResponse(UserProfileResponse.of(u), slots, shared, score, reason(slots.get(0), shared)));
        }
        // 점수가 같으면 먼저 가입한 사람 먼저 (프론트는 USERS 배열 순서 = 시드 id 순)
        out.sort(Comparator.comparingDouble(PersonRecoResponse::score).reversed()
            .thenComparing(p -> p.user().id()));
        return out.size() > MAX_PEOPLE ? out.subList(0, MAX_PEOPLE) : out;
    }

    /**
     * 지금 합류할 수 있는 모임(모집 중·마감 임박) 중 내가 참여하지 않은 것.
     * 내 공강과 겹치는 모임이 먼저, 그 안에서는 빨리 시작하는 순.
     */
    @Transactional(readOnly = true)
    public List<RoomRecoResponse> rooms(Long userId) {
        User me = findUser(userId);
        List<SlotItem> mine = FreeTime.sorted(me.getWantedSlots());
        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        LocalDateTime now = LocalDateTime.now(clock);

        List<Room> rooms = roomRepository.findUpcoming(RoomStatus.OPEN, now.toLocalDate());
        Map<Long, Set<Long>> members = activeMembers(rooms);

        List<RoomRecoResponse> out = new ArrayList<>();
        for (Room r : rooms) {
            if (hidden.contains(r.getHost().getId())) {
                continue;
            }
            Set<Long> m = members.getOrDefault(r.getId(), Set.of());
            if (m.contains(userId)) {
                continue;
            }
            RoomPhase phase = RoomPhase.of(r, m.size(), now);
            if (!phase.joinable()) {
                continue;
            }
            SlotItem best = bestOverlap(r, mine);
            int fit = best == null ? 0 : fitPercent(r, best);
            out.add(new RoomRecoResponse(RoomSummary.of(r, m.size()), phase, fit, best));
        }
        out.sort(Comparator.<RoomRecoResponse>comparingInt(x -> x.fit() > 0 ? 0 : 1)
            .thenComparing(x -> x.room().meetDate())
            .thenComparingInt(x -> x.room().start())
            .thenComparing(x -> x.room().id()));
        return out;
    }

    /** 프론트와 같은 식. 계산 순서도 같아야 소수점 끝자리까지 같다 */
    static double score(int minutes, double similarity, double manner) {
        return TIME_WEIGHT * Math.min(1, minutes / (double) FULL_OVERLAP) + TASTE_WEIGHT * similarity + manner / 1000;
    }

    /** 예: 월요일 11:00–13:00 공강이 겹쳐요 · 보드게임 취향이 비슷해요 */
    static String reason(SlotItem first, List<String> shared) {
        String time = FreeTime.label(first) + " 공강이 겹쳐요";
        return shared.isEmpty() ? time : time + " · " + shared.get(0) + " 취향이 비슷해요";
    }

    /** 모임 시간과 가장 길게 겹치는 내 공강 구간 (프론트 roomFit().slot). 같으면 앞의 것 */
    static SlotItem bestOverlap(Room r, List<SlotItem> mine) {
        SlotItem roomSlot = new SlotItem(r.getDay(), r.getStartMin(), r.getEndMin());
        SlotItem best = null;
        int bestLen = 0;
        for (SlotItem f : mine) {
            SlotItem o = FreeTime.overlap(f, roomSlot);
            if (o != null && o.end() - o.start() > bestLen) {
                bestLen = o.end() - o.start();
                best = o;
            }
        }
        return best;
    }

    /** 모임 시간 중 겹치는 비율, 반올림 (프론트 roomFit().pct) */
    static int fitPercent(Room r, SlotItem overlap) {
        int dur = r.getEndMin() - r.getStartMin();
        return (int) Math.round(((double) (overlap.end() - overlap.start()) / dur) * 100);
    }

    /** 방 id → 지금 참여 중인 사용자 id */
    private Map<Long, Set<Long>> activeMembers(List<Room> rooms) {
        Map<Long, Set<Long>> out = new HashMap<>();
        if (rooms.isEmpty()) {
            return out;
        }
        List<Long> ids = rooms.stream().map(Room::getId).toList();
        for (RoomMember m : roomMemberRepository.findByRoomIdInAndLeftAtIsNull(ids)) {
            out.computeIfAbsent(m.getId().getRoomId(), k -> new HashSet<>()).add(m.getId().getUserId());
        }
        return out;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
