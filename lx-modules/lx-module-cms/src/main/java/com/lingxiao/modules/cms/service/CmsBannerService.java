package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.cms.entity.CmsBanner;
import com.lingxiao.modules.cms.mapper.CmsBannerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * CMS 轮播 / 广告位服务。
 *
 * <p>按 {@code banner_group} 分组（home / sidebar 等），门户只看启用且
 * 处于起止时间窗内的记录，按 sort 升序。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsBannerService {

    private final CmsBannerMapper bannerMapper;
    private final CmsSiteService siteService;

    public PageResult<CmsBanner> page(long pageNum, long pageSize, Long siteId,
                                      String group, String status) {
        Page<CmsBanner> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<CmsBanner> wrapper = new LambdaQueryWrapper<CmsBanner>()
                .eq(siteId != null, CmsBanner::getSiteId, siteId)
                .eq(StringUtils.hasText(group), CmsBanner::getBannerGroup, group)
                .eq(StringUtils.hasText(status), CmsBanner::getStatus, status)
                .orderByAsc(CmsBanner::getSort)
                .orderByDesc(CmsBanner::getBannerId);
        Page<CmsBanner> result = bannerMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public CmsBanner detail(Long bannerId) {
        return mustGet(bannerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsBanner banner) {
        siteService.mustGet(banner.getSiteId());
        validateTimeRange(banner);
        banner.setBannerId(null);
        banner.setBannerGroup(defaultText(banner.getBannerGroup(), "home"));
        banner.setStatus(defaultStatus(banner.getStatus()));
        banner.setSort(banner.getSort() == null ? 0 : banner.getSort());
        banner.setCreateBy(currentUserId());
        bannerMapper.insert(banner);
        return banner.getBannerId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long bannerId, CmsBanner banner) {
        CmsBanner existing = mustGet(bannerId);
        validateTimeRange(banner);
        existing.setBannerGroup(defaultText(banner.getBannerGroup(), existing.getBannerGroup()));
        existing.setTitle(banner.getTitle());
        existing.setImage(banner.getImage());
        existing.setLinkUrl(banner.getLinkUrl());
        existing.setStartTime(banner.getStartTime());
        existing.setEndTime(banner.getEndTime());
        existing.setStatus(defaultStatus(banner.getStatus()));
        existing.setSort(banner.getSort() == null ? existing.getSort() : banner.getSort());
        existing.setRemark(banner.getRemark());
        existing.setUpdateBy(currentUserId());
        bannerMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long bannerId) {
        mustGet(bannerId);
        bannerMapper.hardDeleteById(bannerId);
    }

    public CmsBanner mustGet(Long bannerId) {
        CmsBanner banner = bannerId == null ? null : bannerMapper.selectById(bannerId);
        if (banner == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.banner.notfound");
        }
        return banner;
    }

    /** 起止时间可空；都填时结束必须晚于开始。 */
    private void validateTimeRange(CmsBanner banner) {
        if (banner.getStartTime() != null && banner.getEndTime() != null
                && !banner.getEndTime().isAfter(banner.getStartTime())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.banner.time.invalid");
        }
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : CommonConstants.STATUS_NORMAL;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
