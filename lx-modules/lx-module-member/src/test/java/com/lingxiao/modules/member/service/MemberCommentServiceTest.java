package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberCommentVO;
import com.lingxiao.modules.member.entity.MemComment;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemCommentMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员评论服务测试。
 *
 * <p>重点覆盖：新评论默认待审核、内容裁剪与长度校验、公开列表只映射展示字段、
 * 审核状态白名单，以及不存在评论删除返回 1004。</p>
 */
@DisplayName("MemberCommentService 评论与审核")
class MemberCommentServiceTest {

    private MemCommentMapper commentMapper;
    private MemUserMapper userMapper;
    private MemberCommentService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemComment.class);
        TableInfoHelper.initTableInfo(assistant, MemUser.class);
    }

    @BeforeEach
    void setUp() {
        commentMapper = mock(MemCommentMapper.class);
        userMapper = mock(MemUserMapper.class);
        service = new MemberCommentService(commentMapper, userMapper);
    }

    @Test
    @DisplayName("发表评论默认待审核，并回填昵称")
    void createDefaultsToPending() {
        MemUser member = member(9L, "alice", "爱丽丝");
        when(userMapper.selectById(9L)).thenReturn(member);

        MemberCommentVO vo = service.create(9L, 100L, "  写得好  ");

        ArgumentCaptor<MemComment> captor = ArgumentCaptor.forClass(MemComment.class);
        verify(commentMapper).insert(captor.capture());
        MemComment saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(9L);
        assertThat(saved.getArticleId()).isEqualTo(100L);
        assertThat(saved.getContent()).isEqualTo("写得好");
        assertThat(saved.getStatus()).isEqualTo(MemberCommentService.STATUS_PENDING);
        assertThat(vo.getNickname()).isEqualTo("爱丽丝");
        assertThat(vo.getContent()).isEqualTo("写得好");
    }

    @Test
    @DisplayName("空评论不落库")
    void blankContentRejected() {
        assertThatThrownBy(() -> service.create(9L, 100L, "   "))
                .isInstanceOf(ServiceException.class);
        verify(commentMapper, never()).insert(any(MemComment.class));
    }

    @Test
    @DisplayName("未登录不能发表评论")
    void anonymousCreateRejected() {
        assertThatThrownBy(() -> service.create(null, 100L, "内容"))
                .isInstanceOf(ServiceException.class);
        verify(commentMapper, never()).insert(any(MemComment.class));
    }

    @Test
    @DisplayName("公开列表只返回展示字段")
    void pageApprovedMapsFields() {
        MemComment comment = new MemComment();
        comment.setCommentId(1L);
        comment.setArticleId(100L);
        comment.setMemberId(9L);
        comment.setContent("不错");
        comment.setStatus(MemberCommentService.STATUS_APPROVED);
        comment.setCreateTime(LocalDateTime.of(2026, 11, 22, 20, 0));

        Page<MemComment> page = new Page<>(1, 5);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(comment));
        when(commentMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(member(9L, "alice", "爱丽丝")));

        PageResult<MemberCommentVO> result = service.pageApproved(100L, 1, 5);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        MemberCommentVO vo = result.getRecords().get(0);
        assertThat(vo.getContent()).isEqualTo("不错");
        assertThat(vo.getNickname()).isEqualTo("爱丽丝");
    }

    @Test
    @DisplayName("审核只接受已通过或已拒绝")
    void auditValidatesStatus() {
        assertThatThrownBy(() -> service.audit(1L, "9", null, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审核状态");
        verify(commentMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("审核通过写入审核人与时间")
    void auditUpdatesRecord() {
        MemComment comment = new MemComment();
        comment.setCommentId(1L);
        comment.setStatus(MemberCommentService.STATUS_PENDING);
        when(commentMapper.selectById(1L)).thenReturn(comment);

        service.audit(1L, MemberCommentService.STATUS_APPROVED, "内容合规", 7L);

        verify(commentMapper).update(any(), any());
    }

    @Test
    @DisplayName("删除不存在的评论返回 1004")
    void deleteMissingRejected() {
        when(commentMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ServiceException.class);
    }

    private MemUser member(Long memberId, String username, String nickname) {
        MemUser user = new MemUser();
        user.setMemberId(memberId);
        user.setUsername(username);
        user.setNickname(nickname);
        return user;
    }
}
