package com.lingxiao.modules.member.service;

import com.lingxiao.modules.file.dto.FileVO;
import com.lingxiao.modules.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

/**
 * 第三方头像本地化。
 *
 * <p>微信 / QQ 等平台的头像 URL 有防盗链或有效期，直接存外链迟早会变成裂图。
 * 这里把头像下载到文件中心，会员表只存 fileId；原始 URL 留在
 * {@code mem_user_social.avatar} 仅用于排障。</p>
 *
 * <p><b>安全边界：</b>只允许 http(s)、只允许公网地址、禁止重定向到内网；
 * 连接 / 读取各 3 秒；最大 2MB；只接受 JPEG / PNG / WebP，按文件头识别，
 * 不信任扩展名与 Content-Type。任何一步失败都返回 null，不影响第三方登录主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberAvatarLocalizer {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final String USER_AGENT = "LingXiao-Member-Avatar/1.0";
    private static final String BIZ_TYPE = "member";

    private final ObjectProvider<FileService> fileServiceProvider;

    /**
     * 下载并转存头像。
     *
     * @return 文件中心 fileId 字符串；失败返回 null
     */
    public String localize(String provider, String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return null;
        }
        try {
            DownloadedImage image = download(avatarUrl.trim());
            if (image == null) {
                return null;
            }
            FileService fileService = fileServiceProvider.getIfAvailable();
            if (fileService == null) {
                log.warn("文件模块未启用，跳过头像转存: provider={}", provider);
                return null;
            }
            FileVO file = fileService.uploadBytes(image.bytes,
                    "oauth-avatar." + image.extension, image.mime, BIZ_TYPE,
                    "第三方头像转存: " + provider);
            if (file.getFileId() == null) {
                return null;
            }
            log.info("第三方头像已转存: provider={}, fileId={}, bytes={}",
                    provider, file.getFileId(), image.bytes.length);
            return String.valueOf(file.getFileId());
        } catch (Exception e) {
            log.warn("第三方头像转存失败，忽略: provider={}, host={}, error={}",
                    provider, safeHost(avatarUrl), e.getMessage());
            return null;
        }
    }

    private DownloadedImage download(String url) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            URL target = new URL(current);
            if (!isAllowedTarget(target)) {
                log.warn("第三方头像地址不允许: host={}", safeHost(current));
                return null;
            }
            HttpURLConnection connection = open(target);
            try {
                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    String location = connection.getHeaderField("Location");
                    if (!StringUtils.hasText(location)) {
                        return null;
                    }
                    current = new URL(target, location.trim()).toString();
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    log.warn("第三方头像下载返回非 200: status={}, host={}", status, safeHost(current));
                    return null;
                }
                return readImage(connection);
            } finally {
                connection.disconnect();
            }
        }
        log.warn("第三方头像重定向过多: host={}", safeHost(url));
        return null;
    }

    private HttpURLConnection open(URL url) throws IOException {
        URLConnection raw = url.openConnection();
        if (!(raw instanceof HttpURLConnection)) {
            throw new IOException("not an http connection");
        }
        HttpURLConnection connection = (HttpURLConnection) raw;
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp");
        connection.setRequestProperty("Accept-Encoding", "identity");
        return connection;
    }

    private DownloadedImage readImage(HttpURLConnection connection) throws IOException {
        String contentType = connection.getContentType();
        long declaredLength = parseLength(connection.getContentLengthLong());
        if (declaredLength > MAX_BYTES) {
            log.warn("第三方头像超过大小限制: length={}", declaredLength);
            return null;
        }

        byte[] bytes;
        try (InputStream in = connection.getInputStream()) {
            bytes = readLimited(in);
        }
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        ImageType type = detect(bytes);
        if (type == null) {
            log.warn("第三方头像不是受支持的图片类型: contentType={}", contentType);
            return null;
        }
        // 文件头优先；只有文件头缺失时才用响应头兜底，避免把 HTML 伪装成图片存下来
        return new DownloadedImage(bytes, type.extension, type.mime);
    }

    private byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        byte[] buffer = new byte[8 * 1024];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                log.warn("第三方头像超过大小限制: bytes>{}", MAX_BYTES);
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private long parseLength(long value) {
        return value < 0 ? 0 : value;
    }

    private boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private ImageType detect(byte[] bytes) {
        if (startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF})) {
            return ImageType.JPEG;
        }
        if (startsWith(bytes, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ImageType.PNG;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 只允许 http(s) + 公网地址。
     *
     * <p>这里在建立连接前做一次解析检查，能挡住把 localhost / 内网 IP 直接填进
     * 头像地址的 SSRF；不处理 DNS 重绑定级别的攻击（那需要固定解析结果后按 IP
     * 直连并自行处理 TLS SNI，成本远高于头像转存本身）。</p>
     */
    private boolean isAllowedTarget(URL url) throws IOException {
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            return false;
        }
        if (StringUtils.hasText(url.getUserInfo())) {
            return false;
        }
        String host = url.getHost();
        if (!StringUtils.hasText(host)) {
            return false;
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            return false;
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int first = raw[0] & 0xFF;
            int second = raw[1] & 0xFF;
            // 0.0.0.0/8 与运营商级 NAT 100.64.0.0/10
            return first != 0 && !(first == 100 && second >= 64 && second <= 127);
        }
        if (raw.length == 16) {
            // IPv6 唯一本地地址 fc00::/7
            return (raw[0] & 0xFE) != 0xFC;
        }
        return true;
    }

    private String safeHost(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return "invalid";
        }
    }

    private static final class DownloadedImage {
        private final byte[] bytes;
        private final String extension;
        private final String mime;

        private DownloadedImage(byte[] bytes, String extension, String mime) {
            this.bytes = bytes;
            this.extension = extension;
            this.mime = mime;
        }
    }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String mime;

        ImageType(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }
    }
}
