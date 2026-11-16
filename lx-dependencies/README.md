# lx-dependencies · 依赖版本 BOM

## 这个目录是做什么的

凌霄**唯一的第三方版本入口**。只写 `dependencyManagement`，不写实际依赖。

## 为什么需要它

多模块工程最容易失控的是“每个模块各写一个版本号”。集中到 BOM 后：

- 升级只改一处；
- 内部模块（`lx-common-*` / `lx-module-*` / `lx-admin-api`）也在这里登记，子模块引用不用写 `<version>`；
- 冲突排查只看一个文件。

## 依赖方向

```
根 pom (import) → lx-dependencies → spring-boot-dependencies
```

## 特殊约定（重要）

**本模块不继承根 `pom.xml`**，独立声明坐标。

原因：根 pom `import` 了本 BOM，如果本 BOM 再 `<parent>` 指向根 pom，Maven 会报：

```
The dependencies of type=pom and with scope=import form a cycle
```

## 改版本时必须同步

| 改动 | 还要改哪里 |
|---|---|
| Spring Boot 版本 | 根 pom 的 `<parent>`、本文件 import 的版本 |
| Flyway 版本 | 本文件 + `lx-boot/pom.xml`（显式 7.15.0） |
| 依赖矩阵 | `docs/11-jdk-engineering.md` |

## 禁止事项

- ❌ 在这里写 `<dependencies>`（实际依赖）。
- ❌ 在这里写业务代码或插件配置。
- ❌ 随手升级 Flyway 到 8.x：本机 MySQL 5.7 会直接启动失败，见 `docs/21-build-log.md` 第 5.5 节。
