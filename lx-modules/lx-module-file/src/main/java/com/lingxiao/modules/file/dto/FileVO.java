package com.lingxiao.modules.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件展示对象。
 *
 * <p>不直接返回 {@code SysFile} 实体：实体里有 storage / path / hash 等内部字段，
 * 前端只需要 fileId、URL、名字与尺寸。</p>
 */
@Data
public class FileVO {

    private Long fileId;
    private String originalName;
    private String displayName;
    private String url;
    private String thumbUrl;
    private String mediumUrl;
    private String ext;
    private String mime;
    private Long size;
    private Integer width;
    private Integer height;
    private String bizType;
    private String remark;
    private LocalDateTime createTime;
}
