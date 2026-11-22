package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * CMS 标签服务。
 *
 * <p>标签是站点内维度的“横切分类”：一篇文章可以挂多个标签，一个标签
 * 可以挂多篇文章。删除标签前必须确认没有文章在用，否则关联表会留孤儿行
 * （没有外键兜底）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsTagService {

    private final CmsTagMapper tagMapper;
    private final CmsArticleTagMapper articleTagMapper;
    private final CmsSiteService siteService;

    public PageResult<CmsTag> page(long pageNum, long pageSize, Long siteId, String keyword, String status) {
        siteService.mustGet(siteId);
        Page<CmsTag> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<CmsTag> wrapper = new LambdaQueryWrapper<CmsTag>()
                .eq(CmsTag::getSiteId, siteId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(CmsTag::getTagName, keyword)
                        .or().like(CmsTag::getTagKey, keyword))
                .eq(StringUtils.hasText(status), CmsTag::getStatus, status)
                .orderByAsc(CmsTag::getTagId);
        Page<CmsTag> result = tagMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 文章编辑页的标签多选：只返回启用标签。 */
    public List<CmsTag> listEnabled(Long siteId) {
        siteService.mustGet(siteId);
        return tagMapper.selectList(new LambdaQueryWrapper<CmsTag>()
                .eq(CmsTag::getSiteId, siteId)
                .eq(CmsTag::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByAsc(CmsTag::getTagName));
    }

    public Long create(CmsTag tag) {
        siteService.mustGet(tag.getSiteId());
        Long tagId = tag.getTagId();
        if (tagId != null) {
            // 前端回传了实体主键时不能直接 insert：ASSIGN_ID 会覆盖，
            // 但这里显式置空更直观，避免误以为是更新
            tag.setTagId(null);
        }
        tag.setTagKey(tag.getTagKey().trim());
        tag.setTagName(tag.getTagName().trim());
        ensureKeyUnique(tag.getSiteId(), tag.getTagKey(), null);
        tag.setStatus(defaultIfBlank(tag.getStatus(), CommonConstants.STATUS_NORMAL));
        tag.setCreateBy(LoginHelper.getUserId());
        tagMapper.insert(tag);
        return tag.getTagId();
    }

    public void update(Long tagId, CmsTag tag) {
        CmsTag existing = mustGet(tagId);
        String tagKey = tag.getTagKey().trim();
        ensureKeyUnique(existing.getSiteId(), tagKey, tagId);
        existing.setTagKey(tagKey);
        existing.setTagName(tag.getTagName().trim());
        existing.setStatus(defaultIfBlank(tag.getStatus(), existing.getStatus()));
        existing.setRemark(tag.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        tagMapper.updateById(existing);
    }

    public void delete(Long tagId) {
        mustGet(tagId);
        int used = articleTagMapper.countByTagId(tagId);
        if (used > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "标签已被 " + used + " 篇文章引用，请先在文章中移除");
        }
        tagMapper.hardDeleteById(tagId);
    }

    // ==================== 内部方法 ====================

    private CmsTag mustGet(Long tagId) {
        CmsTag tag = tagId == null ? null : tagMapper.selectById(tagId);
        if (tag == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    private void ensureKeyUnique(Long siteId, String tagKey, Long excludeId) {
        Long count = tagMapper.selectCount(new LambdaQueryWrapper<CmsTag>()
                .eq(CmsTag::getSiteId, siteId)
                .eq(CmsTag::getTagKey, tagKey)
                .ne(excludeId != null, CmsTag::getTagId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "标签标识已存在：" + tagKey);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
