package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsModel;
import com.lingxiao.modules.cms.entity.CmsModelField;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsContentExtMapper;
import com.lingxiao.modules.cms.mapper.CmsModelFieldMapper;
import com.lingxiao.modules.cms.mapper.CmsModelMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型与字段定义测试。
 *
 * <p>重点：内置模型幂等、必填字段校验、内置 / 被引用模型不可删。</p>
 */
@DisplayName("CmsModelService 内容模型")
class CmsModelServiceTest {

    private CmsModelMapper modelMapper;
    private CmsModelFieldMapper fieldMapper;
    private CmsCategoryMapper categoryMapper;
    private CmsPageMapper pageMapper;
    private CmsContentExtMapper extMapper;
    private CmsSiteService siteService;
    private CmsModelService service;

    @BeforeEach
    void setUp() {
        modelMapper = mock(CmsModelMapper.class);
        fieldMapper = mock(CmsModelFieldMapper.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        pageMapper = mock(CmsPageMapper.class);
        extMapper = mock(CmsContentExtMapper.class);
        siteService = mock(CmsSiteService.class);
        service = new CmsModelService(modelMapper, fieldMapper, categoryMapper,
                pageMapper, extMapper, siteService);
    }

    @Test
    @DisplayName("内置模型：空站点一次补齐三个模型及字段")
    void ensureBuiltInCreatesThreeModels() {
        when(modelMapper.selectOne(any())).thenReturn(null);
        when(modelMapper.insert(any())).thenAnswer(invocation -> {
            CmsModel model = invocation.getArgument(0);
            model.setModelId((long) (model.getModelType().hashCode() & 0x7fffffff));
            return 1;
        });
        when(fieldMapper.selectCount(any())).thenReturn(0L);
        when(fieldMapper.insert(any())).thenReturn(1);

        service.ensureBuiltIn(1L);

        verify(modelMapper, org.mockito.Mockito.times(3)).insert(any(CmsModel.class));
        // article 3 + gallery 3 + page 2
        verify(fieldMapper, org.mockito.Mockito.times(8)).insert(any(CmsModelField.class));
    }

    @Test
    @DisplayName("必填扩展字段缺失：拒绝保存")
    void rejectsMissingRequiredExt() {
        CmsModel model = model(10L, 1L, CmsModelService.TYPE_GALLERY);
        when(modelMapper.selectById(10L)).thenReturn(model);
        CmsModelField images = field(100L, 10L, "images", "图集图片", "images", "1");
        when(fieldMapper.selectList(any())).thenReturn(Collections.singletonList(images));

        ServiceException error = catchThrowableOfType(
                () -> service.validateExt(10L, Collections.emptyMap(), true), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessageKey()).isEqualTo("cms.field.required");
        assertThat(error.getArgs()[0]).isEqualTo("图集图片");
    }

    @Test
    @DisplayName("扩展字段：只保留模型已定义字段")
    void filtersUnknownExtKeys() {
        CmsModel model = model(10L, 1L, CmsModelService.TYPE_ARTICLE);
        when(modelMapper.selectById(10L)).thenReturn(model);
        CmsModelField subtitle = field(100L, 10L, "subtitle", "副标题", "text", "0");
        when(fieldMapper.selectList(any())).thenReturn(Collections.singletonList(subtitle));

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("subtitle", "副标题内容");
        ext.put("unknown", "应被丢弃");

        Map<String, Object> result = service.validateExt(10L, ext, true);

        assertThat(result).containsOnlyKeys("subtitle");
        assertThat(result.get("subtitle")).isEqualTo("副标题内容");
    }

    @Test
    @DisplayName("内置模型不可删除")
    void rejectsDeletingBuiltInModel() {
        CmsModel model = model(10L, 1L, CmsModelService.TYPE_ARTICLE);
        model.setBuiltIn(CmsModelService.BUILT_IN_YES);
        when(modelMapper.selectById(10L)).thenReturn(model);

        ServiceException error = catchThrowableOfType(
                () -> service.delete(10L), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.model.builtin.undeletable");
        verify(modelMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("被栏目引用的模型不可删除")
    void rejectsDeletingReferencedModel() {
        CmsModel model = model(10L, 1L, CmsModelService.TYPE_ARTICLE);
        model.setBuiltIn(CmsModelService.BUILT_IN_NO);
        when(modelMapper.selectById(10L)).thenReturn(model);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        ServiceException error = catchThrowableOfType(
                () -> service.delete(10L), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.model.referenced");
    }

    @Test
    @DisplayName("字段类型非法：拒绝新增")
    void rejectsUnsupportedFieldType() {
        CmsModel model = model(10L, 1L, CmsModelService.TYPE_ARTICLE);
        when(modelMapper.selectById(10L)).thenReturn(model);
        when(fieldMapper.selectCount(any())).thenReturn(0L);
        CmsModelField field = field(null, 10L, "foo", "Foo", "unknown", "0");

        ServiceException error = catchThrowableOfType(
                () -> service.addField(10L, field), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.field.type.invalid");
    }

    @Test
    @DisplayName("内置模型编辑：不改 key 与类型")
    void keepsBuiltInKeyOnUpdate() {
        CmsModel existing = model(10L, 1L, CmsModelService.TYPE_ARTICLE);
        existing.setBuiltIn(CmsModelService.BUILT_IN_YES);
        existing.setModelKey("article");
        when(modelMapper.selectById(10L)).thenReturn(existing);
        when(modelMapper.selectCount(any())).thenReturn(0L);
        CmsModel request = model(10L, 1L, CmsModelService.TYPE_GALLERY);
        request.setModelKey("hacked");

        service.update(10L, request);

        assertThat(existing.getModelKey()).isEqualTo("article");
        assertThat(existing.getModelType()).isEqualTo(CmsModelService.TYPE_ARTICLE);
    }

    private CmsModel model(Long id, Long siteId, String type) {
        CmsModel model = new CmsModel();
        model.setModelId(id);
        model.setSiteId(siteId);
        model.setModelKey(type);
        model.setModelName(type);
        model.setModelType(type);
        model.setStatus("0");
        model.setSort(0);
        return model;
    }

    private CmsModelField field(Long id, Long modelId, String key, String name,
                                String type, String required) {
        CmsModelField field = new CmsModelField();
        field.setFieldId(id);
        field.setModelId(modelId);
        field.setFieldKey(key);
        field.setFieldName(name);
        field.setFieldType(type);
        field.setRequired(required);
        field.setStatus("0");
        field.setSort(0);
        return field;
    }
}
