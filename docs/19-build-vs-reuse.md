# 19 · 复用优先与自研边界（Build vs Reuse）

> 结论：**80% 用成熟轮子，20% 写自己的业务与胶水。**
> 自研不是目的，交付才是。重复造轮子 = 多写代码 + 多写测试 + 多修 bug + 多花 AI token，且很难比社区做得更好。
>
> **v1.0 的落地口径是「借代码，不借架构」**（见 [18-day1-checklist.md](18-day1-checklist.md)）：自己建 Maven 多模块骨架与三套安全域，从成熟项目里逐个搬工具层代码与实现思路；不 fork 整份后台直接改，也不从零手写登录、权限、分页、上传、Excel。

---

## 1. 自研判定：先问 5 个问题

| # | 问题 | 如果“是” | 如果“否” |
|---|---|---|---|
| 1 | 社区有成熟、可商用、维护活跃的实现吗？ | 直接复用 | 进入下一问 |
| 2 | 它能通过 SPI / 配置 / 扩展点接入吗？ | 复用 + 适配层 | 进入下一问 |
| 3 | 自研能形成业务壁垒或明显体验差异吗？ | 自研 | 复用 |
| 4 | 自研成本 < 集成成本的 1.5 倍吗？ | 自研 | 复用 |
| 5 | 出问题后有人能长期维护吗？ | 自研 | 复用 |

**默认答案：复用。** 只有 3、4、5 同时成立才自研。

**但“复用”不等于“fork 整份项目”。** 架构、权限模型、包结构必须自己定；只搬与架构无关的工具层与实现思路，避免被别人的结构锁死。

---

## 2. 直接复用清单（不要自己写）

### 2.1 后端基座

| 能力 | 复用组件 | 许可证 | 说明 |
|---|---|---|---|
| 后台模型参考 | RuoYi-Vue（只读参考，不 fork） | MIT | 用户、角色、菜单、部门、字典、参数、日志、代码生成的实现思路 |
| 认证鉴权 | Sa-Token | Apache-2.0 | 三套 `StpLogic`、权限注解、Token 风格 |
| ORM | MyBatis-Plus | Apache-2.0 | 分页、逻辑删除、条件构造；租户插件 v1.0 不接 |
| 数据库迁移 | Flyway | Apache-2.0 | 按模块分目录、版本管理 |
| 工作流引擎 | Flowable 6.8（P4 再引入） | Apache-2.0 | 流程、任务、历史 |
| 接口文档 | SpringDoc + Knife4j | Apache-2.0 | OpenAPI 3、调试页 |
| Excel | EasyExcel | Apache-2.0 | 导入导出、大文件 |
| 缓存 / 锁 | Redis + Redisson | Apache-2.0 | 分布式锁、限流 |
| 连接池 | Druid / HikariCP | Apache-2.0 | 监控、SQL 统计 |
| 工具库 | Hutool（克制） | MPL-2.0 | 只做工具补充，不滥用 |
| 对象存储 SDK | AWS S3 SDK / MinIO SDK | Apache-2.0 | S3 协议通用 |
| 图片处理 | Thumbnailator / libvips | Apache-2.0 / LGPL | 缩放、裁剪、水印 |
| 调度 | Spring Task + 数据库锁 / Quartz / PowerJob | Apache-2.0 | v1.0 只需集群去重，不引 Quartz 的 12 张表；**不要用 XXL-Job（GPL-3.0）** |
| 搜索 | OpenSearch / Meilisearch | Apache-2.0 / MIT | **慎用 Elasticsearch（Elastic License / SSPL）** |
| 监控 | Actuator + Prometheus | Apache-2.0 | 指标、健康检查 |
| 链路 | SkyWalking（可选） | Apache-2.0 | P6 后 |

### 2.2 前端

| 能力 | 复用组件 | 许可证 | 说明 |
|---|---|---|---|
| 后台框架 | Vue3 + Vite + Element Plus | MIT | 自己起工程，不搬 Vue2 模板 |
| 布局 / 路由思路 | vue-vben-admin / vue-element-plus-admin（参考） | MIT | 动态路由、按钮权限、CRUD 页思路 |
| 门户 | Nuxt3 | MIT | SSR / SSG、SEO |
| H5 / 小程序 | uni-app | Apache-2.0 | 一套代码多端 |
| 状态 / 工具 | Pinia + VueUse | MIT | 状态、组合式工具 |
| 图表 | ECharts | Apache-2.0 | 统计、看板 |
| 富文本 | WangEditor / Tiptap | MIT | 编辑器内核 |
| 流程设计器 | bpmn-js（P4） | MIT | BPMN 建模，不自己画 |
| 国际化 | vue-i18n / @nuxtjs/i18n | MIT | 语言包、切换 |
| 请求 | Axios / ofetch | MIT | 拦截、重试 |

### 2.3 工程与运维

| 能力 | 复用组件 | 许可证 | 说明 |
|---|---|---|---|
| 测试 | JUnit5 + Testcontainers | Apache-2.0 / MIT | 单测、集成测试 |
| E2E | Playwright | Apache-2.0 | 关键链路 |
| CI | GitLab CI / Jenkins | MIT | 构建、测试、镜像 |
| 部署 | Nginx + systemd | BSD-2 / LGPL | jar 直部署 |
| 容器 | Docker / Compose（后期） | Apache-2.0 | 后期 |
| 日志 | Logback + Loki / ELK | EPL / AGPL / Elastic | Loki AGPL，分发需注意 |
| 告警 | Alertmanager | Apache-2.0 | 企业微信 / 钉钉 |
| 对象存储服务 | MinIO / SeaweedFS | **AGPL-3.0** / Apache-2.0 | MinIO 作为独立服务通常可控；对外分发需合规，可换 SeaweedFS 或云 OSS |

---

## 3. 必须自研清单（业务壁垒 / 胶水）

| 能力 | 为什么自研 | 复用程度 |
|---|---|---|
| 三套安全域 | 平台后台 / 租户应用 / 会员前台，表、Token、权限码、缓存命名空间全隔离 | Sa-Token 提供能力，模型自己定 |
| 模块开关 | 运行期开关 + 条件装配；关闭后 Bean / 接口 / 迁移 / 菜单都不加载 | 用 Spring 条件装配 |
| CMS 领域模型 | 站点 / 栏目 / 模型 / 内容 / 版本 / 权限 | 编辑器、搜索、静态化复用 |
| 内容权限与会员权益 | 免费 / 登录 / VIP / 等级 / 付费 | 规则自己写 |
| 工作流业务集成（P4） | 审批回写、表单绑定、CMS 审核 | Flowable 引擎复用 |
| 图片引用 / 裁剪 / 水印编排 | `fileId` 引用、衍生文件、引用计数 | 编解码复用 Thumbnailator / libvips |
| 多库方言胶水（后置） | `DatabaseDialect` + 统一 schema 生成 | MP `DbType` + Flyway 复用 |
| i18n 语言包发布 / 热加载 | 版本、审核、回滚、Redis 广播 | MessageSource / vue-i18n 复用 |
| 代码生成模板 | 生成物要符合本项目规范 | RuoYi gen 内核复用，模板自己写 |
| 租户应用隔离（SaaS 立项时） | 独立入口 / 验证 / RBAC，应用内 `tenant_id` | 数据源路由自己写 |
| 开放平台签名 / 计量 | AppKey、Scope、限流、计费 | 网关 / 限流组件复用 |

**一句话：引擎用别人的，规则用自己的；架构自己定，工具层可以搬。**

---

## 4. 许可证红线（商用 / 私有化必须看）

| 许可证 | 义务 | 能否闭源商用 | 典型组件 |
|---|---|---|---|
| MIT | 保留版权与许可声明 | ✅ | Vue、Nuxt、Element Plus、RuoYi、bpmn-js、WangEditor |
| Apache-2.0 | 保留声明、专利授权、修改说明 | ✅ | Sa-Token、MyBatis-Plus、Flowable、Flyway、Knife4j、EasyExcel |
| MPL-2.0 | 修改的文件需开源，可与其他代码组合 | ✅（注意文件级） | Hutool |
| LGPL | 动态链接可闭源，修改库本身需开源 | ✅（遵守条件） | libvips |
| GPL-3.0 | 衍生作品需开源 | ⚠️ 谨慎 | XXL-Job、Halo |
| AGPL-3.0 | 网络服务也触发开源义务 | ⚠️ 高风险 | MinIO、Grafana、Loki |
| Elastic License / SSPL | 限制托管服务 | ⚠️ 谨慎 | Elasticsearch |

**规避建议：**

- 调度：XXL-Job（GPL）→ **Quartz / PowerJob（Apache-2.0）**。
- CMS 内核：Halo（GPL）→ **自研薄 CMS**，只复用编辑器与搜索。
- 搜索：Elasticsearch（Elastic/SSPL）→ **OpenSearch（Apache-2.0）/ Meilisearch（MIT）**。
- 对象存储：MinIO（AGPL）→ 作为独立服务使用通常可控；**对外分发产品时**评估 SeaweedFS（Apache-2.0）或云 OSS。
- 监控：Grafana / Loki（AGPL）→ 作为独立运维组件使用；不分发、不修改即可降低风险。
- 所有组件建 **SBOM + License 台账**，CI 做许可证扫描。

---

## 5. 复用如何省 AI Token

| 做法 | 效果 |
|---|---|
| 自己建骨架 + 搬工具层代码 | 不重复生成统一响应、异常、XSS、幂等、分页、字典、日志切面 |
| 前端参考成熟模板的结构，另起 Vue3 工程 | 不重复造布局、动态路由、按钮权限 |
| 代码生成器 + 模板 | 一个表生成 Controller / Service / Mapper / Vue 页，AI 只改差异 |
| 组件目录 / 示例库 | AI 直接引用现成模式，不反复探索 |
| `AGENTS.md` / `.ai/` 规范 | 一次写清约束，减少来回纠正 |
| 按需读文件、检索符号 | 不把整个框架塞进上下文 |
| 生成后立即编译 / 测试 | 早发现错误，避免返工烧 token |
| 复用成熟库 | 少写代码 = 少 token + 少 bug + 少维护 |

**反模式：**

- 让 AI 从零写权限、菜单、分页、上传、Excel、工作流引擎。
- 把整份框架源码贴进对话再问“怎么改”。
- fork 一份 Vue2 后台直接改，再花更多 token 拆它的结构。
- 同一套 CRUD 每个模块重新生成一遍。
- 不用代码生成器，手写 + AI 补全。

---

## 6. 落地顺序

| 阶段 | 复用动作 |
|---|---|
| P0 / Day-1 | 定选型、跑 License 扫描、建 SBOM；按 `18` 建骨架（`system` / `file` / `lx-demo`）；只读参考 RuoYi 工具层 |
| P1（v1.0，6 周） | 搬统一响应 / 全局异常 / XSS / 幂等 / 分页 / 字典 / 日志切面 / 代码生成模板；前端 Vue3 + Element Plus；图片复用 Thumbnailator + vue-cropper；富文本复用 WangEditor 5。**v1.0 只做基座，不建 CMS / 门户 / 会员。** |
| P2（v1.1，CMS 模块） | 复用 WangEditor 5、图片管线、字典与文件选择器；CMS 只写领域模型、栏目闭包、发布 / 下线链路与 portal API |
| P3（v1.2，会员模块） | 复用 uni-app、vue-i18n、Sa-Token 第三套 `StpLogic`；会员只写注册登录规则、资料与互动 |
| P4（v1.3，门户） | 复用 Nuxt3 SSR、图片组件；只写页面、SEO 与缓存失效 |
| P5（v1.4+，工作流） | 复用 Flowable + bpmn-js；只写业务表单与回写 |
| P6 | 复用 Thumbnailator / libvips / imgproxy 的异步与多规格；只写引用与编排 |
| P7 | 租户应用复用数据源路由组件；只写开通与配额 |
| P8 | 复用开放平台网关 / 限流；只写 Scope 与计量 |

> 与 `17` / `20` 对齐：**P1 = v1.0 基座；P2 起才是 CMS / 会员 / 门户模块嵌入**，不是 v1.0 范围。

---

## 7. 验收清单

- [ ] 每个自研模块都能回答“为什么不复用”。
- [ ] 没有重复实现登录、权限、分页、上传、Excel、工作流引擎、富文本内核。
- [ ] 没有 fork 整份 RuoYi / Vue2 后台作为基线；仓库里没有第三方包名与来源不明片段。
- [ ] 所有第三方组件有 License 记录与版本锁定。
- [ ] CI 有依赖漏洞 + 许可证扫描。
- [ ] 代码生成器覆盖 80% 的 CRUD 场景。
- [ ] AI 生成代码以“改模板 / 改生成物”为主，不是从零写。
- [ ] 关键组件有替换预案（搜索、存储、调度、工作流）。
