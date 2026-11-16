package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysDictData;
import com.lingxiao.modules.system.entity.SysDictType;
import com.lingxiao.modules.system.mapper.SysDictDataMapper;
import com.lingxiao.modules.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 字典管理服务。
 *
 * <p>字典分两层：类型（{@code sys_dict_type}）+ 数据（{@code sys_dict_data}）。
 * 前端下拉框按 {@code dictType} 一次取全部数据，所以**不提供“全表字典”接口**，
 * 否则数据量一大就会拖慢首屏。</p>
 *
 * <p>删除类型前必须确认其下没有数据：允许“类型没了但数据还在”的话，
 * 那些数据的 {@code dict_type} 会变成孤儿，前端永远查不到，也无法在界面上清理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    // ==================== 字典类型 ====================

    public PageResult<SysDictType> pageType(long pageNum, long pageSize, String dictName, String dictType, String status) {
        Page<SysDictType> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .like(StringUtils.hasText(dictName), SysDictType::getDictName, dictName)
                .like(StringUtils.hasText(dictType), SysDictType::getDictType, dictType)
                .eq(StringUtils.hasText(status), SysDictType::getStatus, status)
                .orderByAsc(SysDictType::getDictId);
        Page<SysDictType> result = dictTypeMapper.selectPage(page, wrapper);

        PageResult<SysDictType> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /** 类型下拉：只返回启用类型。 */
    public List<SysDictType> optionsType() {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByAsc(SysDictType::getDictId));
    }

    public SysDictType detailType(Long dictId) {
        return mustGetType(dictId);
    }

    public Long createType(SysDictType type) {
        checkDictTypeUnique(type.getDictType(), null);
        type.setDictId(null);
        type.setStatus(defaultIfBlank(type.getStatus(), CommonConstants.STATUS_NORMAL));
        type.setCreateBy(LoginHelper.getUserId());
        dictTypeMapper.insert(type);
        return type.getDictId();
    }

    /**
     * 修改字典类型。
     *
     * <p>改 {@code dictType} 编码时要同步把该类型下所有数据的 {@code dictType} 一起改，
     * 否则类型和数据会瞬间失联。这是唯一需要跨表更新字典的场景。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long dictId, SysDictType type) {
        SysDictType existing = mustGetType(dictId);
        String oldType = existing.getDictType();
        String newType = type.getDictType();
        if (!oldType.equals(newType)) {
            checkDictTypeUnique(newType, dictId);
        }

        existing.setDictName(type.getDictName());
        existing.setDictType(newType);
        existing.setStatus(defaultIfBlank(type.getStatus(), existing.getStatus()));
        existing.setRemark(type.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        dictTypeMapper.updateById(existing);

        if (!oldType.equals(newType)) {
            int moved = dictDataMapper.update(null, new LambdaUpdateWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, oldType)
                    .set(SysDictData::getDictType, newType));
            log.info("字典类型编码变更: {} -> {}, 同步数据 {} 条", oldType, newType, moved);
        }
    }

    /** 删除字典类型：有数据时拒绝，避免孤儿数据。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long dictId) {
        SysDictType type = mustGetType(dictId);
        Long dataCount = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, type.getDictType()));
        if (dataCount != null && dataCount > 0) {
            throw new ServiceException(ErrorCode.FAIL,
                    "该字典类型下还有 " + dataCount + " 条数据，请先删除数据");
        }
        dictTypeMapper.hardDeleteById(dictId);
    }

    // ==================== 字典数据 ====================

    public PageResult<SysDictData> pageData(long pageNum, long pageSize, String dictType, String dictLabel, String status) {
        Page<SysDictData> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(StringUtils.hasText(dictType), SysDictData::getDictType, dictType)
                .like(StringUtils.hasText(dictLabel), SysDictData::getDictLabel, dictLabel)
                .eq(StringUtils.hasText(status), SysDictData::getStatus, status)
                .orderByAsc(SysDictData::getDictSort)
                .orderByAsc(SysDictData::getDictCode);
        Page<SysDictData> result = dictDataMapper.selectPage(page, wrapper);

        PageResult<SysDictData> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /** 按类型取全部启用数据（前端下拉框用，不分页）。 */
    public List<SysDictData> listDataByType(String dictType) {
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByAsc(SysDictData::getDictSort)
                .orderByAsc(SysDictData::getDictCode));
    }

    public SysDictData detailData(Long dictCode) {
        SysDictData data = dictDataMapper.selectById(dictCode);
        if (data == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "字典数据不存在");
        }
        return data;
    }

    public Long createData(SysDictData data) {
        mustExistType(data.getDictType());
        data.setDictCode(null);
        data.setDictSort(data.getDictSort() == null ? 0 : data.getDictSort());
        data.setIsDefault(defaultIfBlank(data.getIsDefault(), CommonConstants.NO));
        data.setStatus(defaultIfBlank(data.getStatus(), CommonConstants.STATUS_NORMAL));
        data.setCreateBy(LoginHelper.getUserId());
        dictDataMapper.insert(data);
        return data.getDictCode();
    }

    public void updateData(Long dictCode, SysDictData data) {
        SysDictData existing = detailData(dictCode);
        mustExistType(data.getDictType());

        existing.setDictType(data.getDictType());
        existing.setDictSort(data.getDictSort() == null ? existing.getDictSort() : data.getDictSort());
        existing.setDictLabel(data.getDictLabel());
        existing.setDictValue(data.getDictValue());
        existing.setCssClass(data.getCssClass());
        existing.setListClass(data.getListClass());
        existing.setIsDefault(defaultIfBlank(data.getIsDefault(), existing.getIsDefault()));
        existing.setStatus(defaultIfBlank(data.getStatus(), existing.getStatus()));
        existing.setRemark(data.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        dictDataMapper.updateById(existing);
    }

    public void deleteData(Long dictCode) {
        detailData(dictCode);
        dictDataMapper.hardDeleteById(dictCode);
    }

    // ==================== 内部方法 ====================

    private SysDictType mustGetType(Long dictId) {
        SysDictType type = dictTypeMapper.selectById(dictId);
        if (type == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    /** 数据必须挂在已存在的类型下，否则前端永远查不到。 */
    private void mustExistType(String dictType) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType));
        if (count == null || count == 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "字典类型不存在: " + dictType);
        }
    }

    private void checkDictTypeUnique(String dictType, Long excludeDictId) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)
                .ne(excludeDictId != null, SysDictType::getDictId, excludeDictId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.FAIL, "字典类型编码已存在: " + dictType);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
