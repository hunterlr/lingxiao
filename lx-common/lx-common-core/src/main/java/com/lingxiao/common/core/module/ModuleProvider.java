package com.lingxiao.common.core.module;

/**
 * 模块启用状态 SPI。
 *
 * <p><b>为什么放在 common-core：</b>系统模块需要按 {@code sys_menu.module_code}
 * 过滤菜单与权限码，但它不能依赖 {@code lx-framework}（框架层依赖所有模块，
 * 反过来会形成环）。这里只定义最小判断能力，由框架层的模块注册表实现。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>只读。模块开关来自配置文件，改完必须重启，不支持运行期热切换；</li>
 *   <li>没有实现时调用方按“模块全部启用”处理，保证单独启动某个模块也能跑；</li>
 *   <li>基座模块（system / file）永远启用，不参与开关判断。</li>
 * </ul>
 */
public interface ModuleProvider {

    /**
     * 模块是否启用。
     *
     * @param moduleCode 模块编码，对应 {@code sys_menu.module_code}；
     *                   为空时按启用处理，兼容没有模块归属的旧数据
     */
    boolean isEnabled(String moduleCode);
}
