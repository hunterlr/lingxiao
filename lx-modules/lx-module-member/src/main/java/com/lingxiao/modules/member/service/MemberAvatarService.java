package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.file.dto.FileVO;
import com.lingxiao.modules.file.service.FileService;
import com.lingxiao.modules.member.dto.MemberInfoVO;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * 会员头像上传。
 *
 * <p>头像统一进文件中心：会员表只存 {@code fileId}，由文件模块负责
 * 类型白名单、大小限制、EXIF 剥离与缩略图。门户渲染时按
 * {@code fileId → /api/file/{id}?size=thumb} 取值。</p>
 *
 * <p>旧头像文件不删除：文件中心没有引用计数，删除可能误删其它业务在用的图；
 * 头像这种低频替换留着更安全，需要清理时按 {@code biz_type=member} 人工处理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberAvatarService {

    private static final String BIZ_TYPE = "member";
    private static final String REMARK = "会员头像";

    private final ObjectProvider<FileService> fileServiceProvider;
    private final MemUserMapper userMapper;
    private final MemberUserService userService;

    /** 上传并替换头像，返回最新资料。 */
    @Transactional
    public MemberInfoVO upload(Long memberId, MultipartFile file) {
        Long currentMemberId = requireMemberId(memberId);
        if (file == null || file.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.avatar.required");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)
                || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.avatar.image");
        }

        FileService fileService = fileServiceProvider.getIfAvailable();
        if (fileService == null) {
            // 文件模块被关掉时直接说清楚，不要让用户以为上传成功
            throw ServiceException.keyed(ErrorCode.FILE_UPLOAD_FAILED, "error.1003.file.moduleDisabled");
        }

        FileVO stored = fileService.upload(file, BIZ_TYPE, REMARK);
        if (stored == null || stored.getFileId() == null) {
            throw ServiceException.keyed(ErrorCode.FILE_UPLOAD_FAILED, "error.3001");
        }
        String avatar = String.valueOf(stored.getFileId());
        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, currentMemberId)
                .set(MemUser::getAvatar, avatar));
        log.info("会员头像已更新: memberId={}, fileId={}", currentMemberId, avatar);
        return userService.getInfo();
    }

    private Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return memberId;
    }
}