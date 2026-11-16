# lx-common-log · 操作日志

## 这个目录是做什么的

提供 `@Log` 注解与切面，统一记录“谁、什么时候、调了什么、耗时多少、成功失败”。

切面**只负责采集**；写到哪里由 `OperLogStore` SPI 决定。这样公共模块不反向依赖业务模块，
裁剪掉系统模块时切面自动退化为只打应用日志，不会报“找不到 Bean”。

## 包结构

```
com.lingxiao.common.log
├─ annotation/Log        注解：title、businessType、saveRequestData
├─ enums/BusinessType    操作类型（存序号：0 其他 … 8 清空）
├─ domain/OperLogRecord  采集结果，字段与 sys_oper_log 一一对应
├─ store/OperLogStore    存储 SPI，由业务模块实现
└─ aspect/LogAspect      AOP 环绕通知：采集 → 脱敏 → 截断 → 交给 Store
```

## 用法

```java
@Log(title = "用户管理", businessType = BusinessType.INSERT)
@PostMapping
public R<Long> add(@RequestBody SysUser user) { ... }

// 涉及密码 / Token 的接口：不记录请求体
@Log(title = "重置用户密码", businessType = BusinessType.UPDATE, saveRequestData = false)
```

## 当前实现（v1.0）

```
@Log 方法 → LogAspect
  ├─ 记录耗时、请求方法、URI、客户端 IP、操作人、成功/失败
  ├─ 参数序列化：跳过 MultipartFile / ServletRequest / BindingResult / byte[]
  ├─ 脱敏：字段名含 password / passwd / secret / token 的值替换为 ***
  ├─ 截断：参数与结果各 2000 字符，方法 255、URL 500
  └─ OperLogStore.save(record)  ← lx-module-system 的 SysOperLogStore 写 sys_oper_log
```

**同步写、失败只 warn**：日志落库放在业务方法返回之后，异常被切面吞掉。
选择同步而不是事件异步，是因为审计要求“接口返回时日志已经可查”，
异步队列在进程崩溃时可能丢最后几条；代价是每条写接口多一次 insert，
日志表已按时间建索引，v1.0 的量级可以接受。将来要异步，只改 `SysOperLogStore` 的实现。

**为什么密码不会进日志**：`SysUser.password` 上有 `@JsonProperty(access = WRITE_ONLY)`，
序列化时根本不会出现；正则脱敏是第二道防线，给以后的 DTO 兜底。
重置密码接口另外设置了 `saveRequestData = false`，请求体整段不记录。

## 没有实现时

`ObjectProvider<OperLogStore>` 取不到实现（例如未来把系统模块从部署里裁掉）时，
切面只打一行应用日志：

```
[操作日志] title=文件上传, type=INSERT, user=admin, ip=127.0.0.1,
           method=FileController.upload(..), cost=30ms, status=OK
```

## 只记录写操作

查询接口不记，否则日志表会迅速膨胀。日志保留天数由参数 `sys.log.keepDays` 控制，
由 `lx-module-system` 的 `LogRetentionTask` 每天凌晨清理。

## 禁止事项

- ❌ 给涉及密码 / 密钥的接口保留 `saveRequestData=true`（会把明文密码写进日志）。
- ❌ 在 `OperLogStore` 实现里向调用方抛异常（日志失败不能影响业务）。
- ❌ 用 `@Log` 记录高频查询接口。
- ❌ 把 `OperLogRecord` 换成业务实体（会让公共模块依赖系统模块）。
