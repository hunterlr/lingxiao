package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.web.util.HtmlSanitizer;
import com.lingxiao.modules.cms.entity.CmsBlock;
import com.lingxiao.modules.cms.mapper.CmsBlockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * CMS 区块 / 碎片服务。
 *
 * <p>区块用于首页推荐位、可复用文案等“不属于某篇文章”的内容，
 * 门户按 {@code block_key} 读取。html 类型正文统一清洗。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsBlockService {

    private static final String TYPE_HTML = "html";
    private static final String TYPE_IMAGE = "image";

    private final CmsBlockMapper blockMapper;
    private final CmsSiteService siteService;

    public PageResult<CmsBlock> page(long pageNum, long pageSize, Long siteId,
                                     String keyword, String status) {
        Page<CmsBlock> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<CmsBlock> wrapper = new LambdaQueryWrapper<CmsBlock>()
                .select(CmsBlock.class, field -> !"content".equals(field.getColumn()))
                .eq(siteId != null, CmsBlock::getSiteId, siteId)
                .eq(StringUtils.hasText(status), CmsBlock::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(CmsBlock::getBlockName, keyword)
                        .or().like(CmsBlock::getBlockKey, keyword))
                .orderByAsc(CmsBlock::getSort)
                .orderByDesc(CmsBlock::getBlockId);
        Page<CmsBlock> result = blockMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public CmsBlock detail(Long blockId) {
        CmsBlock block = mustGet(blockId);
        block.setContent(HtmlSanitizer.clean(block.getContent()));
        return block;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsBlock block) {
        siteService.mustGet(block.getSiteId());
        String blockKey = block.getBlockKey().trim();
        ensureKeyUnique(block.getSiteId(), blockKey, null);
        block.setBlockId(null);
        block.setBlockKey(blockKey);
        block.setBlockName(block.getBlockName().trim());
        block.setBlockType(validateType(block.getBlockType()));
        block.setContent(cleanIfHtml(block.getBlockType(), block.getContent()));
        block.setStatus(defaultStatus(block.getStatus()));
        block.setSort(block.getSort() == null ? 0 : block.getSort());
        block.setCreateBy(currentUserId());
        blockMapper.insert(block);
        return block.getBlockId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long blockId, CmsBlock block) {
        CmsBlock existing = mustGet(blockId);
        String blockKey = block.getBlockKey().trim();
        ensureKeyUnique(existing.getSiteId(), blockKey, blockId);
        existing.setBlockKey(blockKey);
        existing.setBlockName(block.getBlockName().trim());
        existing.setBlockType(validateType(block.getBlockType()));
        existing.setContent(cleanIfHtml(block.getBlockType(), block.getContent()));
        existing.setCover(block.getCover());
        existing.setLinkUrl(block.getLinkUrl());
        existing.setStatus(defaultStatus(block.getStatus()));
        existing.setSort(block.getSort() == null ? existing.getSort() : block.getSort());
        existing.setRemark(block.getRemark());
        existing.setUpdateBy(currentUserId());
        blockMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long blockId) {
        mustGet(blockId);
        blockMapper.hardDeleteById(blockId);
    }

    public CmsBlock mustGet(Long blockId) {
        CmsBlock block = blockId == null ? null : blockMapper.selectById(blockId);
        if (block == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.block.notfound");
        }
        return block;
    }

    private String validateType(String type) {
        if (!TYPE_HTML.equals(type) && !TYPE_IMAGE.equals(type)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.block.type.invalid");
        }
        return type;
    }

    private String cleanIfHtml(String type, String content) {
        return TYPE_HTML.equals(type) ? HtmlSanitizer.clean(content) : content;
    }

    private void ensureKeyUnique(Long siteId, String blockKey, Long excludeId) {
        Long count = blockMapper.selectCount(new LambdaQueryWrapper<CmsBlock>()
                .eq(CmsBlock::getSiteId, siteId)
                .eq(CmsBlock::getBlockKey, blockKey)
                .ne(excludeId != null, CmsBlock::getBlockId, excludeId));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.block.key.duplicate");
        }
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : CommonConstants.STATUS_NORMAL;
    }

    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
