package com.lingxiao.modules.cms.support;

import com.lingxiao.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 模板编码白名单测试。
 *
 * <p>模板编码会同时进入数据库、后台下拉和门户组件映射，必须锁住三件事：
 * 空值回退、未知编码拒绝、预览模板与目标类型匹配。</p>
 */
@DisplayName("CmsTemplates 模板与主题白名单")
class CmsTemplatesTest {

    @Test
    @DisplayName("主题：空值回退 default，白名单通过，未知编码拒绝")
    void normalizesTheme() {
        assertThat(CmsTemplates.normalizeTheme(null)).isEqualTo(CmsTemplates.THEME_DEFAULT);
        assertThat(CmsTemplates.normalizeTheme("  ")).isEqualTo(CmsTemplates.THEME_DEFAULT);
        assertThat(CmsTemplates.normalizeTheme(" magazine ")).isEqualTo("magazine");

        ServiceException error = catchThrowableOfType(
                () -> CmsTemplates.normalizeTheme("dark"), ServiceException.class);
        assertThat(error.getMessageKey()).isEqualTo("valid.cms.site.theme");
    }

    @Test
    @DisplayName("栏目模板：空值保持空，未知编码拒绝")
    void normalizesListTemplate() {
        assertThat(CmsTemplates.normalizeListTemplate(null)).isNull();
        assertThat(CmsTemplates.normalizeListTemplate("list.gallery")).isEqualTo("list.gallery");

        ServiceException error = catchThrowableOfType(
                () -> CmsTemplates.normalizeListTemplate("list.unknown"), ServiceException.class);
        assertThat(error.getMessageKey()).isEqualTo("valid.cms.category.template");
    }

    @Test
    @DisplayName("单页模板：空值保持空，未知编码拒绝")
    void normalizesPageTemplate() {
        assertThat(CmsTemplates.normalizePageTemplate(" ")).isNull();
        assertThat(CmsTemplates.normalizePageTemplate("page.about")).isEqualTo("page.about");

        ServiceException error = catchThrowableOfType(
                () -> CmsTemplates.normalizePageTemplate("list.news"), ServiceException.class);
        assertThat(error.getMessageKey()).isEqualTo("valid.cms.page.template");
    }

    @Test
    @DisplayName("详情模板：由栏目列表模板推导，未知回退默认")
    void resolvesDetailTemplate() {
        assertThat(CmsTemplates.resolveDetailTemplate("list.news")).isEqualTo("detail.news");
        assertThat(CmsTemplates.resolveDetailTemplate("list.gallery")).isEqualTo("detail.gallery");
        assertThat(CmsTemplates.resolveDetailTemplate(null)).isEqualTo("detail.default");
        assertThat(CmsTemplates.resolveDetailTemplate("list.default")).isEqualTo("detail.default");
    }

    @Test
    @DisplayName("预览模板：按目标类型限制前缀")
    void normalizesPreviewTemplate() {
        assertThat(CmsTemplates.normalizePreviewTemplate("category", "list.news")).isEqualTo("list.news");
        assertThat(CmsTemplates.normalizePreviewTemplate("article", "detail.gallery")).isEqualTo("detail.gallery");
        assertThat(CmsTemplates.normalizePreviewTemplate("page", "page.about")).isEqualTo("page.about");
        assertThat(CmsTemplates.normalizePreviewTemplate("page", null)).isNull();

        ServiceException error = catchThrowableOfType(
                () -> CmsTemplates.normalizePreviewTemplate("category", "page.about"),
                ServiceException.class);
        assertThat(error.getMessageKey()).isEqualTo("cms.preview.template.invalid");
    }

    @Test
    @DisplayName("选项：编码与名称齐全，三类模板互不重叠")
    void exposesOptions() {
        assertThat(CmsTemplates.themeOptions()).extracting("code")
                .containsExactly("default", "magazine", "minimal");
        assertThat(CmsTemplates.listTemplateOptions()).extracting("code")
                .containsExactly("list.default", "list.news", "list.gallery");
        assertThat(CmsTemplates.detailTemplateOptions()).extracting("code")
                .containsExactly("detail.default", "detail.news", "detail.gallery");
        assertThat(CmsTemplates.pageTemplateOptions()).extracting("code")
                .containsExactly("page.default", "page.about");
        assertThat(CmsTemplates.themeOptions().get(0).getName()).isEqualTo("默认主题");
        assertThat(CmsTemplates.themeOptions().get(0).getNameEn()).isEqualTo("Default");
    }
}
