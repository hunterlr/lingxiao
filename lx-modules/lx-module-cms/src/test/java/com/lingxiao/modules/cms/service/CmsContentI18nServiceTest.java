package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.dto.CmsContentI18nSaveRequest;
import com.lingxiao.modules.cms.dto.CmsContentI18nVO;
import com.lingxiao.modules.cms.entity.CmsContentI18n;
import com.lingxiao.modules.cms.mapper.CmsContentI18nMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容多语言服务测试。
 *
 * <p>重点覆盖：保存落草稿、语言归一（en → en-US）、发布 / 下线状态流转、
 * 门户只认已发布版本、彻底删除时清理译文。</p>
 */
@DisplayName("CmsContentI18nService 内容翻译")
class CmsContentI18nServiceTest {

    private CmsContentI18nMapper mapper;
    private CmsContentI18nService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CmsContentI18n.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(CmsContentI18nMapper.class);
        service = new CmsContentI18nService(mapper);
    }

    @Test
    @DisplayName("保存草稿：新建时状态为草稿，语言归一为完整 locale")
    void saveDraftCreatesDraft() {
        when(mapper.selectOne(any())).thenReturn(null);

        CmsContentI18nVO vo = service.saveDraft("article", 9L, "en", request("English title"));

        ArgumentCaptor<CmsContentI18n> captor = ArgumentCaptor.forClass(CmsContentI18n.class);
        verify(mapper).insert(captor.capture());
        CmsContentI18n saved = captor.getValue();
        assertThat(saved.getLang()).isEqualTo("en-US");
        assertThat(saved.getStatus()).isEqualTo(CmsContentI18nService.STATUS_DRAFT);
        assertThat(saved.getTitle()).isEqualTo("English title");
        assertThat(vo.getStatus()).isEqualTo(CmsContentI18nService.STATUS_DRAFT);
    }

    @Test
    @DisplayName("保存草稿：正文按白名单清洗")
    void saveDraftSanitizesContent() {
        CmsContentI18nSaveRequest request = request("Title");
        request.setContent("<p>ok</p><script>alert(1)</script>");
        when(mapper.selectOne(any())).thenReturn(null);

        service.saveDraft("article", 9L, "en-US", request);

        ArgumentCaptor<CmsContentI18n> captor = ArgumentCaptor.forClass(CmsContentI18n.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).contains("ok").doesNotContain("<script");
    }

    @Test
    @DisplayName("空标题被拒绝")
    void blankTitleRejected() {
        assertThatThrownBy(() -> service.saveDraft("article", 9L, "en-US", request("  ")))
                .isInstanceOf(ServiceException.class);
        verify(mapper, never()).insert(any(CmsContentI18n.class));
    }

    @Test
    @DisplayName("未知 ownerType 被拒绝")
    void unknownOwnerTypeRejected() {
        assertThatThrownBy(() -> service.list("unknown", 9L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("发布把状态改为已发布；重复发布幂等")
    void publishFlipsStatus() {
        CmsContentI18n row = row(1L, 9L, "en-US", CmsContentI18nService.STATUS_DRAFT);
        when(mapper.selectOne(any())).thenReturn(row);

        service.publish("article", 9L, "en-US");

        assertThat(row.getStatus()).isEqualTo(CmsContentI18nService.STATUS_PUBLISHED);
        verify(mapper).updateById(row);
    }

    @Test
    @DisplayName("门户只取已发布版本：草稿不返回")
    void publishedIgnoresDraft() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThat(service.published("article", 9L, "en-US")).isNull();
        verify(mapper).selectOne(any());
    }

    @Test
    @DisplayName("批量取译文返回按 ownerId 的映射")
    void publishedMapKeysByOwner() {
        CmsContentI18n row = row(1L, 9L, "en-US", CmsContentI18nService.STATUS_PUBLISHED);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(row));

        Map<Long, CmsContentI18n> map = service.publishedMap("article", Arrays.asList(9L, 10L), "en");

        assertThat(map).containsKey(9L);
        assertThat(map.get(9L).getTitle()).isEqualTo("English");
    }

    @Test
    @DisplayName("彻底删除内容时清理所有语言版本")
    void deleteByOwnerRemovesRows() {
        service.deleteByOwner("article", 9L);
        verify(mapper).delete(any());
    }

    @Test
    @DisplayName("列表按语言排序返回")
    void listMapsRows() {
        when(mapper.selectList(any())).thenReturn(Arrays.asList(
                row(1L, 9L, "en-US", CmsContentI18nService.STATUS_PUBLISHED)));
        List<CmsContentI18nVO> rows = service.list("article", 9L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getLang()).isEqualTo("en-US");
    }

    private CmsContentI18nSaveRequest request(String title) {
        CmsContentI18nSaveRequest request = new CmsContentI18nSaveRequest();
        request.setTitle(title);
        request.setSummary("summary");
        request.setContent("<p>body</p>");
        return request;
    }

    private CmsContentI18n row(Long id, Long ownerId, String lang, String status) {
        CmsContentI18n row = new CmsContentI18n();
        row.setI18nId(id);
        row.setOwnerType("article");
        row.setOwnerId(ownerId);
        row.setLang(lang);
        row.setTitle("English");
        row.setStatus(status);
        return row;
    }
}