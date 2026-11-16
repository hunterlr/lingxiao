package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysNotice;
import com.lingxiao.modules.system.mapper.SysNoticeMapper;
import com.lingxiao.modules.system.support.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 公告服务。
 *
 * <p>公告内容是富文本（WangEditor 5 提交 HTML），<b>入库前必须做 XSS 清洗</b>，
 * 清洗逻辑统一走 {@link HtmlSanitizer}（Jsoup 白名单，不用正则删标签）。</p>
 *
 * <p>清洗点有两处：</p>
 * <ul>
 *   <li>写入：{@link #create} / {@link #update} 落库前清洗，拦住新增脏数据；</li>
 *   <li>读取：{@link #page} / {@link #detail} 返回前再清洗一次，兜住接入清洗之前
 *       已经入库的历史数据。读取侧清洗有少量性能开销，但比“某条老公告突然弹窗”便宜。</li>
 * </ul>
 *
 * <p>不放在前端（可绕过），也不放在 Controller（新增接口容易忘）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNoticeService {

    private final SysNoticeMapper noticeMapper;

    public PageResult<SysNotice> page(long pageNum, long pageSize, String noticeTitle, String noticeType, String status) {
        Page<SysNotice> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .like(StringUtils.hasText(noticeTitle), SysNotice::getNoticeTitle, noticeTitle)
                .eq(StringUtils.hasText(noticeType), SysNotice::getNoticeType, noticeType)
                .eq(StringUtils.hasText(status), SysNotice::getStatus, status)
                .orderByDesc(SysNotice::getCreateTime);
        Page<SysNotice> result = noticeMapper.selectPage(page, wrapper);
        result.getRecords().forEach(item -> item.setNoticeContent(HtmlSanitizer.clean(item.getNoticeContent())));

        PageResult<SysNotice> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    public SysNotice detail(Long noticeId) {
        SysNotice notice = mustGet(noticeId);
        notice.setNoticeContent(HtmlSanitizer.clean(notice.getNoticeContent()));
        return notice;
    }

    public Long create(SysNotice notice) {
        notice.setNoticeId(null);
        notice.setNoticeType(defaultIfBlank(notice.getNoticeType(), "2"));
        notice.setStatus(defaultIfBlank(notice.getStatus(), CommonConstants.STATUS_NORMAL));
        notice.setNoticeContent(HtmlSanitizer.clean(notice.getNoticeContent()));
        notice.setCreateBy(LoginHelper.getUserId());
        noticeMapper.insert(notice);
        return notice.getNoticeId();
    }

    public void update(Long noticeId, SysNotice notice) {
        SysNotice existing = mustGet(noticeId);

        existing.setNoticeTitle(notice.getNoticeTitle());
        existing.setNoticeType(defaultIfBlank(notice.getNoticeType(), existing.getNoticeType()));
        existing.setNoticeContent(HtmlSanitizer.clean(notice.getNoticeContent()));
        existing.setStatus(defaultIfBlank(notice.getStatus(), existing.getStatus()));
        existing.setRemark(notice.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        noticeMapper.updateById(existing);
    }

    /** 公告没有唯一键，物理删除不会与唯一索引冲突。 */
    public void delete(Long noticeId) {
        mustGet(noticeId);
        noticeMapper.hardDeleteById(noticeId);
    }

    // ==================== 内部方法 ====================

    private SysNotice mustGet(Long noticeId) {
        SysNotice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return notice;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
