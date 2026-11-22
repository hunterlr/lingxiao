package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.entity.MemLevel;
import com.lingxiao.modules.member.mapper.MemLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员等级服务。
 *
 * <p>等级数量少，所有查询直接取列表后在内存排序 / 取第一条，不写
 * {@code LIMIT}，方便后续换 PostgreSQL / 达梦 / 金仓。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberLevelService {

    private final MemLevelMapper levelMapper;

    /** 下拉用：只返回启用等级，按 sort 从小到大。 */
    public List<MemLevel> listEnabled() {
        return levelMapper.selectList(new LambdaQueryWrapper<MemLevel>()
                .eq(MemLevel::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByAsc(MemLevel::getSort)
                .orderByAsc(MemLevel::getLevelId));
    }

    /** 注册默认等级：启用等级里 sort 最小的一个。 */
    public MemLevel defaultLevel() {
        List<MemLevel> levels = listEnabled();
        if (levels.isEmpty()) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "会员等级未初始化");
        }
        return levels.get(0);
    }

    /** 后台分配等级时校验：必须存在且启用。 */
    public MemLevel requireEnabled(Long levelId) {
        if (levelId == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.level.required");
        }
        MemLevel level = levelMapper.selectById(levelId);
        if (level == null || !CommonConstants.STATUS_NORMAL.equals(level.getStatus())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.level.invalid");
        }
        return level;
    }

    /** 展示用等级名；等级缺失返回 null，不让资料页 500。 */
    public String nameOf(Long levelId) {
        if (levelId == null) {
            return null;
        }
        MemLevel level = levelMapper.selectById(levelId);
        return level == null ? null : level.getLevelName();
    }

    /**
     * 全部等级（含停用），后台列表补名字用。
     *
     * <p>停用等级也可能挂在老会员身上，如果只查启用等级，列表里这些会员
     * 的等级列会变成空白，看起来像数据丢失。</p>
     */
    public List<MemLevel> listAll() {
        return levelMapper.selectList(new LambdaQueryWrapper<MemLevel>()
                .orderByAsc(MemLevel::getSort)
                .orderByAsc(MemLevel::getLevelId));
    }

    /** 等级 ID → 名称；后台列表一次查询后内存映射，避免每行查一次库。 */
    public Map<Long, String> nameMap() {
        Map<Long, String> result = new LinkedHashMap<>();
        for (MemLevel level : listAll()) {
            result.put(level.getLevelId(), level.getLevelName());
        }
        return result;
    }
}
