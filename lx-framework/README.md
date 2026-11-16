# lx-framework · 模块机制与自动装配

## 这个目录是做什么的

后台**基座的“插拔器”**：读取 `lx.module.*` 开关、生成模块注册表、向前端下发启用状态。

## 包结构

```
com.lingxiao.framework
├─ module/LxModuleProperties   配置绑定（Map<String, ModuleSwitch>）
├─ module/ModuleRegistry       查询是否启用 / 列出全部模块
├─ module/ModuleInfo           只读视图
├─ module/ModuleController     GET /api/admin/modules
└─ config/LxFrameworkAutoConfiguration  启用配置绑定
```

## 开关怎么写

```yaml
lx:
  module:
    modules:
      demo:
        enabled: false
        name: 演示模块
        description: 验证可选模块关闭后 Bean 不加载、接口 404
```

**新增模块只加一段 YAML，不改基座代码。** 这就是用 `Map` 而不是给每个模块写字段的原因。

## 关闭模块时发生什么

不是“隐藏菜单”，而是：

1. 模块自己的 `@ConditionalOnProperty` 不满足 → **Bean 不装配**；
2. 接口不存在 → 返回 **404**（不是“接口在但抛异常”）；
3. `/api/admin/modules` 返回 `enabled=false` → 前端不注册路由、不显示菜单；
4. 生产可在 Maven 打包阶段直接不引入该模块 jar。

## 依赖方向

```
lx-framework → lx-common-core + lx-common-web + spring-boot-starter-web
```

**不 import 任何 `lx-module-*` 的类。** 基座只认“模块编码 + 开关”。

## 禁止事项

- ❌ 在 `lx-framework` 里 `import com.lingxiao.modules.*`（基座会被具体模块绑架）。
- ❌ 让模块注册表负责实例化模块 Bean（应由模块自己的条件注解控制）。
- ❌ 把模块开关做成数据库表却不做缓存（启动期就要用，不能依赖运行期查库）。
