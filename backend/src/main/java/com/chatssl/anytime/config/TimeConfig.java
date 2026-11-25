package com.chatssl.anytime.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버의 '지금'. 모임 시간(meet_date + start_min)은 한국 시간이라, 서버 VM이 UTC여도 한국 시간으로 계산한다.
 * 시간에 따라 결과가 바뀌는 코드(방 상태, 크론)는 LocalDateTime.now() 대신 이 Clock을 주입받아
 * LocalDateTime.now(clock)을 쓴다. 테스트에서는 Clock.fixed(...)로 원하는 시각을 넣을 수 있다.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
