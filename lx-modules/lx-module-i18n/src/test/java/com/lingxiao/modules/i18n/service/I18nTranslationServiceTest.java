package com.lingxiao.modules.i18n.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.i18n.dto.ValueBatchRequest;
import com.lingxiao.modules.i18n.entity.I18nKey;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import com.lingxiao.modules.i18n.entity.I18nValue;
import com.lingxiao.modules.i18n.mapper.I18nKeyMapper;
import com.lingxiao.modules.i18n.mapper.I18nMissingLogMapper;
import com.lingxiao.modules.i18n.mapper.I18nValueMapper;
import com.lingxiao.modules.i18n.message.DatabaseTranslationProvider;
import com.lingxiao.modules.i18n.message.I18nMissingTracker;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 译文保存测试。
 *
 * <p>核心规则：保存只写草稿；占位符不一致直接拒绝，避免上线后参数丢失。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("I18nTranslationService 批量保存")
class I18nTranslationServiceTest {

    @Mock
    private I18nKeyMapper keyMapper;

    @Mock
    private I18nValueMapper valueMapper;

    @Mock
    private I18nMissingLogMapper missingLogMapper;

    @Mock
    private I18nLanguageService languageService;

    @Mock
    private DatabaseTranslationProvider translationProvider;

    @Mock
    private I18nMissingTracker missingTracker;

    @InjectMocks
    private I18nTranslationService translationService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, I18nKey.class);
        TableInfoHelper.initTableInfo(assistant, I18nValue.class);
        TableInfoHelper.initTableInfo(assistant, I18nLanguage.class);
    }

    private ValueBatchRequest request(String text) {
        ValueBatchRequest.ValueItem item = new ValueBatchRequest.ValueItem();
        item.setKeyId(10L);
        item.setLanguageCode("en-US");
        item.setValueText(text);
        ValueBatchRequest request = new ValueBatchRequest();
        request.setItems(Collections.singletonList(item));
        return request;
    }

    private I18nKey key() {
        I18nKey key = new I18nKey();
        key.setKeyId(10L);
        key.setNamespace("error");
        key.setKeyCode("error.1001");
        key.setDefaultValue("共 {0} 条");
        key.setStatus("0");
        return key;
    }

    private I18nLanguage language() {
        I18nLanguage language = new I18nLanguage();
        language.setLanguageCode("en-US");
        language.setStatus("0");
        return language;
    }

    @Test
    @DisplayName("占位符不一致拒绝保存")
    void rejectPlaceholderMismatch() {
        when(keyMapper.selectById(10L)).thenReturn(key());
        when(languageService.mustGetByCode("en-US")).thenReturn(language());

        assertThatThrownBy(() -> translationService.batchSave(request("Total items"), 9L))
                .isInstanceOf(ServiceException.class);

        verify(valueMapper, never()).insert(any(I18nValue.class));
    }

    @Test
    @DisplayName("新增译文写草稿，不直接发布")
    void saveAsDraft() {
        when(keyMapper.selectById(10L)).thenReturn(key());
        when(languageService.mustGetByCode("en-US")).thenReturn(language());
        when(valueMapper.selectOne(any())).thenReturn(null);

        translationService.batchSave(request("Total {0} items"), 9L);

        ArgumentCaptor<I18nValue> captor = ArgumentCaptor.forClass(I18nValue.class);
        verify(valueMapper).insert(captor.capture());
        I18nValue saved = captor.getValue();
        assertThat(saved.getKeyId()).isEqualTo(10L);
        assertThat(saved.getLanguageCode()).isEqualTo("en-US");
        assertThat(saved.getValueText()).isEqualTo("Total {0} items");
        assertThat(saved.getPublishedText()).isNull();
        assertThat(saved.getReviewStatus()).isEqualTo("0");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getTranslatedBy()).isEqualTo(9L);
    }

    @Test
    @DisplayName("编辑已发布译文只改工作副本，发布值保持不变")
    void keepPublishedTextWhenDraftSaved() {
        I18nValue existing = new I18nValue();
        existing.setKeyId(10L);
        existing.setLanguageCode("en-US");
        existing.setValueText("Total {0} items");
        existing.setPublishedText("Published {0} items");
        existing.setReviewStatus("1");
        existing.setVersion(1);
        when(keyMapper.selectById(10L)).thenReturn(key());
        when(languageService.mustGetByCode("en-US")).thenReturn(language());
        when(valueMapper.selectOne(any())).thenReturn(existing);

        translationService.batchSave(request("Draft {0} items"), 9L);

        ArgumentCaptor<I18nValue> captor = ArgumentCaptor.forClass(I18nValue.class);
        verify(valueMapper).updateById(captor.capture());
        I18nValue saved = captor.getValue();
        assertThat(saved.getValueText()).isEqualTo("Draft {0} items");
        assertThat(saved.getPublishedText()).isEqualTo("Published {0} items");
        assertThat(saved.getReviewStatus()).isEqualTo("0");
    }
}
