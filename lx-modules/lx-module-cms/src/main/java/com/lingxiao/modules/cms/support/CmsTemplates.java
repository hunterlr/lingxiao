package com.lingxiao.modules.cms.support;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.dto.CmsTemplateOptionVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CMS 模板编码与站点主题白名单。
 *
 * <p><b>稳定契约：</b>模板编码同时被后台下拉、门户组件映射和预览票据使用，
 * 一旦发布就不要改名，只能新增。未知编码一律拒绝写入，读取时回退默认版式。</p>
 *
 * <ul>
 *   <li>{@code list.*}：栏目列表页版式；</li>
 *   <li>{@code detail.*}：文章详情页版式，由所属栏目的 list 模板推导；</li>
 *   <li>{@code page.*}：单页版式。</li>
 * </ul>
 *
 * <p>站点主题只影响门户 CSS 变量，不改变接口字段与 URL。</p>
 */
public final class CmsTemplates {

    /** 站点默认主题。 */
    public static final String THEME_DEFAULT = "default";

    public static final String LIST_DEFAULT = "list.default";
    public static final String LIST_NEWS = "list.news";
    public static final String LIST_GALLERY = "list.gallery";

    public static final String DETAIL_DEFAULT = "detail.default";
    public static final String DETAIL_NEWS = "detail.news";
    public static final String DETAIL_GALLERY = "detail.gallery";

    public static final String PAGE_DEFAULT = "page.default";
    public static final String PAGE_ABOUT = "page.about";

    private static final Map<String, String[]> THEMES = new LinkedHashMap<>();
    private static final Map<String, String[]> LIST_TEMPLATES = new LinkedHashMap<>();
    private static final Map<String, String[]> DETAIL_TEMPLATES = new LinkedHashMap<>();
    private static final Map<String, String[]> PAGE_TEMPLATES = new LinkedHashMap<>();

    static {
        THEMES.put(THEME_DEFAULT, new String[]{"默认主题", "Default"});
        THEMES.put("magazine", new String[]{"杂志主题", "Magazine"});
        THEMES.put("minimal", new String[]{"极简主题", "Minimal"});

        LIST_TEMPLATES.put(LIST_DEFAULT, new String[]{"默认列表", "Default list"});
        LIST_TEMPLATES.put(LIST_NEWS, new String[]{"新闻列表", "News list"});
        LIST_TEMPLATES.put(LIST_GALLERY, new String[]{"图集网格", "Gallery grid"});

        DETAIL_TEMPLATES.put(DETAIL_DEFAULT, new String[]{"默认详情", "Default detail"});
        DETAIL_TEMPLATES.put(DETAIL_NEWS, new String[]{"新闻详情", "News detail"});
        DETAIL_TEMPLATES.put(DETAIL_GALLERY, new String[]{"图集详情", "Gallery detail"});

        PAGE_TEMPLATES.put(PAGE_DEFAULT, new String[]{"默认单页", "Default page"});
        PAGE_TEMPLATES.put(PAGE_ABOUT, new String[]{"关于我们", "About page"});
    }

    private CmsTemplates() {
    }

    /** 站点主题：空值回退默认；非白名单拒绝。 */
    public static String normalizeTheme(String theme) {
        if (!hasText(theme)) {
            return THEME_DEFAULT;
        }
        String value = theme.trim();
        if (!THEMES.containsKey(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.cms.site.theme");
        }
        return value;
    }

    /** 栏目列表模板：空值保持空（门户回退默认版式）；非白名单拒绝。 */
    public static String normalizeListTemplate(String template) {
        return normalize(template, LIST_TEMPLATES, "valid.cms.category.template");
    }

    /** 单页模板：空值保持空（门户回退默认版式）；非白名单拒绝。 */
    public static String normalizePageTemplate(String template) {
        return normalize(template, PAGE_TEMPLATES, "valid.cms.page.template");
    }

    /**
     * 预览覆盖模板：按目标类型限制前缀，避免用 list 模板预览单页。
     *
     * @param targetType article / page / category
     */
    public static String normalizePreviewTemplate(String targetType, String template) {
        if (!hasText(template)) {
            return null;
        }
        String value = template.trim();
        Map<String, String[]> allowed;
        if ("category".equals(targetType)) {
            allowed = LIST_TEMPLATES;
        } else if ("article".equals(targetType)) {
            allowed = DETAIL_TEMPLATES;
        } else if ("page".equals(targetType)) {
            allowed = PAGE_TEMPLATES;
        } else {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.template.invalid");
        }
        if (!allowed.containsKey(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.template.invalid");
        }
        return value;
    }

    /**
     * 由栏目列表模板推导文章详情模板：同一栏目保持版式一致。
     * 未设置或未知编码时回退默认详情。
     */
    public static String resolveDetailTemplate(String listTemplate) {
        if (LIST_NEWS.equals(listTemplate)) {
            return DETAIL_NEWS;
        }
        if (LIST_GALLERY.equals(listTemplate)) {
            return DETAIL_GALLERY;
        }
        return DETAIL_DEFAULT;
    }

    public static List<CmsTemplateOptionVO> themeOptions() {
        return options(THEMES);
    }

    public static List<CmsTemplateOptionVO> listTemplateOptions() {
        return options(LIST_TEMPLATES);
    }

    public static List<CmsTemplateOptionVO> detailTemplateOptions() {
        return options(DETAIL_TEMPLATES);
    }

    public static List<CmsTemplateOptionVO> pageTemplateOptions() {
        return options(PAGE_TEMPLATES);
    }

    private static String normalize(String template, Map<String, String[]> allowed, String errorKey) {
        if (!hasText(template)) {
            return null;
        }
        String value = template.trim();
        if (!allowed.containsKey(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, errorKey);
        }
        return value;
    }

    private static List<CmsTemplateOptionVO> options(Map<String, String[]> source) {
        List<CmsTemplateOptionVO> result = new ArrayList<>(source.size());
        for (Map.Entry<String, String[]> entry : source.entrySet()) {
            CmsTemplateOptionVO vo = new CmsTemplateOptionVO();
            vo.setCode(entry.getKey());
            vo.setName(entry.getValue()[0]);
            vo.setNameEn(entry.getValue()[1]);
            result.add(vo);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 供测试与调用方判断编码是否合法，避免重复维护列表。 */
    public static boolean isDetailTemplate(String code) {
        return code != null && DETAIL_TEMPLATES.containsKey(code.trim());
    }

    /** 全部模板编码快照，用于文档 / 脚本核对。 */
    public static Map<String, List<String>> catalog() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("list", new ArrayList<>(LIST_TEMPLATES.keySet()));
        result.put("detail", new ArrayList<>(DETAIL_TEMPLATES.keySet()));
        result.put("page", new ArrayList<>(PAGE_TEMPLATES.keySet()));
        result.put("themes", new ArrayList<>(THEMES.keySet()));
        return result;
    }

    static {
        // 防止误把 detail 模板写进 list 白名单：启动即断言三类集合互不重叠。
        for (String code : DETAIL_TEMPLATES.keySet()) {
            if (LIST_TEMPLATES.containsKey(code) || PAGE_TEMPLATES.containsKey(code)) {
                throw new IllegalStateException("CMS template code duplicated: " + code);
            }
        }
    }
}
