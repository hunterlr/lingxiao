package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsSite;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsSiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 站点服务。
 *
 * <p>站点是 CMS 的根：栏目、文章、标签都挂 {@code siteId}。删除站点前
 * 必须保证下面没有栏目和文章，否则会出现“孤儿内容”。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsSiteService {

    private final CmsSiteMapper siteMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsArticleMapper articleMapper;

    public PageResult<CmsSite> page(long pageNum, long pageSize, String keyword, String status) {
        Page<CmsSite> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<CmsSite> wrapper = new LambdaQueryWrapper<CmsSite>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(CmsSite::getSiteName, keyword)
                        .or().like(CmsSite::getSiteKey, keyword))
                .eq(StringUtils.hasText(status), CmsSite::getStatus, status)
                .orderByAsc(CmsSite::getSiteId);
        Page<CmsSite> result = siteMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 下拉用：只返回启用站点，不分页。 */
    public List<CmsSite> listEnabled() {
        return siteMapper.selectList(new LambdaQueryWrapper<CmsSite>()
                .eq(CmsSite::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByAsc(CmsSite::getSiteId));
    }

    public CmsSite detail(Long siteId) {
        return mustGet(siteId);
    }

    public CmsSite detailByKey(String siteKey) {
        CmsSite site = siteMapper.selectOne(new LambdaQueryWrapper<CmsSite>()
                .eq(CmsSite::getSiteKey, siteKey));
        if (site == null || !CommonConstants.STATUS_NORMAL.equals(site.getStatus())) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "站点不存在或已停用");
        }
        return site;
    }

    public Long create(CmsSite site) {
        site.setSiteId(null);
        site.setSiteKey(site.getSiteKey().trim());
        site.setSiteName(site.getSiteName().trim());
        ensureKeyUnique(site.getSiteKey(), null);
        site.setStatus(defaultStatus(site.getStatus()));
        site.setCreateBy(LoginHelper.getUserId());
        siteMapper.insert(site);
        return site.getSiteId();
    }

    public void update(Long siteId, CmsSite site) {
        CmsSite existing = mustGet(siteId);
        String siteKey = site.getSiteKey().trim();
        ensureKeyUnique(siteKey, siteId);
        existing.setSiteKey(siteKey);
        existing.setSiteName(site.getSiteName().trim());
        existing.setDomain(site.getDomain());
        existing.setLogo(site.getLogo());
        existing.setSeoTitle(site.getSeoTitle());
        existing.setSeoKeywords(site.getSeoKeywords());
        existing.setSeoDescription(site.getSeoDescription());
        existing.setStatus(defaultStatus(site.getStatus()));
        existing.setRemark(site.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        siteMapper.updateById(existing);
    }

    public void delete(Long siteId) {
        mustGet(siteId);
        // 先查文章（含回收站）：栏目通常是文章的前置条件，先提示“还有文章”
        // 才能让运营直接去回收站清理，而不是先删栏目再被文章拦住。
        long articles = articleMapper.countBySiteIncludingDeleted(siteId);
        if (articles > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "站点下还有文章（含回收站），请先处理后再删除");
        }
        Long categories = categoryMapper.selectCount(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getSiteId, siteId));
        if (categories != null && categories > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "站点下还有栏目，不能删除");
        }
        siteMapper.hardDeleteById(siteId);
    }

    public CmsSite mustGet(Long siteId) {
        CmsSite site = siteId == null ? null : siteMapper.selectById(siteId);
        if (site == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "站点不存在");
        }
        return site;
    }

    private void ensureKeyUnique(String siteKey, Long excludeId) {
        LambdaQueryWrapper<CmsSite> wrapper = new LambdaQueryWrapper<CmsSite>()
                .eq(CmsSite::getSiteKey, siteKey)
                .ne(excludeId != null, CmsSite::getSiteId, excludeId);
        Long count = siteMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "站点标识已存在：" + siteKey);
        }
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : CommonConstants.STATUS_NORMAL;
    }
}
