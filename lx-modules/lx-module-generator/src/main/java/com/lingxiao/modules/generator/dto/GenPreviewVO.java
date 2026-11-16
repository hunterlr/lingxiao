package com.lingxiao.modules.generator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 生成代码预览。
 *
 * <p>返回完整文件内容而不是片段：预览页要能复制、要能直接比对，
 * 截断会让“下载的和我看到的不一样”成为新的排查点。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenPreviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 展示名，例如 SysUser.java */
    private String name;

    /** 在 ZIP 里的相对路径 */
    private String path;

    /** 代码语言：java / javascript / vue / sql */
    private String language;

    private String content;
}
