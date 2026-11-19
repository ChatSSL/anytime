package com.chatssl.anytime;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AnytimeApplicationTests {

    @Test
    @Disabled("로컬 MySQL이 필요하다. DB 설정이 끝나면 @Disabled를 지운다")
    void contextLoads() {
    }
}
