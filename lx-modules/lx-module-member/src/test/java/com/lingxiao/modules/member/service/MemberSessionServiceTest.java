package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberSessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员登录设备服务测试。
 *
 * <p>重点覆盖：当前设备排最前、过期会话不展示、会话句柄不是 Token 本身、
 * 不能踢当前设备、只能踢自己的、退出其他设备不影响当前。</p>
 */
@DisplayName("MemberSessionService 登录设备")
class MemberSessionServiceTest {

    private MemberSessionStore store;
    private MemberSessionService service;

    @BeforeEach
    void setUp() {
        store = mock(MemberSessionStore.class);
        service = new MemberSessionService(store);
    }

    @Test
    @DisplayName("当前设备排最前，过期会话被过滤")
    void listPutsCurrentFirstAndSkipsExpired() {
        when(store.currentToken()).thenReturn("token-current");
        when(store.tokens(9L)).thenReturn(Arrays.asList("token-current", "token-old", "token-expired"));
        when(store.timeout("token-current")).thenReturn(3600L);
        when(store.timeout("token-old")).thenReturn(1200L);
        when(store.timeout("token-expired")).thenReturn(-2L);
        when(store.context(anyString())).thenReturn(
                new MemberSessionStore.SessionContext("10.0.0.2", "Mozilla/5.0 (Windows NT 10.0) Chrome/120", LocalDateTime.of(2026, 11, 27, 9, 0)));

        List<MemberSessionVO> result = service.list(9L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCurrentSession()).isTrue();
        assertThat(result.get(0).getDevice()).isEqualTo("Windows · Chrome");
        assertThat(result.get(0).getLoginIp()).isEqualTo("10.0.0.2");
        // 句柄是哈希，不是原始 Token
        assertThat(result.get(0).getSessionId()).hasSize(16).isNotEqualTo("token-current");
        assertThat(result.get(1).getCurrentSession()).isFalse();
    }

    @Test
    @DisplayName("未登录不返回会话")
    void listRequiresMember() {
        assertThatThrownBy(() -> service.list(null))
                .isInstanceOf(ServiceException.class);
        verify(store, never()).tokens(any());
    }

    @Test
    @DisplayName("不能踢当前设备")
    void kickRejectsCurrentSession() {
        when(store.currentToken()).thenReturn("token-current");
        when(store.tokens(9L)).thenReturn(Collections.singletonList("token-current"));
        when(store.timeout("token-current")).thenReturn(3600L);
        when(store.context(anyString())).thenReturn(new MemberSessionStore.SessionContext(null, null, null));
        // 用同一套 hash 从列表里拿当前设备的句柄
        String currentHandle = service.list(9L).get(0).getSessionId();

        assertThatThrownBy(() -> service.kick(9L, currentHandle))
                .isInstanceOf(ServiceException.class);
        verify(store, never()).logout(anyString());
    }

    @Test
    @DisplayName("踢掉指定设备")
    void kickOtherSession() {
        when(store.currentToken()).thenReturn("token-current");
        when(store.tokens(9L)).thenReturn(Arrays.asList("token-current", "token-old"));
        when(store.timeout(anyString())).thenReturn(3600L);
        when(store.context(anyString())).thenReturn(new MemberSessionStore.SessionContext(null, null, null));
        String oldHandle = service.list(9L).get(1).getSessionId();

        service.kick(9L, oldHandle);

        verify(store).logout("token-old");
    }

    @Test
    @DisplayName("未知会话句柄返回 1004")
    void kickUnknownSession() {
        when(store.currentToken()).thenReturn("token-current");
        when(store.tokens(9L)).thenReturn(Collections.singletonList("token-current"));

        assertThatThrownBy(() -> service.kick(9L, "ffffffffffffffff"))
                .isInstanceOf(ServiceException.class);
        verify(store, never()).logout(anyString());
    }

    @Test
    @DisplayName("退出其他设备保留当前设备")
    void kickOthersKeepsCurrent() {
        when(store.currentToken()).thenReturn("token-current");
        when(store.tokens(9L)).thenReturn(Arrays.asList("token-current", "token-a", "token-b"));

        int kicked = service.kickOthers(9L);

        assertThat(kicked).isEqualTo(2);
        verify(store).logout("token-a");
        verify(store).logout("token-b");
        verify(store, never()).logout("token-current");
    }

    @Test
    @DisplayName("设备名识别：Android / 未知")
    void deviceLabelFallback() {
        when(store.currentToken()).thenReturn("t1");
        when(store.tokens(9L)).thenReturn(Arrays.asList("t1", "t2"));
        when(store.timeout(anyString())).thenReturn(600L);
        when(store.context("t1")).thenReturn(
                new MemberSessionStore.SessionContext(null, "Mozilla/5.0 (Linux; Android 14) AppleWebKit", null));
        when(store.context("t2")).thenReturn(new MemberSessionStore.SessionContext(null, "", null));

        List<MemberSessionVO> result = service.list(9L);

        assertThat(result.get(0).getDevice()).isEqualTo("Android");
        assertThat(result.get(1).getDevice()).isEqualTo("未知设备");
    }
}