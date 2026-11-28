package com.chatssl.anytime.room;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.CurrentUserIdResolver;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.common.GlobalExceptionHandler;
import com.chatssl.anytime.room.dto.CreateRoomRequest;
import com.chatssl.anytime.room.dto.LeaveRoomRequest;
import com.chatssl.anytime.room.dto.RoomDetailResponse;
import com.chatssl.anytime.room.dto.RoomResponse;
import com.chatssl.anytime.timetable.dto.SlotItem;
import com.chatssl.anytime.user.dto.UserProfileResponse;

class RoomControllerTest {

    private final RoomService roomService = mock(RoomService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(roomService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserIdResolver())
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 모임_생성_성공_시_201과_상세_JSON을_반환한다() throws Exception {
        RoomDetailResponse detail = new RoomDetailResponse(
            100L, "보드게임 같이 해요", "보드게임", 2, 720, 810, LocalDate.of(2026, 11, 25),
            "학생회관 1층 라운지", 4, 1, 1L, "데모유저", "가볍게 한 판", false,
            RoomPhase.OPEN, true, true, 200L, 100, new SlotItem(2, 720, 810),
            List.of(new UserProfileResponse(1L, "데모유저", "소프트웨어학과", 0, List.of("보드게임"), 36.8, 3)));
        when(roomService.create(eq(1L), any(CreateRoomRequest.class))).thenReturn(detail);

        mvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "보드게임 같이 해요",
                      "activity": "보드게임",
                      "day": 2,
                      "start": 720,
                      "end": 810,
                      "place": "학생회관 1층 라운지",
                      "cap": 4,
                      "topic": "가볍게 한 판"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(100))
            .andExpect(jsonPath("$.status").value("open"))
            .andExpect(jsonPath("$.isHost").value(true))
            .andExpect(jsonPath("$.joined").value(true))
            .andExpect(jsonPath("$.chatId").value(200))
            .andExpect(jsonPath("$.members[0].nick").value("데모유저"));
    }

    @Test
    void 입력값이_잘못되면_400_INVALID_INPUT() throws Exception {
        mvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "",
                      "activity": "보드게임",
                      "day": 2,
                      "start": 720,
                      "end": 810,
                      "place": "학생회관",
                      "cap": 4
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 모임_목록_필터_파라미터를_전달하고_소문자_상태를_반환한다() throws Exception {
        RoomResponse item = new RoomResponse(
            1L, "점심 보드게임", "보드게임", 2, 720, 810, LocalDate.of(2026, 11, 25),
            "학생회관", 6, 3, 7L, "카탄마스터", "카탄", false,
            RoomPhase.SOON, false, 100, new SlotItem(2, 720, 810));
        when(roomService.list(1L, 2, List.of("보드게임"), null, 600, 3, null, null, true, false))
            .thenReturn(List.of(item));

        mvc.perform(get("/api/rooms")
                .param("day", "2")
                .param("activity", "보드게임")
                .param("afterMin", "600")
                .param("minCap", "3")
                .param("fitOnly", "true")
                .param("mine", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].status").value("soon"))
            .andExpect(jsonPath("$[0].fit").value(100));

        verify(roomService).list(1L, 2, List.of("보드게임"), null, 600, 3, null, null, true, false);
    }

    @Test
    void 없는_모임_조회는_404_ROOM_NOT_FOUND() throws Exception {
        when(roomService.detail(1L, 999L)).thenThrow(new ApiException(ErrorCode.ROOM_NOT_FOUND));

        mvc.perform(get("/api/rooms/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 인증이_없으면_401_UNAUTHORIZED() throws Exception {
        SecurityContextHolder.clearContext();

        mvc.perform(get("/api/rooms"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 모임_합류_취소_방장취소_방장위임_엔드포인트가_200_상세를_반환한다() throws Exception {
        RoomDetailResponse detail = new RoomDetailResponse(
            10L, "보드게임", "보드게임", 2, 720, 810, LocalDate.of(2026, 11, 25),
            "학생회관", 4, 2, 2L, "민트초코", null, false,
            RoomPhase.OPEN, true, false, 55L, 100, new SlotItem(2, 720, 810), List.of());

        when(roomService.join(1L, 10L)).thenReturn(detail);
        when(roomService.leave(eq(1L), eq(10L), any(LeaveRoomRequest.class))).thenReturn(detail);
        when(roomService.cancel(1L, 10L)).thenReturn(detail);
        when(roomService.transferHost(1L, 10L, 2L)).thenReturn(detail);

        mvc.perform(post("/api/rooms/10/join"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.joined").value(true));

        mvc.perform(post("/api/rooms/10/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason": "그냥 못 가요"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10));

        mvc.perform(post("/api/rooms/10/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10));

        mvc.perform(post("/api/rooms/10/host")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId": 2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hostId").value(2));
    }

    @Test
    void 방장_위임_시_userId가_없으면_400_INVALID_INPUT() throws Exception {
        mvc.perform(post("/api/rooms/10/host")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 정원_마감_시_409_ROOM_FULL_권한없음_시_403_FORBIDDEN() throws Exception {
        when(roomService.join(1L, 10L)).thenThrow(new ApiException(ErrorCode.ROOM_FULL));
        when(roomService.cancel(1L, 10L)).thenThrow(new ApiException(ErrorCode.FORBIDDEN));

        mvc.perform(post("/api/rooms/10/join"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROOM_FULL"));

        mvc.perform(post("/api/rooms/10/cancel"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
