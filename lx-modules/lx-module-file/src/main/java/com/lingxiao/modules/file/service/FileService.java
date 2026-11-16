package com.lingxiao.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.file.config.FileProperties;
import com.lingxiao.modules.file.dto.FileVO;
import com.lingxiao.modules.file.entity.SysFile;
import com.lingxiao.modules.file.entity.SysFileNameLog;
import com.lingxiao.modules.file.image.ImageProcessor;
import com.lingxiao.modules.file.image.ProcessedImage;
import com.lingxiao.modules.file.mapper.SysFileMapper;
import com.lingxiao.modules.file.mapper.SysFileNameLogMapper;
import com.lingxiao.modules.file.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件服务。
 *
 * <p>v1.0 已实现：上传校验、SHA-256 秒传、本地存储、文件库列表、改名留痕、
 * 图片宽高识别、EXIF 剥离 / 方向纠正、缩略图与中图生成。</p>
 *
 * <p>图片处理是“尽力而为”：任何一步失败都回退原图并记日志，绝不让上传整体失败。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 参数设置里的单文件上限（MB）；与 YAML 的 lx.file.max-size-mb 互为兜底。 */
    private static final String CONFIG_UPLOAD_MAX_MB = "sys.file.uploadMaxMb";

    /**
     * 容器 multipart 上限（application.yml：spring.servlet.multipart.max-request-size）。
     *
     * <p>请求进入 Controller 之前就被 Tomcat 限制，参数设置只能在这个范围内调小；
     * 想放开到更大需要先改 YAML 并重启，不能靠改数据库突破。</p>
     */
    private static final long CONTAINER_MAX_MB = 20;

    private final SysFileMapper fileMapper;
    private final SysFileNameLogMapper nameLogMapper;
    private final StorageService storageService;
    private final FileProperties properties;
    private final ImageProcessor imageProcessor;

    /**
     * 参数读取器由系统模块提供；系统模块不存在时（裁剪部署）为 null，
     * 此时回退 YAML {@code lx.file.max-size-mb}，不影响启动。
     */
    private final ObjectProvider<ConfigProvider> configProvider;

    /**
     * 上传文件。
     *
     * @param file    multipart 文件
     * @param bizType 业务场景，例如 notice / avatar / common
     * @param remark  人可读描述，可为空
     */
    @Transactional(rollbackFor = Exception.class)
    public FileVO upload(MultipartFile file, String bizType, String remark) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ErrorCode.FILE_UPLOAD_FAILED, "上传文件为空");
        }
        long maxSizeMb = resolveMaxSizeMb();
        if (file.getSize() > maxSizeMb * 1024 * 1024) {
            throw new ServiceException(ErrorCode.FILE_TOO_LARGE,
                    "文件超过 " + maxSizeMb + "MB 限制");
        }

        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "unnamed";
        String ext = resolveExtension(originalName);
        if (!isAllowed(ext)) {
            throw new ServiceException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不允许的文件类型: " + ext);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.FILE_UPLOAD_FAILED, "读取上传文件失败");
        }

        String hash = sha256(bytes);
        SysFile existing = findByHash(hash);
        if (existing != null) {
            // 秒传：同一份文件只存一份，返回已有 fileId
            log.info("命中秒传: hash={}, fileId={}", hash, existing.getFileId());
            return toVO(existing);
        }

        String safeBizType = sanitizeBizType(bizType);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + "." + ext;
        String relativePath = safeBizType + "/" + LocalDate.now().format(MONTH_FORMAT) + "/" + fileName;

        // 图片：重新编码剥离 EXIF / 纠正方向，并生成 thumb / medium；非图片或失败时为 null
        ProcessedImage processed = imageProcessor.process(bytes, ext);
        byte[] mainBytes = processed != null ? processed.getMainBytes() : bytes;

        try (ByteArrayInputStream in = new ByteArrayInputStream(mainBytes)) {
            storageService.store(in, relativePath);
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.FILE_UPLOAD_FAILED, "保存文件失败");
        }

        String thumbPath = storeVariant(relativePath, "thumb",
                processed == null ? null : processed.getThumbBytes(),
                processed == null ? null : processed.getThumbFormat());
        String mediumPath = storeVariant(relativePath, "medium",
                processed == null ? null : processed.getMediumBytes(),
                processed == null ? null : processed.getMediumFormat());

        SysFile entity = new SysFile();
        entity.setFileId(IdWorker.getId());
        entity.setStorage(storageService.type());
        entity.setPath(relativePath);
        entity.setUrl("/api/file/" + entity.getFileId());
        entity.setOriginalName(originalName);
        entity.setDisplayName(originalName);
        entity.setFileName(fileName);
        entity.setExt(ext);
        entity.setMime(file.getContentType());
        entity.setSize((long) mainBytes.length);
        entity.setWidth(processed == null ? null : processed.getWidth());
        entity.setHeight(processed == null ? null : processed.getHeight());
        entity.setThumbPath(thumbPath);
        entity.setMediumPath(mediumPath);
        entity.setHash(hash);
        entity.setBizType(safeBizType);
        entity.setRemark(remark);
        entity.setRefCount(0);
        entity.setStatus("0");
        entity.setUploaderId(LoginHelper.getUserId());
        entity.setCreateBy(LoginHelper.getUserId());
        fileMapper.insert(entity);
        return toVO(entity);
    }

    public Resource load(Long fileId) {
        SysFile file = getEntity(fileId);
        return storageService.load(file.getPath());
    }

    /**
     * 解析实际要读取的相对路径。
     *
     * <p>{@code size=thumb|medium} 且变体存在时返回变体；参数为空、取值未知或变体缺失时
     * 一律回退原图。这样前端可以无条件请求 {@code ?size=thumb}，老文件也不会 404。</p>
     */
    public String resolvePath(SysFile file, String size) {
        if ("thumb".equalsIgnoreCase(size) && StringUtils.hasText(file.getThumbPath())) {
            return file.getThumbPath();
        }
        if ("medium".equalsIgnoreCase(size) && StringUtils.hasText(file.getMediumPath())) {
            return file.getMediumPath();
        }
        return file.getPath();
    }

    public Resource loadPath(String relativePath) {
        return storageService.load(relativePath);
    }

    public SysFile getEntity(Long fileId) {
        SysFile file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new ServiceException(ErrorCode.FILE_NOT_FOUND);
        }
        return file;
    }

    public FileVO detail(Long fileId) {
        return toVO(getEntity(fileId));
    }

    /**
     * 文件库分页。
     *
     * @param keyword 匹配 display_name / original_name / remark
     */
    public PageResult<FileVO> list(long pageNum, long pageSize, String keyword, String bizType) {
        Page<SysFile> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .eq(StringUtils.hasText(bizType), SysFile::getBizType, bizType)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysFile::getDisplayName, keyword)
                        .or().like(SysFile::getOriginalName, keyword)
                        .or().like(SysFile::getRemark, keyword))
                .orderByDesc(SysFile::getCreateTime);

        Page<SysFile> result = fileMapper.selectPage(page, wrapper);
        PageResult<FileVO> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords().stream().map(this::toVO).collect(java.util.stream.Collectors.toList()));
        return pageResult;
    }

    /**
     * 改名：只改 display_name，并写一条改名历史。
     */
    @Transactional(rollbackFor = Exception.class)
    public FileVO rename(Long fileId, String newName) {
        SysFile file = getEntity(fileId);
        String oldName = StringUtils.hasText(file.getDisplayName())
                ? file.getDisplayName() : file.getOriginalName();
        String trimmed = newName.trim();
        if (trimmed.equals(oldName)) {
            return toVO(file);
        }

        file.setDisplayName(trimmed);
        fileMapper.updateById(file);

        SysFileNameLog nameLog = new SysFileNameLog();
        nameLog.setFileId(fileId);
        nameLog.setOldName(oldName);
        nameLog.setNewName(trimmed);
        nameLog.setOperatorId(LoginHelper.getUserId());
        nameLog.setOperatorName(LoginHelper.getUsername());
        nameLog.setCreateTime(LocalDateTime.now());
        nameLogMapper.insert(nameLog);

        return toVO(file);
    }

    public List<SysFileNameLog> nameHistory(Long fileId) {
        return nameLogMapper.selectList(new LambdaQueryWrapper<SysFileNameLog>()
                .eq(SysFileNameLog::getFileId, fileId)
                .orderByDesc(SysFileNameLog::getCreateTime));
    }

    /**
     * 解析当前生效的单文件上限。
     *
     * <p>优先级：参数设置 {@code sys.file.uploadMaxMb} &gt; YAML
     * {@code lx.file.max-size-mb}；最终再被容器 multipart 上限截断。
     * 参数值非法（负数 / 0 / 非整数）时回退 YAML，不因为一条脏配置就让上传全挂。</p>
     */
    private long resolveMaxSizeMb() {
        ConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            return properties.getMaxSizeMb();
        }
        int configured = provider.getIntValue(CONFIG_UPLOAD_MAX_MB, -1);
        if (configured <= 0) {
            return properties.getMaxSizeMb();
        }
        if (configured > CONTAINER_MAX_MB) {
            log.warn("参数 {}={}MB 超过容器 multipart 上限 {}MB，按容器上限生效",
                    CONFIG_UPLOAD_MAX_MB, configured, CONTAINER_MAX_MB);
            return CONTAINER_MAX_MB;
        }
        return configured;
    }

    private SysFile findByHash(String hash) {
        List<SysFile> list = fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getHash, hash));
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean isAllowed(String ext) {
        if (!StringUtils.hasText(ext)) {
            return false;
        }
        for (String allowed : properties.getAllowedExtensions().split(",")) {
            if (allowed.trim().equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }

    private String resolveExtension(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 业务场景只允许字母、数字、下划线与短横线，防止拼进路径后穿越。
     */
    private String sanitizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return "common";
        }
        String cleaned = bizType.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isEmpty() ? "common" : cleaned;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 保存图片衍生文件；失败只记日志并返回 null，不影响主文件与数据库记录。
     */
    private String storeVariant(String relativePath, String suffix, byte[] content, String format) {
        if (content == null || content.length == 0 || !StringUtils.hasText(format)) {
            return null;
        }
        String path = variantPath(relativePath, suffix, format);
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            storageService.store(in, path);
            return path;
        } catch (Exception e) {
            log.warn("保存图片变体失败: path={}", path, e);
            return null;
        }
    }

    /**
     * 由主图路径派生变体路径：{@code notice/2026/11/uuid.jpg} →
     * {@code notice/2026/11/uuid_thumb.jpg}。
     */
    private String variantPath(String relativePath, String suffix, String format) {
        int slash = relativePath.lastIndexOf('/');
        int dot = relativePath.lastIndexOf('.');
        String base = dot > slash ? relativePath.substring(0, dot) : relativePath;
        return base + "_" + suffix + "." + format;
    }

    private String variantUrl(Long fileId, String size) {
        return "/api/file/" + fileId + "?size=" + size;
    }

    private FileVO toVO(SysFile file) {
        FileVO vo = new FileVO();
        vo.setFileId(file.getFileId());
        vo.setOriginalName(file.getOriginalName());
        vo.setDisplayName(StringUtils.hasText(file.getDisplayName())
                ? file.getDisplayName() : file.getOriginalName());
        vo.setUrl(file.getUrl());
        vo.setThumbUrl(StringUtils.hasText(file.getThumbPath())
                ? variantUrl(file.getFileId(), "thumb") : null);
        vo.setMediumUrl(StringUtils.hasText(file.getMediumPath())
                ? variantUrl(file.getFileId(), "medium") : null);
        vo.setExt(file.getExt());
        vo.setMime(file.getMime());
        vo.setSize(file.getSize());
        vo.setWidth(file.getWidth());
        vo.setHeight(file.getHeight());
        vo.setBizType(file.getBizType());
        vo.setRemark(file.getRemark());
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }
}
