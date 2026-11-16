package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 公告（sys_notice）。
 *
 * <p>{@code noticeType}：1 通知 / 2 公告；{@code status}：0 正常 / 1 关闭。</p>
 *
 * <p>内容是富文本（WangEditor 5 提交 HTML），服务端用 Jsoup 白名单清洗
 * （{@code HtmlSanitizer}），写入与读取两侧都过一遍，不能直接信任前端。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {

    @TableId(value = "notice_id", type = IdType.ASSIGN_ID)
    private Long noticeId;

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题不能超过 200 个字符")
    private String noticeTitle;

    /** 1 通知 / 2 公告 */
    private String noticeType;

    /** 富文本 HTML，已由服务端白名单清洗；前端渲染仍需按 HTML 处理 */
    private String noticeContent;

    /** 0 正常 / 1 关闭 */
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
