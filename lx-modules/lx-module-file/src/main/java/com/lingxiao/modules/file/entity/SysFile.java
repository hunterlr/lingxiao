package com.lingxiao.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件主表（sys_file）。
 *
 * <p>三个名字必须分清：</p>
 * <ul>
 *   <li>{@code originalName}：上传时的原始文件名，<b>不可改</b>，用于追溯“原来叫什么”；</li>
 *   <li>{@code displayName}：文件库展示名，<b>可改</b>，为空时回退 originalName；</li>
 *   <li>{@code fileName} / {@code path}：存储名与路径，<b>不可改</b>，改名不影响 URL 与引用。</li>
 * </ul>
 *
 * <p>平台后台域，不带 tenant_id；租户应用立项时再按需加隔离列。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends BaseEntity {

    @TableId(value = "file_id", type = IdType.ASSIGN_ID)
    private Long fileId;

    private Long groupId;

    /** local / minio / oss / cos / s3 */
    private String storage;

    private String bucket;

    /** 相对路径，不含存储根目录 */
    private String path;

    /** 访问地址；v1.0 为 /api/file/{fileId} */
    private String url;

    private String originalName;
    private String displayName;
    private String fileName;
    private String ext;
    private String mime;
    private Long size;

    /** SHA-256，用于秒传 / 去重 */
    private String hash;

    private Integer width;
    private Integer height;
    private Integer duration;
    /** 缩略图（最长边约 160px）相对路径；空表示原图够小或未生成 */
    private String thumbPath;

    /** 中图（最长边约 800px）相对路径；空表示原图够小或未生成 */
    private String mediumPath;

    /** 业务场景：notice / avatar / cms_content（后续） */
    private String bizType;

    /** 人可读描述，解决“文件太多不知道是什么” */
    private String remark;

    private Integer refCount;

    /** 衍生来源：裁剪 / 水印 / 缩略图指向原图 */
    private Long derivedFrom;

    /** 0 正常 / 1 禁用 */
    private String status;

    private Long uploaderId;
}
