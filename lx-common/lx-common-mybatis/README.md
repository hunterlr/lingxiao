# lx-common-mybatis · 数据访问公共层

## 这个目录是做什么的

MyBatis-Plus 的统一配置：分页、乐观锁、防全表更新、审计字段自动填充、所有实体的公共字段。

## 包结构

```
com.lingxiao.common.mybatis
├─ config/MybatisPlusConfig        三个拦截器
├─ handler/AuditMetaObjectHandler  自动填 create_time / update_time
└─ domain/BaseEntity               审计四字段 + del_flag + version
```

## 三个插件缺一不可

| 插件 | 解决什么问题 | 不加会怎样 |
|---|---|---|
| `PaginationInnerInterceptor` | 统一分页 | 容易把全表查出来内存分页 |
| `OptimisticLockerInnerInterceptor` | 并发编辑互相覆盖 | 两个人同时改，后保存的静默覆盖前一个 |
| `BlockAttackInnerInterceptor` | 没有 where 的 update/delete | 一次误操作清库 |

## BaseEntity 字段

| 字段 | 填充时机 | 说明 |
|---|---|---|
| `createBy` / `createTime` | INSERT | 创建人由业务显式设置，时间自动填 |
| `updateBy` / `updateTime` | INSERT + UPDATE | 同上 |
| `delFlag` | — | `@TableLogic`，0 正常 / 1 删除 |
| `version` | — | `@Version`，更新自动 +1 |

**主键不放在 BaseEntity**：每张表主键名不同（`user_id` / `role_id` / `file_id`），由实体自己声明。

## 为什么时间用应用时间

- 多库兼容（不依赖 MySQL `now()`）；
- 时区一致（JVM 时区统一）；
- 单元测试可预测。

## 禁止事项

- ❌ 写具体业务 Mapper（放各自模块）。
- ❌ 在 SQL 里写 MySQL 方言函数（`find_in_set`、`group_concat`、`LIMIT`），否则后续 PG / 达梦 / 金仓迁移要重写。
- ❌ 让本模块依赖 `lx-common-security`（会造成审计填充反向依赖登录态）。
- ❌ 在实体里用数据库自增主键（统一雪花 ID）。

## 多库适配注意

`MybatisPlusConfig` 目前固定 `DbType.MYSQL`。接 PostgreSQL / 达梦 / 金仓时，改为按数据源动态判断，**不要**在每个 Mapper 里写方言。
