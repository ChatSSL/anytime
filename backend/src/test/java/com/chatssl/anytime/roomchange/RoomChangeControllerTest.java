package com.chatssl.anytime.roomchange;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
import com.chatssl.anytime.domain.ChangeStatus;
import com.chatssl.anytime.roomchange.dto.ChangeResponse;
import com.chatssl.anytime.roomchange.dto.ProposeChangeRequest;

class RoomChangeControllerTest {

    private final RoomChangeService service = mock(RoomChangeService.class);
    private MockMvc mvc;

    private static final ChangeResponse PENDING = new ChangeResponse(70L, 10L, ChangeStatus.PENDING,
        4, 750, 810, null, 4, 720, 780, "학생회관", LocalDate.of(2026, 11, 27), 2, 0, null, true, false);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RoomChangeController(service))
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
    void 제안은_201() throws Exception {
        when(service.propose(eq(1L), eq(10L), any(ProposeChangeRequest.class))).thenReturn(PENDING);

        mvc.perform(post("/api/rooms/10/changes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"day\": 4, \"start\": 750, \"end\": 810}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.newStart").value(750))
            .andExpect(jsonPath("$.required").value(2));
    }

    @Test
    void 대기_중인_변경이_없으면_204() throws Exception {
        when(service.pending(1L, 10L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/rooms/10/changes/pending")).andExpect(status().isNoContent());

        when(service.pending(1L, 10L)).thenReturn(Optional.of(PENDING));
        mvc.perform(get("/api/rooms/10/changes/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(70))
            .andExpect(jsonPath("$.canVote").value(true));
    }

    @Test
    void 투표() throws Exception {
        when(service.vote(1L, 70L, false)).thenReturn(PENDING);
        mvc.perform(post("/api/changes/70/vote").contentType(MediaType.APPLICATION_JSON).content("{\"accept\": false}"))
            .andExpect(status().isOk());
        verify(service).vote(1L, 70L, false);

        mvc.perform(post("/api/changes/70/vote").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        when(service.vote(1L, 71L, true)).thenThrow(new ApiException(ErrorCode.CHANGE_HOST_NO_VOTE));
        mvc.perform(post("/api/changes/71/vote").contentType(MediaType.APPLICATION_JSON).content("{\"accept\": true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHANGE_HOST_NO_VOTE"));
    }

    @Test
    void 요일이_없으면_400() throws Exception {
        mvc.perform(post("/api/rooms/10/changes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"start\": 750, \"end\": 810}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).propose(anyLong(), anyLong(), any());
        verify(service, never()).vote(anyLong(), anyLong(), anyBoolean());
    }
}
