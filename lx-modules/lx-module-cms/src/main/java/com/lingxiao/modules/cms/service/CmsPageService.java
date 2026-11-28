package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.web.util.HtmlSanitizer;
import com.lingxiao.modules.cms.entity.CmsModel;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.mapper.CmsNavItemMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import com.lingxiao.modules.cms.support.CmsTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CMS 单页服务。
 *
 * <p>单页用 {@code page_key} 作为站点内稳定键（门户 URL / 导航目标），
 * 改名不影响引用。状态与文章一致：0 草稿 / 1 已发布 / 2 已下线。</p>
 *
 * <p>正文在写入与读取两侧都用 {@link HtmlSanitizer} 清洗；扩展字段
 * 由 page 模型定义，值存 {@code cms_content_ext}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsPageService {

    public static final String STATUS_DRAFT = "0";
    public static final String STATUS_PUBLISHED = "1";
    public static final String STATUS_OFFLINE = "2";

    private final CmsPageMapper pageMapper;
    private final CmsNavItemMapper navItemMapper;
    private final CmsModelService modelService;
    private final CmsContentExtService extService;
    private final CmsSiteService siteService;
    private final CmsContentI18nService i18nService;

    public PageResult<CmsPage> page(long pageNum, long pageSize, Long siteId,
                                    String keyword, String status) {
        Page<CmsPage> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<CmsPage> wrapper = new LambdaQueryWrapper<CmsPage>()
                .select(CmsPage.class, field -> !"content".equals(field.getColumn()))
                .eq(siteId != null, CmsPage::getSiteId, siteId)
                .eq(StringUtils.hasText(status), CmsPage::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(CmsPage::getPageName, keyword)
                        .or().like(CmsPage::getPageKey, keyword)
                        .or().like(CmsPage::getTitle, keyword))
                .orderByAsc(CmsPage::getSort)
                .orderByDesc(CmsPage::getPageId);
        Page<CmsPage> result = pageMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public CmsPage detail(Long pageId) {
        CmsPage page = mustGet(pageId);
        page.setContent(HtmlSanitizer.clean(page.getContent()));
        page.setExt(extService.load("page", pageId));
        return page;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsPage page) {
        siteService.mustGet(page.getSiteId());
        String pageKey = page.getPageKey().trim();
        ensureKeyUnique(page.getSiteId(), pageKey, null);
        CmsModel model = modelService.resolveForPage(page.getSiteId());

        page.setPageId(null);
        page.setPageKey(pageKey);
        page.setPageName(page.getPageName().trim());
        page.setModelId(model.getModelId());
        page.setContent(HtmlSanitizer.clean(page.getContent()));
        page.setTemplate(CmsTemplates.normalizePageTemplate(page.getTemplate()));
        page.setStatus(STATUS_DRAFT);
        page.setPublishTime(null);
        page.setSort(page.getSort() == null ? 0 : page.getSort());
        page.setCreateBy(currentUserId());
        pageMapper.insert(page);
        saveExt(page, model);
        return page.getPageId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long pageId, CmsPage page) {
        CmsPage existing = mustGet(pageId);
        Long siteId = existing.getSiteId();
        String pageKey = page.getPageKey().trim();
        ensureKeyUnique(siteId, pageKey, pageId);
        CmsModel model = existing.getModelId() == null
                ? modelService.resolveForPage(siteId)
                : modelService.mustGet(existing.getModelId());

        existing.setPageKey(pageKey);
        existing.setPageName(page.getPageName().trim());
        existing.setModelId(model.getModelId());
        existing.setTitle(page.getTitle());
        existing.setSummary(page.getSummary());
        existing.setCover(page.getCover());
        existing.setContent(HtmlSanitizer.clean(page.getContent()));
        existing.setTemplate(CmsTemplates.normalizePageTemplate(page.getTemplate()));
        existing.setSeoTitle(page.getSeoTitle());
        existing.setSeoKeywords(page.getSeoKeywords());
        existing.setSeoDescription(page.getSeoDescription());
        existing.setSort(page.getSort() == null ? existing.getSort() : page.getSort());
        existing.setRemark(page.getRemark());
        existing.setUpdateBy(currentUserId());
        pageMapper.updateById(existing);
        saveExt(existing, model);
    }

    /** 发布：首次发布写 publishTime，重新发布保留原时间。 */
    public void publish(Long pageId) {
        CmsPage existing = mustGet(pageId);
        LambdaUpdateWrapper<CmsPage> update = new LambdaUpdateWrapper<CmsPage>()
                .eq(CmsPage::getPageId, pageId)
                .set(CmsPage::getStatus, STATUS_PUBLISHED)
                .set(CmsPage::getUpdateBy, currentUserId());
        if (existing.getPublishTime() == null) {
            update.set(CmsPage::getPublishTime, LocalDateTime.now());
        }
        pageMapper.update(null, update);
    }

    public void offline(Long pageId) {
        mustGet(pageId);
        pageMapper.update(null, new LambdaUpdateWrapper<CmsPage>()
                .eq(CmsPage::getPageId, pageId)
                .set(CmsPage::getStatus, STATUS_OFFLINE)
                .set(CmsPage::getUpdateBy, currentUserId()));
    }

    /** 删除单页：被导航项引用时拒绝，避免门户出现死链。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long pageId) {
        mustGet(pageId);
        if (navItemMapper.countByTarget("page", pageId) > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.page.referenced");
        }
        pageMapper.hardDeleteById(pageId);
        extService.deleteByOwner("page", pageId);
        i18nService.deleteByOwner("page", pageId);
    }

    public CmsPage mustGet(Long pageId) {
        CmsPage page = pageId == null ? null : pageMapper.selectById(pageId);
        if (page == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.page.notfound");
        }
        return page;
    }

    private void saveExt(CmsPage page, CmsModel model) {
        Map<String, Object> ext = modelService.validateExt(
                model.getModelId(), page.getExt(), true);
        extService.save("page", page.getPageId(), page.getSiteId(), model.getModelId(), ext);
    }

    private void ensureKeyUnique(Long siteId, String pageKey, Long excludeId) {
        Long count = pageMapper.selectCount(new LambdaQueryWrapper<CmsPage>()
                .eq(CmsPage::getSiteId, siteId)
                .eq(CmsPage::getPageKey, pageKey)
                .ne(excludeId != null, CmsPage::getPageId, excludeId));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.page.key.duplicate");
        }
    }

    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 供门户预览判断状态用；避免预览服务重复定义常量。 */
    static boolean isPublished(CmsPage page) {
        return page != null && STATUS_PUBLISHED.equals(page.getStatus())
                && CommonConstants.DEL_FLAG_NORMAL.equals(page.getDelFlag());
    }
}
