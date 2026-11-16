package com.lingxiao.modules.system.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 强退在线用户请求。
 *
 * <p>用请求体而不是路径参数，一是支持批量，二是接口层会关闭请求参数落日志
 * （{@code @Log(saveRequestData=false)}），避免 Token 被写进操作日志。
 * 这里传的仍然是“脱敏 tokenId”，不是真实 Token。</p>
 */
@Data
public class KickoutRequest {

    @NotEmpty(message = "{valid.online.sessions.required}")
    private List<String> tokenIds;
}
