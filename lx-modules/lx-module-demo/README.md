# lx-module-demo · 可选模块关闭态验证

## 这个目录是做什么的

**唯一作用**：证明模块开关真的生效，而不是“只隐藏了菜单”。

验证三点：

1. `lx.module.modules.demo.enabled=false` → Bean 不装配、接口 **404**；
2. 改成 `true` → 接口返回 `pong`；
3. 基座（framework / boot）**没有 import 本模块任何类**。

## 代码

```java
@RestController
@RequestMapping("/api/admin/demo")
@ConditionalOnProperty(name = "lx.module.modules.demo.enabled", havingValue = "true")
public class DemoController {
    @GetMapping("/ping")
    public R<String> ping() { return R.ok("pong"); }
}
```

`@ConditionalOnProperty` 保证关闭时**连 Controller Bean 都不创建**，所以是 404，而不是“接口在但抛异常”。

## 验证步骤

```powershell
# 关闭态（默认）
java -jar lx-boot\target\lingxiao.jar
# GET /api/admin/demo/ping → 404
# GET /api/admin/modules    → demo:false

# 开启态
java -jar lx-boot\target\lingxiao.jar --lx.module.modules.demo.enabled=true
# GET /api/admin/demo/ping → {"code":0,"data":"pong"}
# GET /api/admin/modules    → demo:true
```

## 生命周期

- CMS 模块（v1.1）上线并验证后，**本模块可直接删除**；
- 生产打包时不应包含本模块。

## 禁止事项

- ❌ 往这里放真实业务代码。
- ❌ 让其他模块依赖本模块（它只是验证用例）。
- ❌ 在生产 `application.yml` 里把它设为 `enabled: true`。
