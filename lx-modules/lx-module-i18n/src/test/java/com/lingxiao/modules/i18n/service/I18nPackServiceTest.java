package com.lingxiao.modules.i18n.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxiao.modules.i18n.dto.PackPublishRequest;
import com.lingxiao.modules.i18n.dto.PackRollbackRequest;
import com.lingxiao.modules.i18n.dto.PackVO;
import com.lingxiao.modules.i18n.entity.I18nKey;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import com.lingxiao.modules.i18n.entity.I18nPack;
import com.lingxiao.modules.i18n.entity.I18nValue;
import com.lingxiao.modules.i18n.mapper.I18nKeyMapper;
import com.lingxiao.modules.i18n.mapper.I18nPackMapper;
import com.lingxiao.modules.i18n.mapper.I18nValueMapper;
import com.lingxiao.modules.i18n.message.DatabaseTranslationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布 / 回滚测试。
 *
 * <p>锁定的行为：发布生成快照与校验和；回滚按快照恢复译文，而不是只改状态。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("I18nPackService 发布与回滚")
class I18nPackServiceTest {

    @Mock
    private I18nLanguageService languageService;

    @Mock
    private I18nKeyMapper keyMapper;

    @Mock
    private I18nValueMapper valueMapper;

    @Mock
    private I18nPackMapper packMapper;

    @Mock
    private DatabaseTranslationProvider translationProvider;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private I18nPackService packService;

    @BeforeAll
    static void initTableInfo() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, I18nPack.class);
        TableInfoHelper.initTableInfo(assistant, I18nKey.class);
        TableInfoHelper.initTableInfo(assistant, I18nValue.class);
        TableInfoHelper.initTableInfo(assistant, I18nLanguage.class);
    }

    private I18nLanguage language() {
        I18nLanguage language = new I18nLanguage();
        language.setLanguageCode("en-US");
        language.setStatus("0");
        return language;
    }

    @Test
    @DisplayName("发布生成新版本、快照与校验和")
    void publishCreatesSnapshot() {
        I18nKey key = new I18nKey();
        key.setKeyId(10L);
        key.setNamespace("error");
        key.setKeyCode("error.1001");
        key.setDefaultValue("共 {0} 条");
        key.setStatus("0");
        I18nValue value = new I18nValue();
        value.setKeyId(10L);
        value.setLanguageCode("en-US");
        value.setValueText("Total {0} items");

        when(languageService.mustGetByCode("en-US")).thenReturn(language());
        when(keyMapper.selectList(any())).thenReturn(Collections.singletonList(key));
        when(valueMapper.selectList(any())).thenReturn(Collections.singletonList(value));
        when(packMapper.selectOne(any())).thenReturn(null);

        PackPublishRequest request = new PackPublishRequest();
        request.setLanguageCode("en-US");
        request.setNamespace("error");
        List<PackVO> result = packService.publish(request, 9L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersion()).isEqualTo(1);
        assertThat(result.get(0).getChecksum()).hasSize(64);

        ArgumentCaptor<I18nPack> captor = ArgumentCaptor.forClass(I18nPack.class);
        verify(packMapper).insert(captor.capture());
        I18nPack saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("1");
        assertThat(saved.getSnapshotJson()).contains("\"10\":\"Total {0} items\"");
        verify(translationProvider).invalidate();
    }

    @Test
    @DisplayName("回滚按快照恢复译文并切换当前版本")
    void rollbackRestoresSnapshot() {
        I18nPack target = new I18nPack();
        target.setPackId(100L);
        target.setLanguageCode("en-US");
        target.setNamespace("error");
        target.setVersion(1);
        target.setStatus("0");
        target.setSnapshotJson("{\"10\":\"old text\"}");

        I18nValue current = new I18nValue();
        current.setKeyId(10L);
        current.setLanguageCode("en-US");
        current.setValueText("new text");
        current.setVersion(3);

        when(packMapper.selectById(100L)).thenReturn(target);
        when(valueMapper.selectOne(any())).thenReturn(current);

        PackRollbackRequest request = new PackRollbackRequest();
        request.setPackId(100L);
        PackVO result = packService.rollback(request, 9L);

        assertThat(result.getStatus()).isEqualTo("1");
        assertThat(current.getValueText()).isEqualTo("old text");
        assertThat(current.getPublishedText()).isEqualTo("old text");
        assertThat(current.getReviewStatus()).isEqualTo("1");
        assertThat(current.getVersion()).isEqualTo(4);
        verify(valueMapper).updateById(current);
        verify(translationProvider).invalidate();
    }
}
