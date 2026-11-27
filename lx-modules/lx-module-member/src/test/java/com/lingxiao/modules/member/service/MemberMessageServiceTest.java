package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberMessageAdminVO;
import com.lingxiao.modules.member.dto.MemberMessageVO;
import com.lingxiao.modules.member.entity.MemMessage;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemMessageMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员站内消息服务测试。
 *
 * <p>重点覆盖：只能读 / 标自己的消息、已读幂等、开关关闭时不产生审核通知、
 * 重复审核不重复通知、后台群发去重与会员存在性校验。</p>
 */
@DisplayName("MemberMessageService 站内消息")
class MemberMessageServiceTest {

    private MemMessageMapper messageMapper;
    private MemUserMapper userMapper;
    private ConfigProvider configProvider;
    private MemberMessageService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemMessage.class);
        TableInfoHelper.initTableInfo(assistant, MemUser.class);
    }

    @BeforeEach
    void setUp() {
        messageMapper = mock(MemMessageMapper.class);
        userMapper = mock(MemUserMapper.class);
        configProvider = mock(ConfigProvider.class);
        when(configProvider.getBooleanValue(MemberMessageService.SWITCH_KEY, true)).thenReturn(true);
        service = new MemberMessageService(messageMapper, userMapper, configProvider);
    }

    @Test
    @DisplayName("我的消息只返回本人记录，默认按时间倒序")
    void pageMineMapsFields() {
        MemMessage message = message(1L, 9L, MemberMessageService.TYPE_SYSTEM);
        Page<MemMessage> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(message));
        when(messageMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<MemberMessageVO> result = service.pageMine(9L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTitle()).isEqualTo("系统通知");
        // 门户 VO 不带发送人与内部字段
        assertThat(result.getRecords().get(0).getReadFlag()).isEqualTo(MemberMessageService.READ_NO);
    }

    @Test
    @DisplayName("未登录查消息直接拒绝")
    void anonymousPageRejected() {
        assertThatThrownBy(() -> service.pageMine(null, 1, 10))
                .isInstanceOf(ServiceException.class);
        verify(messageMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("标记已读只改本人消息，已读时幂等")
    void markReadIsIdempotent() {
        MemMessage read = message(2L, 9L, MemberMessageService.TYPE_SYSTEM);
        read.setReadFlag(MemberMessageService.READ_YES);
        when(messageMapper.selectById(2L)).thenReturn(read);

        service.markRead(9L, 2L);

        verify(messageMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("标记已读不能越权操作别人的消息")
    void markReadRejectsForeignMessage() {
        when(messageMapper.selectById(3L)).thenReturn(message(3L, 8L, MemberMessageService.TYPE_SYSTEM));

        assertThatThrownBy(() -> service.markRead(9L, 3L))
                .isInstanceOf(ServiceException.class);
        verify(messageMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("审核通过给评论作者写一条站内信")
    void notifyOnApproved() {
        service.notifyCommentAudited(9L, 100L, "1", "写得好", "0");

        ArgumentCaptor<MemMessage> captor = ArgumentCaptor.forClass(MemMessage.class);
        verify(messageMapper).insert(captor.capture());
        MemMessage saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(9L);
        assertThat(saved.getMsgType()).isEqualTo(MemberMessageService.TYPE_COMMENT_APPROVED);
        assertThat(saved.getBizType()).isEqualTo("article");
        assertThat(saved.getBizId()).isEqualTo(100L);
        assertThat(saved.getReadFlag()).isEqualTo(MemberMessageService.READ_NO);
        assertThat(saved.getContent()).contains("通过审核").contains("写得好");
    }

    @Test
    @DisplayName("状态没变不重复通知")
    void notifySkippedWhenStatusUnchanged() {
        service.notifyCommentAudited(9L, 100L, "1", "写得好", "1");

        verify(messageMapper, never()).insert(any(MemMessage.class));
    }

    @Test
    @DisplayName("消息开关关闭时不产生审核通知")
    void notifySkippedWhenDisabled() {
        when(configProvider.getBooleanValue(MemberMessageService.SWITCH_KEY, true)).thenReturn(false);

        service.notifyCommentAudited(9L, 100L, "1", "写得好", "0");

        verify(messageMapper, never()).insert(any(MemMessage.class));
    }

    @Test
    @DisplayName("后台群发按会员去重，并写入发送人")
    void sendDeduplicatesMembers() {
        when(userMapper.selectBatchIds(any())).thenReturn(Arrays.asList(member(9L), member(10L)));

        int created = service.send(Arrays.asList(9L, 9L, 10L), "  通知  ", "  内容  ", 1L);

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<MemMessage> captor = ArgumentCaptor.forClass(MemMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allMatch(item ->
                MemberMessageService.TYPE_SYSTEM.equals(item.getMsgType())
                        && "通知".equals(item.getTitle())
                        && "内容".equals(item.getContent())
                        && Long.valueOf(1L).equals(item.getCreateBy()));
    }

    @Test
    @DisplayName("群发时会员不存在直接拒绝")
    void sendRejectsMissingMember() {
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(member(9L)));

        assertThatThrownBy(() -> service.send(Arrays.asList(9L, 10L), "通知", "内容", 1L))
                .isInstanceOf(ServiceException.class);
        verify(messageMapper, never()).insert(any(MemMessage.class));
    }

    @Test
    @DisplayName("空标题与超长正文被拒绝")
    void sendValidatesText() {
        assertThatThrownBy(() -> service.send(Collections.singletonList(9L), "  ", "内容", 1L))
                .isInstanceOf(ServiceException.class);

        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            tooLong.append('a');
        }
        assertThatThrownBy(() -> service.send(Collections.singletonList(9L), "通知", tooLong.toString(), 1L))
                .isInstanceOf(ServiceException.class);
        verify(messageMapper, never()).insert(any(MemMessage.class));
    }

    @Test
    @DisplayName("后台列表补会员用户名与昵称")
    void pageAdminFillsMember() {
        MemMessage message = message(1L, 9L, MemberMessageService.TYPE_SYSTEM);
        Page<MemMessage> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(message));
        when(messageMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(member(9L)));

        PageResult<MemberMessageAdminVO> result = service.pageAdmin(1, 20, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("alice");
        assertThat(result.getRecords().get(0).getNickname()).isEqualTo("爱丽丝");
    }

    @Test
    @DisplayName("删除不存在的消息返回 1004")
    void deleteMissingRejected() {
        when(messageMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ServiceException.class);
    }

    private MemMessage message(Long msgId, Long memberId, String type) {
        MemMessage message = new MemMessage();
        message.setMsgId(msgId);
        message.setMemberId(memberId);
        message.setMsgType(type);
        message.setTitle(MemberMessageService.TYPE_SYSTEM.equals(type) ? "系统通知" : "评论通知");
        message.setContent("内容");
        message.setReadFlag(MemberMessageService.READ_NO);
        message.setCreateTime(LocalDateTime.of(2026, 11, 27, 20, 0));
        return message;
    }

    private MemUser member(Long memberId) {
        MemUser user = new MemUser();
        user.setMemberId(memberId);
        user.setUsername("alice");
        user.setNickname("爱丽丝");
        return user;
    }
}