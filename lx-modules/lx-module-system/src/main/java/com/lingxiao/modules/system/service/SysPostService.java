package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysPost;
import com.lingxiao.modules.system.mapper.SysPostMapper;
import com.lingxiao.modules.system.mapper.SysUserPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 岗位管理服务。
 *
 * <p>岗位是“职务标签”，不承载权限；权限只挂在角色上。因此岗位可以放心删除，
 * 只需先确认没有用户占用，否则用户详情里的岗位会变成悬空 ID。</p>
 *
 * <p>{@code post_code} 是业务标识（唯一索引 {@code uk_post_code}），改名允许，改编码要校验唯一性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysPostService {

    private final SysPostMapper postMapper;
    private final SysUserPostMapper userPostMapper;

    public PageResult<SysPost> page(long pageNum, long pageSize, String postCode, String postName, String status) {
        Page<SysPost> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .like(StringUtils.hasText(postCode), SysPost::getPostCode, postCode)
                .like(StringUtils.hasText(postName), SysPost::getPostName, postName)
                .eq(StringUtils.hasText(status), SysPost::getStatus, status)
                .orderByAsc(SysPost::getSort)
                .orderByAsc(SysPost::getPostId);
        Page<SysPost> result = postMapper.selectPage(page, wrapper);

        PageResult<SysPost> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /** 下拉选项：只返回启用岗位。 */
    public List<SysPost> options() {
        return postMapper.selectList(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByAsc(SysPost::getSort));
    }

    public SysPost detail(Long postId) {
        return mustGet(postId);
    }

    public Long create(SysPost post) {
        checkPostCodeUnique(post.getPostCode(), null);
        post.setPostId(null);
        post.setSort(post.getSort() == null ? 0 : post.getSort());
        post.setStatus(defaultIfBlank(post.getStatus(), CommonConstants.STATUS_NORMAL));
        post.setCreateBy(LoginHelper.getUserId());
        postMapper.insert(post);
        return post.getPostId();
    }

    public void update(Long postId, SysPost post) {
        SysPost existing = mustGet(postId);
        if (!existing.getPostCode().equals(post.getPostCode())) {
            checkPostCodeUnique(post.getPostCode(), postId);
        }
        existing.setPostCode(post.getPostCode());
        existing.setPostName(post.getPostName());
        existing.setSort(post.getSort() == null ? existing.getSort() : post.getSort());
        existing.setStatus(defaultIfBlank(post.getStatus(), existing.getStatus()));
        existing.setRemark(post.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        postMapper.updateById(existing);
    }

    /**
     * 删除岗位（物理删除）。
     *
     * <p>与用户管理不同，岗位被占用时**不级联删除**关联行：用户岗位是明确的业务映射，
     * 静默抹掉会让管理员以为“只是删了个岗位”。这里直接拒绝，让管理员先调整用户。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long postId) {
        mustGet(postId);
        int userCount = userPostMapper.countByPostId(postId);
        if (userCount > 0) {
            throw new ServiceException(ErrorCode.FAIL,
                    "该岗位已分配给 " + userCount + " 个用户，请先解除关联再删除");
        }
        postMapper.hardDeleteById(postId);
    }

    // ==================== 内部方法 ====================

    private SysPost mustGet(Long postId) {
        SysPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "岗位不存在");
        }
        return post;
    }

    private void checkPostCodeUnique(String postCode, Long excludePostId) {
        Long count = postMapper.selectCount(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostCode, postCode)
                .ne(excludePostId != null, SysPost::getPostId, excludePostId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.FAIL, "岗位编码已存在: " + postCode);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
