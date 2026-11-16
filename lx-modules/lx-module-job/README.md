# lx-module-job · 定时任务模块

## 这个目录是做什么的

后台定时任务：建任务、启停、立即执行、看执行日志，多实例部署时不重复执行。

**v1.0 已实装**：任务 CRUD、cron 校验、执行器注册表、集群锁、执行日志（开始 / 结束 / 耗时 / 触发方式 / 异常堆栈）、
任务日志保留清理、模块开关、按钮级权限。

**不做**：Quartz 的持久化触发器、错失补偿、任务依赖、分片、传参。

## 为什么是 Spring Task + 数据库锁

| 方案 | 表 | 多库兼容 | v1.0 需求覆盖 |
|---|---|---|---|
| Quartz 集群 | 11~13 张 `QRTZ_*` | 达梦 / 金仓无官方方言 | 全 |
| **Spring Task + 数据库锁** | 1 张锁表 + 复用 `sys_job` | 一条标准 `UPDATE`，无方言 | 启停 / 立即执行 / 日志 / 集群不重复 |

v1.0 只需要“到点执行、别重复、能看结果”，选后者：
少 12 张表，少一套调度元数据，升级数据库时不用额外适配。

## 包结构

```
com.lingxiao.modules.job
├─ controller/  SysJobController、SysJobLogController
├─ service/
│  ├─ SysJobService        任务 CRUD / cron 校验 / 启停 / 手动执行
│  ├─ SysJobLogService     日志分页 / 清空
│  ├─ JobScheduler         动态注册 CronTrigger、计算下次执行时间
│  ├─ JobExecutor          抢锁 → 调执行器 → 写日志 → 释放锁
│  ├─ JobLockService       sys_job_lock 抢锁 / 释放 / 清理
│  └─ JobHandlerRegistry   收集容器里的 JobHandler
├─ handler/
│  ├─ JobLogCleanHandler   清理任务日志（读 sys.job.logKeepDays）
│  ├─ DbHealthCheckHandler 数据库连通性检查
│  └─ PathCheckHandler     上传目录可写性检查
├─ entity/      SysJob、SysJobLog
├─ mapper/      SysJobMapper、SysJobLogMapper、SysJobLockMapper
├─ dto/         JobHandlerVO、JobLastRunVO
└─ resources/db/migration/job/  V11 迁移
```

执行器 SPI 在 `lx-common-core`：

```java
public interface JobHandler {
    String name();          // 写进 sys_job.invoke_target
    String description();   // 任务表单下拉里显示
    String execute(String params) throws Exception; // 返回值写 job_message
}
```

**为什么 SPI 放 common-core**：系统模块要贡献 `systemLogClean`，但不能反向依赖 job 模块。
依赖方向保持 `modules → common / framework`，job 模块只负责发现和调度。

## 新增一个执行器

```java
@Component
public class MyTaskHandler implements JobHandler {
    @Override public String name() { return "myTask"; }
    @Override public String description() { return "我的任务"; }
    @Override public String execute(String params) {
        // 业务逻辑；抛异常即记为失败
        return "处理 10 条";
    }
}
```

任务表单的“执行器”下拉会自动出现它；保存时后端会校验名称存在。

## 执行链路

```
CronTrigger 到点
  → JobExecutor
      1. 读任务快照（可能刚被暂停 / 删除）
      2. concurrent=1：本机 AtomicBoolean 挡住重叠执行
      3. JobLockService.tryLock(job_id)
           UPDATE sys_job_lock
           SET lock_owner=?, lock_time=now
           WHERE job_id=? AND (lock_owner IS NULL OR lock_time < now - timeout)
         抢不到 → 本次跳过（另一个节点在跑）
      4. JobHandler.execute(null)
      5. 成功 / 失败都写 sys_job_log
      6. 释放锁（带 owner 条件，防止释放别人接管的锁）
```

### 为什么锁表只有一行一任务

- 抢锁是单条 `UPDATE ... WHERE`，数据库保证同一行只有一个事务成功；
- 不需要 `SELECT ... FOR UPDATE`，也不依赖 Redis；
- 节点宕机后，`lock_time` 超过 `lx.job.lock-timeout-seconds`（默认 600 秒）自动可被接管；
- 释放锁必须带 `lock_owner`：长任务超时被接管后，旧节点不能把新节点的锁清掉。

> 长任务的执行时间必须小于锁超时，否则会出现“两个节点同时跑”。
> 真遇到长任务，调大 `lx.job.lock-timeout-seconds`，或把任务拆小。

## 数据模型

`sys_job`（V1 建表，复用 RuoYi 风格字段）：

| 字段 | 说明 |
|---|---|
| `invoke_target` | 执行器名称，**不是** `类.方法`；不接受任意反射调用 |
| `cron_expression` | Spring 6 段：秒 分 时 日 月 周 |
| `status` | 0 正常 / 1 暂停 |
| `concurrent` | 0 允许 / 1 禁止（同节点不重叠） |
| `misfire_policy` | 1 / 2 / 3，v1.0 统一按“放弃”处理，字段保留兼容旧数据 |

`sys_job_log`（V11 补字段）：`job_id / trigger_type / job_message / status / exception_info /
start_time / end_time / duration_ms / create_time`。

`sys_job_lock`：`job_id` 主键 + `lock_owner` + `lock_time`。

`job_name / job_group / invoke_target` 在日志里是**执行时快照**：任务后来改名、改执行器甚至删除，
历史日志仍能读懂当时跑了什么。

## 任务日志保留

- 参数设置 `sys.job.logKeepDays`（V11 种入，默认 30 天，控件 number，范围 1~3650）；
- 执行器 `jobLogClean` 读取该参数并按 `create_time` 删除；
- 参数非法 / 小于 1 时**让任务失败**，页面上能看到红色日志，而不是悄悄不清理；
- 删除任务不删历史日志，统一由 `jobLogClean` 清理。

## 模块开关

```yaml
lx:
  module:
    modules:
      job:
        enabled: true
```

- `true`：Controller / Service / Scheduler / Handler 全部装配；
- `false`：上述 Bean 不装配，`/api/admin/monitor/job/**` 返回 404，菜单落到前端占位页；
- Mapper 仍会被 `@MapperScan` 注册，但没有任何 Bean 调用它，不影响启动。

已验证：`.local/verify-job-module.ps1`（5 项，8081 实例以关闭态启动）。

## 运行参数

```yaml
lx:
  job:
    pool-size: 4                  # 调度线程池
    lock-timeout-seconds: 600     # 集群锁超时
    shutdown-wait-seconds: 10     # 关闭时等待在跑任务
```

这些是基础设施参数，放 YAML 不放 `sys_config`：改线程池 / 锁超时需要重启，做成热开关反而危险。

## 与系统模块的关系

- `systemLogClean` 执行器由 `lx-module-system` 提供，调用 `LogRetentionService.cleanNow()`；
- job 模块开启时，系统模块自带的 `LogRetentionTask`（`@Scheduled`）**不注册**，避免同一份清理跑两遍；
- job 模块关闭时，`LogRetentionTask` 按 `lx.log.cleanCron` 兜底，审计表仍然不会无限增长。

## 验证

```powershell
# 后端 8080 + 前端 5173 已启动
powershell -ExecutionPolicy Bypass -File .local\verify-job.ps1         # 51 项
powershell -ExecutionPolicy Bypass -File .local\verify-job-module.ps1  # 5 项，需 8081 关闭态实例
node .local\uitest\job.mjs                                             # 9 项
```

覆盖：V11 迁移、执行器注册、cron 校验、调度执行、暂停、手动执行、集群锁占用 / 过期接管、
失败日志与堆栈、删除保留历史、权限拆分（list-only 403）、匿名 401、日志清空、模块开关 404。
