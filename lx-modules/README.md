# lx-modules · 业务模块聚合

## 这个目录是做什么的

所有**可插拔业务模块**的父目录。v1.0 建五个：

| 模块 | 是否必装 | 职责 |
|---|---|---|
| `lx-module-system` | ✅ 必装 | 认证（登录失败锁定 / 密码策略）、用户、角色、菜单、部门、岗位、字典、参数、公告、日志 |
| `lx-module-file` | ✅ 必装 | 存储抽象、上传下载、文件库、缩略图 / 中图、EXIF 剥离、改名留痕 |
| `lx-module-job` | ✅ 必装（可关） | 定时任务：Spring Task + 数据库锁、执行日志 |
| `lx-module-generator` | ✅ 开发期（可关） | 代码生成：导入表、字段配置、Velocity 预览、下载 ZIP、菜单 SQL |
| `lx-module-demo` | ❌ 仅验证 | 证明“模块关闭后接口 404” |

## 后续（不在 v1.0）

```
lx-module-cms      v1.1  栏目 / 模型 / 文章 / 标签
lx-module-member   v1.2  会员 / 积分 / 订单
lx-module-portal   v1.3  门户聚合
```

嵌入契约见 `docs/20-cms-embed-plan.md`。

## 模块之间怎么通信

1. **不直接依赖对方的 Mapper**；
2. 共享模型放 `lx-api`；
3. 跨模块异步场景用领域事件；
4. 文件只传 `fileId`，不复制文件表。

## 数据边界（重要）

| 模块 | 只允许操作的表 |
|---|---|
| system | `sys_*`（除 `sys_file*`） |
| file | `sys_file`、`sys_file_name_log` |
| job | `sys_job`、`sys_job_log`、`sys_job_lock` |
| generator | `gen_table`、`gen_table_column`（工具表，物理删除） |
| cms（后续） | `cms_*`，通过 `fileId` 引用文件 |
| member（后续） | `mem_*`，与 `sys_*` 物理隔离 |

## 禁止事项

- ❌ 模块之间互相 `import` 对方的 Service / Mapper。
- ❌ 在 system 里写文件存储逻辑。
- ❌ 在 file 里写用户 / 权限逻辑。
- ❌ 在 job 里写具体业务清理 / 校验逻辑（应实现 `JobHandler` 放在业务模块）。
- ❌ 让基座（framework / boot）反向依赖某个具体模块。
