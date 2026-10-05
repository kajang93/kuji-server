package com.kuji.backend.domain.threads.client;

import com.kuji.backend.domain.threads.dto.ThreadsAccountInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ThreadsApiClientTest {

    @InjectMocks
    private ThreadsApiClient threadsApiClient;

    @Test
    @DisplayName("설정된 user-id가 있으면 해당 id를 반환하고 없으면 me를 반환한다")
    void getTargetUserId() {
        // given
        ReflectionTestUtils.setField(threadsApiClient, "configuredUserId", "28861832050080267");

        // when & then
        assertThat(threadsApiClient.getTargetUserId()).isEqualTo("28861832050080267");

        // given - empty
        ReflectionTestUtils.setField(threadsApiClient, "configuredUserId", "");
        assertThat(threadsApiClient.getTargetUserId()).isEqualTo("me");
    }
}
