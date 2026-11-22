package com.lingxiao.framework.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 按模块开关拼装 Flyway 迁移目录。
 *
 * <p>约定：模块编码 = 目录名，例如 {@code cms} → {@code classpath:db/migration/cms}。
 * 基座模块 {@code system} / {@code file} 永远参与；其它模块只有
 * {@code lx.module.modules.<code>.enabled=true} 且目录真实存在时才加入。</p>
 *
 * <p>为什么不能把目录写死在 YAML：模块关闭后如果仍扫描它的迁移，
 * 会继续建表 / 改表，违背“关闭模块不产生副作用”的约定。</p>
 *
 * <p>目录存在性用 {@link Predicate} 注入，方便单测不依赖 classpath。</p>
 */
public final class ModuleMigrationLocations {

    private static final String PREFIX = "classpath:db/migration/";

    private ModuleMigrationLocations() {
    }

    /**
     * @param properties 模块开关
     * @param directoryExists 判断 {@code db/migration/<moduleCode>} 是否存在
     * @return 可直接传给 Flyway 的 locations，顺序固定为 system、file、其余按编码排序
     */
    public static List<String> build(LxModuleProperties properties, Predicate<String> directoryExists) {
        List<String> codes = new ArrayList<>();
        codes.add("system");
        codes.add("file");

        properties.getModules().entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .sorted()
                .filter(code -> !codes.contains(code))
                .forEach(codes::add);

        return codes.stream()
                .filter(directoryExists)
                .map(code -> PREFIX + code)
                .collect(Collectors.toList());
    }
}
