package com.lingxiao.modules.member.oauth;

import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * 支付宝登录。
 *
 * <p>支付宝不是标准 OAuth2：换 Token 与取用户信息都走网关，并且请求要做
 * RSA2 签名。这里把 {@code clientSecret} 当作应用私钥（PKCS8，兼容 PKCS1），
 * 用 {@code SHA256withRSA} 签名；响应依赖 HTTPS，不在本地验签。</p>
 */
public class AlipayOAuthProvider implements MemberOAuthProvider {

    private static final String AUTHORIZE_URL = "https://openauth.alipay.com/oauth2/publicAppAuthorize.htm";
    private static final String GATEWAY_URL = "https://openapi.alipay.com/gateway.do";

    private final MemberOAuthProviderSpec spec;
    private final OAuthHttpClient http;
    private final OAuthJson json;

    public AlipayOAuthProvider(MemberOAuthProviderSpec spec, OAuthHttpClient http, OAuthJson json) {
        this.spec = spec;
        this.http = http;
        this.json = json;
    }

    @Override
    public String code() {
        return spec.getCode();
    }

    @Override
    public String displayName() {
        return spec.getDisplayName();
    }

    @Override
    public String group() {
        return spec.getGroup();
    }

    @Override
    public String authorizeUrl(MemberOAuthProviderConfig config, String state, String codeChallenge) {
        String base = StringUtils.hasText(config.getAuthorizeUrl()) ? config.getAuthorizeUrl() : AUTHORIZE_URL;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", config.getClientId());
        params.put("scope", StringUtils.hasText(config.getScope()) ? config.getScope() : "auth_user");
        params.put("redirect_uri", config.getRedirectUri());
        params.put("state", state);
        return json.buildUrl(base, params);
    }

    @Override
    public OAuthUserInfo exchange(MemberOAuthProviderConfig config, String code, String codeVerifier) {
        PrivateKey privateKey = loadPrivateKey(config.getClientSecret());
        String tokenUrl = StringUtils.hasText(config.getTokenUrl()) ? config.getTokenUrl() : GATEWAY_URL;

        Map<String, String> tokenRequest = baseRequest(config, "alipay.system.oauth.token");
        tokenRequest.put("grant_type", "authorization_code");
        tokenRequest.put("code", code);
        String tokenBody = http.postForm(tokenUrl, sign(tokenRequest, privateKey), null);
        Map<String, Object> tokenRoot = json.parseObject(tokenBody);
        Map<String, Object> token = node(tokenRoot, "alipay_system_oauth_token_response");
        String accessToken = json.string(token, "access_token");
        String openId = first(json.string(token, "user_id"), json.string(token, "alipay_user_id"));
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(openId)) {
            throw new OAuthException("alipay did not return access_token / user_id");
        }

        String userInfoUrl = StringUtils.hasText(config.getUserInfoUrl()) ? config.getUserInfoUrl() : GATEWAY_URL;
        Map<String, String> userRequest = baseRequest(config, "alipay.user.info.share");
        userRequest.put("auth_token", accessToken);
        String userBody = http.postForm(userInfoUrl, sign(userRequest, privateKey), null);
        Map<String, Object> userRoot = json.parseObject(userBody);
        Map<String, Object> user = node(userRoot, "alipay_user_info_share_response");

        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(code());
        info.setOpenId(first(json.string(user, "user_id"), openId));
        info.setNickname(first(json.string(user, "nick_name"), displayName() + "用户"));
        String avatar = json.string(user, "avatar");
        info.setAvatar(isHttpUrl(avatar) ? avatar : null);
        info.setEmail(json.string(user, "email"));
        info.setRawData(json.truncate(userBody));
        return info;
    }

    private Map<String, String> baseRequest(MemberOAuthProviderConfig config, String method) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", config.getClientId());
        params.put("method", method);
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", timestamp());
        params.put("version", "1.0");
        return params;
    }

    /** 支付宝要求按 key 升序拼接，再做 SHA256withRSA。 */
    private Map<String, String> sign(Map<String, String> params, PrivateKey privateKey) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!StringUtils.hasText(entry.getValue()) || "sign".equals(entry.getKey())) {
                continue;
            }
            if (content.length() > 0) {
                content.append('&');
            }
            content.append(entry.getKey()).append('=').append(entry.getValue());
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(content.toString().getBytes(StandardCharsets.UTF_8));
            Map<String, String> result = new LinkedHashMap<>(params);
            result.put("sign", Base64.getEncoder().encodeToString(signature.sign()));
            return result;
        } catch (Exception e) {
            throw new OAuthException("cannot sign alipay request", e);
        }
    }

    private PrivateKey loadPrivateKey(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new OAuthException("alipay private key is missing");
        }
        try {
            String normalized = secret.trim();
            boolean pkcs1 = normalized.contains("BEGIN RSA PRIVATE KEY");
            normalized = normalized
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            if (pkcs1) {
                keyBytes = wrapPkcs1(keyBytes);
            }
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            throw new OAuthException("cannot load alipay private key (expect PKCS8/PKCS1 PEM)", e);
        }
    }

    /** 把 PKCS1 RSA 私钥包成 PKCS8，兼容支付宝工具导出的旧格式。 */
    private byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] algorithm = new byte[]{
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0x02);
        body.write(0x01);
        body.write(0x00);
        body.write(algorithm, 0, algorithm.length);
        body.write(0x04);
        writeLength(body, pkcs1.length);
        body.write(pkcs1, 0, pkcs1.length);
        byte[] content = body.toByteArray();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(0x30);
        writeLength(result, content.length);
        result.write(content, 0, content.length);
        return result.toByteArray();
    }

    private void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
            return;
        }
        int bytes = length <= 0xff ? 1 : length <= 0xffff ? 2 : length <= 0xffffff ? 3 : 4;
        out.write(0x80 | bytes);
        for (int i = bytes - 1; i >= 0; i--) {
            out.write((length >>> (8 * i)) & 0xff);
        }
    }

    private String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.format(new Date());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> node(Map<String, Object> root, String name) {
        Object error = root.get("error_response");
        if (error instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) error;
            throw new OAuthException("alipay error: " + json.string(map, "sub_code")
                    + " " + json.string(map, "sub_msg"));
        }
        Object node = root.get(name);
        if (!(node instanceof Map)) {
            throw new OAuthException("alipay response missing " + name);
        }
        return (Map<String, Object>) node;
    }

    private String first(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean isHttpUrl(String url) {
        return StringUtils.hasText(url)
                && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
