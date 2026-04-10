package com.noom.interview.fullstack.sleep

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class TestControllerIT : AbstractIntegrationTest() {
    @Test
    fun testHealth() {
        mockMvc
            .get("/test")
            .andExpect {
                status { isOk() }
                content { json("{\"testMessage\": \"Hello world!\"}") }
            }
    }
}
