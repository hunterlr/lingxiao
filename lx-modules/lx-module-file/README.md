# lx-module-file · 文件中心模块

## 这个目录是做什么的

统一文件存储与文件库：上传、秒传、下载、列表搜索、改名留痕。

**v1.0 已实装**：上传校验、SHA-256 秒传、本地存储、分页搜索、改名与改名历史、公开访问、
图片宽高识别、EXIF 剥离 / 方向纠正、缩略图与中图。
**后续版本**：在线裁剪、水印、引用计数回收、MinIO / OSS 存储实现。

## 包结构

```
com.lingxiao.modules.file
├─ controller/  FileController（后台）、PublicFileController（公开访问）
├─ service/     FileService
├─ storage/     StorageService（抽象）、LocalStorageService（本地实现）
├─ image/       ImageProcessor（宽高 / EXIF / 缩略图 / 中图）、ProcessedImage
├─ entity/      SysFile、SysFileNameLog
├─ mapper/      SysFileMapper、SysFileNameLogMapper
├─ dto/         FileVO、RenameRequest
├─ config/      FileProperties（lx.file.*）
└─ resources/db/migration/file/  V3 建表、V13 中图路径
```

## 三个名字分离（本模块最核心的设计）

| 字段 | 可变 | 回答什么问题 |
|---|---|---|
| `original_name` | ❌ 不可变 | 这个文件**原来**叫什么 |
| `display_name` | ✅ 可变 | 文件库里**现在**叫什么 |
| `file_name` / `path` | ❌ 不可变 | 磁盘上叫什么、在哪 |

改名只改 `display_name`，同时写 `sys_file_name_log`（旧名 / 新名 / 操作人 / 时间）。
**改名不影响 URL、不影响引用**，因为 URL 用 `fileId`。

## 存储抽象

```java
public interface StorageService {
    String store(InputStream in, String relativePath);
    Resource load(String relativePath);
    boolean delete(String relativePath);
    String type();
}
```

v1.0 只有 `LocalStorageService`；MinIO / OSS / COS / S3 后续实现同一接口，业务代码不改。

### 路径安全

- 文件名用 UUID，**不用用户原始文件名**（防路径穿越、防中文乱码）；
- 所有写入 `normalize()` 后校验仍在根目录内；
- `bizType` 只保留字母数字下划线短横线，防止拼进路径后穿越。

## 上传流程

```
校验非空 / 大小 → 扩展名白名单 → SHA-256
  → 命中已有 hash：直接返回已有 fileId（秒传，同一份文件只存一份）
  → 生成 UUID，路径 {bizType}/yyyy/MM/uuid.ext
  → 图片：读宽高 → 重新编码（剥离 EXIF / 按 Orientation 转正）
          → 生成 {uuid}_thumb.ext / {uuid}_medium.ext
  → 存储主图与变体 → 写 sys_file
```

SVG **默认不放行**（XSS 风险）；需要时在 `lx.file.allowed-extensions` 显式加。

## 图片管线

| 能力 | 实现 |
|---|---|
| 读宽高 | `ImageReader` 只读文件头，不整图解码 |
| 剥离 EXIF | JPEG / PNG 经 Thumbnailator 重新编码，GPS / 设备 / 拍摄时间不随文件泄露 |
| 方向纠正 | 自己解析 JPEG APP1 的 Orientation，支持 3 / 6 / 8（180° / 90° / 270°） |
| 缩略图 | 最长边 `thumb-size`（默认 160px），文件网格用 |
| 中图 | 最长边 `medium-size`（默认 800px），详情预览 / 富文本用 |
| 安全阀 | 像素数超过 `max-pixels`（默认 2000 万）只入库宽高，不解码，防压缩炸弹 |

访问方式：

```
/api/file/{fileId}                原图
/api/file/{fileId}?size=thumb     缩略图，变体不存在时回退原图
/api/file/{fileId}?size=medium    中图，变体不存在时回退原图
```

**格式边界**：ImageIO 在 JDK 8 能稳定解码的 `jpg / jpeg / png / gif / bmp`；
`webp` 保持原样入库（不生成变体）。GIF 不重编码，避免破坏动图。
任何一步失败都回退原图并记日志，**不影响上传成功**。

衍生图只写磁盘，不写 `sys_file`：它们不是独立业务文件，不参与秒传、改名与引用计数。

## 后台列表筛选（素材选择器）

`GET /api/admin/file/list` 支持可选参数 `imageOnly=true`，只返回
`jpg / jpeg / png / gif / bmp / webp / svg`，供 CMS 素材选择器按图片分页。

- 只影响列表筛选，不改变上传白名单；能否上传仍由 `lx.file.allowed-extensions` 决定。
- 不传或传 `false` 时行为与 v1.0 一致，文件中心页面无需改动。
- 非图片文件仍可通过文件中心访问，只是不会出现在图片选择器里。

## 配置

```yaml
lx:
  file:
    storage: local
    local-path: ./data/upload
    max-size-mb: 10
    allowed-extensions: jpg,jpeg,png,gif,webp,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,txt
    # 图片变体最长边（px）
    thumb-size: 160
    medium-size: 800
    # 超过这个像素数只入库宽高，不生成变体
    max-pixels: 20000000
```

单文件上限的实际取值顺序：**参数设置 `sys.file.uploadMaxMb`**
→ YAML `lx.file.max-size-mb` → 容器 multipart 上限（当前 20MB）。
参数由 `lx-common-core` 的 `ConfigProvider` 只读 SPI 提供，系统模块实现；
文件模块用 `ObjectProvider` 注入，裁剪掉系统模块时自动回退 YAML，启动不报错。
数据库里的值只能把上限调小，不能突破容器限制。

## 数据边界

**只操作 `sys_file`、`sys_file_name_log`。** CMS / 会员只通过 `fileId` 引用，不建自己的文件表。

## 禁止事项

- ❌ 直接暴露磁盘真实路径（只能通过 `/api/file/{fileId}`）。
- ❌ 用原始文件名落盘。
- ❌ 改名时修改 `original_name` 或 `path`。
- ❌ 在 Mapper 里写 `.last("LIMIT 1")` 这类 MySQL 方言。
- ❌ 让 `ref_count > 0` 的文件被物理删除（W3 补回收逻辑）。

## 公开访问的安全边界

`/api/file/{fileId}` **不要求后台登录**，适用于公告配图、CMS 文章图。
`?size=thumb|medium` 也走同一套公开访问，返回的仍是该文件的衍生图。
私有附件（合同、证件）后续必须走签名 URL 或鉴权下载，**不要**直接放这个接口。
