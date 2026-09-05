package com.course.platform.infra.docking.impl;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.external.ApiHttpClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnwangDockingStrategyTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void progressQuerySendsOriginalCredentialsInsteadOfLogMasks() {
        ApiHttpClient client = mock(ApiHttpClient.class);
        when(client.getForString(eq("https://provider.example/api/search"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"process\":\"50%\",\"status\":\"进行中\"}]}");

        ApiProvider provider = new ApiProvider();
        provider.setApiUrl("https://provider.example");
        provider.setUsername("provider-uid");
        provider.setApiKey("provider-key");
        CoursePlatform platform = new CoursePlatform();
        platform.setDockParam("platform-1");
        CourseOrder order = new CourseOrder();
        order.setStudentAccount("student-account");
        order.setCourseName("大学英语");

        new AnwangDockingStrategy(client).queryOrderProgress(order, platform, provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).getForString(eq("https://provider.example/api/search"), captor.capture());
        Map<String, Object> sent = captor.getValue();
        assertEquals("provider-uid", sent.get("uid"));
        assertEquals("provider-key", sent.get("key"));
        assertEquals("student-account", sent.get("username"));
        assertEquals("大学英语", sent.get("kcname"));
        assertEquals("platform-1", sent.get("cid"));
    }
}
