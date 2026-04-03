package com.example.bank;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("CI에서 전체 컨텍스트 로드 테스트는 현재 사용하지 않음")
class BankApplicationTests {

    @Test
    void contextLoads() {
    }

}
