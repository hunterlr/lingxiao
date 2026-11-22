package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.dto.SysConfigValueRequest;
import com.lingxiao.modules.system.entity.SysConfig;
import com.lingxiao.modules.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 系统参数服务。
 *
 * <p>{@code configType = 'Y'} 是内置参数：<b>允许改值，不允许改键名 / 控件类型 / 选项，不允许删除</b>。
 * 原因很直接：代码里按 key 读取参数（例如 {@code sys.account.captchaEnabled}），
 * 键名一变代码就找不到；控件类型一改，原来的值可能不再可解析（把开关改成数字）。
 * 这类故障不会在保存时报错，只会在某个功能悄悄失效时暴露。</p>
 *
 * <p>{@code controlType} 只决定前端用什么控件编辑，<b>不是安全边界</b>：
 * 新增 / 修改都会走 {@link #normalizeAndValidateControl}，直接调接口塞非法值同样被拒绝。</p>
 *
 * <p>v1.0 没有参数缓存，读取直接查库；后续加 Caffeine / Redis 时，
 * {@link #update} 与 {@link #delete} 必须同步失效缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService implements ConfigProvider {

    /**
     * 允许登录用户直接读取的公开参数键。
     *
     * <p>只放“业务页面需要、且不含敏感信息”的配置。新增前先确认：
     * 值泄露给任意后台账号是否可接受。</p>
     */
    private static final Set<String> PUBLIC_CONFIG_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("sys.editor.type")));

    public static final String CONTROL_TEXT = "text";
    public static final String CONTROL_NUMBER = "number";
    public static final String CONTROL_SWITCH = "switch";
    public static final String CONTROL_SELECT = "select";

    /** 控件类型白名单；与前端 `src/utils/config-control.js` 保持一致。 */
    private static final Set<String> CONTROL_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(CONTROL_TEXT, CONTROL_NUMBER, CONTROL_SWITCH, CONTROL_SELECT)));

    /** number 的选项格式：{@code min:max}，两侧都可省略。 */
    private static final Pattern NUMBER_RANGE_PATTERN = Pattern.compile("^(-?\\d+)?:(-?\\d+)?$");

    private static final String DEFAULT_SWITCH_OPTIONS = "true:false";

    private final SysConfigMapper configMapper;

    /**
     * 分页查询参数。
     *
     * <p>{@code configKeyPrefix} / {@code excludeConfigKeyPrefix} 用于按模块拆分参数页：
     * 通用参数页排除 {@code mem.}，会员设置页只看 {@code mem.}。
     * 用前缀而不是精确 key，是为了以后模块新增参数时页面不用改代码。</p>
     */
    public PageResult<SysConfig> page(long pageNum, long pageSize, String configName, String configKey,
                                      String configType, String controlType,
                                      String configKeyPrefix, String excludeConfigKeyPrefix) {
        Page<SysConfig> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .like(StringUtils.hasText(configName), SysConfig::getConfigName, configName)
                .like(StringUtils.hasText(configKey), SysConfig::getConfigKey, configKey)
                .likeRight(StringUtils.hasText(configKeyPrefix), SysConfig::getConfigKey, configKeyPrefix)
                .notLikeRight(StringUtils.hasText(excludeConfigKeyPrefix), SysConfig::getConfigKey, excludeConfigKeyPrefix)
                .eq(StringUtils.hasText(configType), SysConfig::getConfigType, configType)
                .eq(StringUtils.hasText(controlType), SysConfig::getControlType, controlType)
                .orderByAsc(SysConfig::getConfigId);
        Page<SysConfig> result = configMapper.selectPage(page, wrapper);

        PageResult<SysConfig> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    public SysConfig detail(Long configId) {
        return mustGet(configId);
    }

    /** 按 key 取参数值；不存在返回 null，由调用方决定默认值。 */
    public String getValueByKey(String configKey) {
        List<SysConfig> list = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey));
        return list.isEmpty() ? null : list.get(0).getConfigValue();
    }

    /**
     * {@link ConfigProvider} 实现：给其它模块（如文件模块）读参数用。
     *
     * <p>只暴露“按 key 取值”，不暴露 CRUD，避免别的模块绕过参数校验直接改库。</p>
     */
    @Override
    public String getValue(String configKey) {
        return getValueByKey(configKey);
    }

    /**
     * 读取“公开参数”：登录即可读，不要求 {@code config:list} 权限。
     *
     * <p>为什么需要：富文本引擎这类参数是普通业务页面（公告管理）要用的，
     * 但公告管理员通常没有参数管理权限。用白名单限制可读 key，
     * 非白名单 key 返回 null，既不报错也不暴露该参数是否存在。</p>
     *
     * <p>新增公开参数时必须评估：值里不能含密钥、内网地址等敏感信息。</p>
     */
    public String getPublicValue(String configKey) {
        if (!PUBLIC_CONFIG_KEYS.contains(configKey)) {
            return null;
        }
        return getValueByKey(configKey);
    }

    public Long create(SysConfig config) {
        checkConfigKeyUnique(config.getConfigKey(), null);
        config.setConfigId(null);
        config.setConfigType(defaultIfBlank(config.getConfigType(), CommonConstants.NO));
        normalizeAndValidateControl(config);
        config.setCreateBy(LoginHelper.getUserId());
        configMapper.insert(config);
        return config.getConfigId();
    }

    /**
     * 修改参数。
     *
     * <p>内置参数（Y）只允许改名称、值、备注；键名、控件类型与选项被强制还原，
     * 前端即使提交了也会被忽略。</p>
     */
    public void update(Long configId, SysConfig config) {
        SysConfig existing = mustGet(configId);
        boolean builtin = CommonConstants.YES.equals(existing.getConfigType());

        if (builtin) {
            if (!existing.getConfigKey().equals(config.getConfigKey())) {
                throw new ServiceException(ErrorCode.FAIL, "内置参数的键名不允许修改");
            }
        } else if (!existing.getConfigKey().equals(config.getConfigKey())) {
            checkConfigKeyUnique(config.getConfigKey(), configId);
        }

        existing.setConfigName(config.getConfigName());
        existing.setConfigKey(config.getConfigKey());
        existing.setConfigValue(config.getConfigValue());
        existing.setRemark(config.getRemark());
        if (builtin) {
            config.setControlType(existing.getControlType());
            config.setConfigOptions(existing.getConfigOptions());
        } else {
            existing.setConfigType(defaultIfBlank(config.getConfigType(), existing.getConfigType()));
        }
        normalizeAndValidateControl(config);
        existing.setControlType(config.getControlType());
        existing.setConfigOptions(config.getConfigOptions());
        existing.setUpdateBy(LoginHelper.getUserId());
        configMapper.updateById(existing);
        log.info("修改系统参数: key={}, 操作人={}", existing.getConfigKey(), LoginHelper.getUsername());
    }

    /**
     * 批量改值：设置类页面一次保存多个参数。
     *
     * <p>先全部校验再统一写库，避免前几个参数已保存、后一个参数校验失败，
     * 页面却提示“保存失败”的半个状态。只改值，不改键名 / 控件 / 选项，
     * 因此同样受内置参数约束与控件校验保护。</p>
     */
    public void updateValues(List<SysConfigValueRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "至少提交一个参数");
        }
        List<SysConfig> pending = new ArrayList<>(items.size());
        for (SysConfigValueRequest item : items) {
            SysConfig existing = mustGet(item.getConfigId());
            SysConfig probe = new SysConfig();
            probe.setConfigValue(item.getConfigValue() == null ? "" : item.getConfigValue().trim());
            probe.setControlType(existing.getControlType());
            probe.setConfigOptions(existing.getConfigOptions());
            normalizeAndValidateControl(probe);
            existing.setConfigValue(probe.getConfigValue());
            existing.setUpdateBy(LoginHelper.getUserId());
            pending.add(existing);
        }
        for (SysConfig existing : pending) {
            configMapper.updateById(existing);
        }
        log.info("批量修改系统参数: count={}, 操作人={}", pending.size(), LoginHelper.getUsername());
    }

    /** 删除参数：内置参数拒绝删除。 */
    public void delete(Long configId) {
        SysConfig config = mustGet(configId);
        if (CommonConstants.YES.equals(config.getConfigType())) {
            throw new ServiceException(ErrorCode.FAIL, "内置参数不允许删除");
        }
        configMapper.hardDeleteById(configId);
    }

    // ==================== 内部方法 ====================

    /**
     * 归一化并校验控件类型 / 选项 / 值。
     *
     * <p>值校验放在服务端而不是只放前端，原因和 notice 的 XSS 清洗一样：
     * 抓包直接调接口可以绕过任何前端校验。</p>
     *
     * @throws ServiceException 控件类型未知、选项格式错误、值不满足控件约束时
     */
    private void normalizeAndValidateControl(SysConfig config) {
        String type = defaultIfBlank(config.getControlType(), CONTROL_TEXT);
        if (!CONTROL_TYPES.contains(type)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "不支持的控件类型: " + type);
        }
        String value = config.getConfigValue() == null ? "" : config.getConfigValue().trim();
        String options = trimToNull(config.getConfigOptions());

        if (CONTROL_NUMBER.equals(type)) {
            int number;
            try {
                number = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "数字参数必须是整数: " + value);
            }
            int[] range = parseNumberRange(options);
            if (number < range[0] || number > range[1]) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "数字参数超出范围 " + range[0] + "~" + range[1] + ": " + value);
            }
        } else if (CONTROL_SWITCH.equals(type)) {
            String[] pair = parseSwitchOptions(options);
            options = pair[0] + ":" + pair[1];
            if (!pair[0].equals(value) && !pair[1].equals(value)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "开关参数只能是 " + pair[0] + " 或 " + pair[1] + ": " + value);
            }
        } else if (CONTROL_SELECT.equals(type)) {
            Map<String, String> selectOptions = parseSelectOptions(options);
            if (selectOptions.isEmpty()) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "下拉参数必须配置可选项");
            }
            if (!selectOptions.containsKey(value)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "参数值不在可选项内: " + value);
            }
            options = joinSelectOptions(selectOptions);
        } else {
            // text：选项无意义，直接丢弃，避免留下误导性的残留配置
            options = null;
        }

        config.setControlType(type);
        config.setConfigOptions(options);
    }

    /**
     * 解析 number 的范围 {@code min:max}。
     *
     * <p>返回 {@code [Integer.MIN_VALUE, Integer.MAX_VALUE]} 表示无边界；
     * 格式不合法或 min > max 直接拒绝。</p>
     */
    private int[] parseNumberRange(String options) {
        if (options == null) {
            return new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE};
        }
        if (!NUMBER_RANGE_PATTERN.matcher(options).matches()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "数字范围格式应为 最小值:最大值，例如 1:100: " + options);
        }
        String[] parts = options.split(":", -1);
        int min = parts[0].isEmpty() ? Integer.MIN_VALUE : Integer.parseInt(parts[0]);
        int max = parts[1].isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(parts[1]);
        if (min > max) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "数字范围最小值不能大于最大值: " + options);
        }
        return new int[]{min, max};
    }

    /**
     * 解析 switch 的开关值 {@code on:off}；空值默认 {@code true:false}。
     */
    private String[] parseSwitchOptions(String options) {
        if (options == null) {
            return DEFAULT_SWITCH_OPTIONS.split(":", -1);
        }
        String[] parts = options.split(":", -1);
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()
                || parts[0].trim().equals(parts[1].trim())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "开关选项格式应为 开值:关值，且两值不同，例如 true:false");
        }
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    /**
     * 解析 select 的选项 {@code value:label,value:label}。
     *
     * <p>分隔符兼容英文逗号、中文逗号与换行；没写显示名时用值本身；
     * 空值与重复值直接拒绝，避免前端下拉出现两个一样的项。</p>
     */
    private Map<String, String> parseSelectOptions(String options) {
        Map<String, String> result = new LinkedHashMap<>();
        if (options == null) {
            return result;
        }
        for (String raw : options.split("[,，\\r\\n]")) {
            String item = raw.trim();
            if (item.isEmpty()) {
                continue;
            }
            int idx = item.indexOf(':');
            String value = (idx < 0 ? item : item.substring(0, idx)).trim();
            String label = idx < 0 ? value : item.substring(idx + 1).trim();
            if (value.isEmpty()) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "下拉选项的值不能为空: " + item);
            }
            if (result.containsKey(value)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "下拉选项的值重复: " + value);
            }
            result.put(value, label.isEmpty() ? value : label);
        }
        return result;
    }

    private String joinSelectOptions(Map<String, String> options) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    private SysConfig mustGet(Long configId) {
        SysConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "系统参数不存在");
        }
        return config;
    }

    private void checkConfigKeyUnique(String configKey, Long excludeConfigId) {
        Long count = configMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .ne(excludeConfigId != null, SysConfig::getConfigId, excludeConfigId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.FAIL, "参数键名已存在: " + configKey);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
