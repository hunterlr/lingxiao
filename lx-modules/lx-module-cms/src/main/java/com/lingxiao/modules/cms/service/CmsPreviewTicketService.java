package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.cms.dto.CmsPreviewTicketVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.entity.CmsPreviewTicket;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import com.lingxiao.modules.cms.mapper.CmsPreviewTicketMapper;
import com.lingxiao.modules.cms.support.CmsTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 草稿预览票据服务。
 *
 * <p>后台为草稿生成一次性票据，门户凭票打开预览页。约束：</p>
 * <ul>
 *   <li>票据绑定站点与目标，不能跨站点使用；</li>
 *   <li>默认 10 分钟过期（{@code lx.cms.preview-ttl-minutes}）；</li>
 *   <li>只能消费一次，重复打开按失效处理。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsPreviewTicketService {

    public static final String TARGET_ARTICLE = "article";
    public static final String TARGET_PAGE = "page";
    public static final String TARGET_CATEGORY = "category";

    private final CmsPreviewTicketMapper ticketMapper;
    private final CmsArticleMapper articleMapper;
    private final CmsPageMapper pageMapper;
    private final CmsCategoryMapper categoryMapper;

    @Value("${lx.cms.preview-ttl-minutes:10}")
    private int ttlMinutes = 10;

    /** 后台生成预览票据；草稿 / 已下线都可预览，但目标必须存在且同站点。 */
    @Transactional(rollbackFor = Exception.class)
    public CmsPreviewTicketVO issue(String targetType, Long targetId) {
        return issue(targetType, targetId, null);
    }

    /**
     * 生成预览票据，可指定覆盖模板。
     *
     * <p>模板按目标类型限制：article 只接受 detail.*，page 只接受 page.*，
     * category 只接受 list.*；空值表示沿用目标自身模板。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public CmsPreviewTicketVO issue(String targetType, Long targetId, String template) {
        if (!StringUtils.hasText(targetType) || targetId == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.target.missing");
        }
        Long siteId;
        if (TARGET_ARTICLE.equals(targetType)) {
            CmsArticle article = articleMapper.selectById(targetId);
            if (article == null) {
                throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.article.notfound");
            }
            siteId = article.getSiteId();
        } else if (TARGET_PAGE.equals(targetType)) {
            CmsPage page = pageMapper.selectById(targetId);
            if (page == null) {
                throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.page.notfound");
            }
            siteId = page.getSiteId();
        } else if (TARGET_CATEGORY.equals(targetType)) {
            CmsCategory category = categoryMapper.selectById(targetId);
            if (category == null) {
                throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.category.notfound");
            }
            siteId = category.getSiteId();
        } else {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.target.missing");
        }
        String previewTemplate = CmsTemplates.normalizePreviewTemplate(targetType, template);

        LocalDateTime now = LocalDateTime.now();
        CmsPreviewTicket ticket = new CmsPreviewTicket();
        ticket.setTicket(UUID.randomUUID().toString().replace("-", ""));
        ticket.setSiteId(siteId);
        ticket.setTargetType(targetType);
        ticket.setTargetId(targetId);
        ticket.setTemplate(previewTemplate);
        ticket.setExpireTime(now.plusMinutes(Math.max(ttlMinutes, 1)));
        ticket.setUsed("0");
        ticket.setCreateBy(currentUserId());
        ticketMapper.insert(ticket);

        CmsPreviewTicketVO vo = new CmsPreviewTicketVO();
        vo.setTicket(ticket.getTicket());
        vo.setExpireTime(ticket.getExpireTime());
        return vo;
    }

    /**
     * 消费票据并返回绑定信息。
     *
     * <p>先原子 UPDATE 抢占，成功后再读记录；并发下只有第一个请求能拿到内容。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public CmsPreviewTicket consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.ticket.invalid");
        }
        String normalized = ticket.trim();
        int updated = ticketMapper.consume(normalized);
        if (updated == 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.ticket.invalid");
        }
        CmsPreviewTicket row = ticketMapper.selectOne(new LambdaQueryWrapper<CmsPreviewTicket>()
                .eq(CmsPreviewTicket::getTicket, normalized));
        if (row == null || !Objects.equals("1", row.getUsed())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.ticket.invalid");
        }
        return row;
    }

    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
