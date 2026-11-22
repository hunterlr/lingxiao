package com.lingxiao.modules.member.oauth;

import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sign in with Apple。
 *
 * <p>Apple 的 client_secret 不是固定字符串，而是用团队私钥签出的 ES256 JWT；
 * 用户信息也不在独立端点，而是 {@code id_token} 里的 claims。这里完成：
 * 生成 client secret JWT、换 Token、解析 id_token，并把 DER 签名转成 JOSE 格式。</p>
 *
 * <p>{@code clientSecret} 存 Apple 私钥（PKCS8 PEM），{@code extra_json} 存
 * {@code {"teamId":"...","keyId":"..."}}。id_token 来自 Apple 的 Token 端点
 * （HTTPS 直连），本地只校验 iss / aud / exp，不重复拉 JWKS 验签。</p>
 */
public class AppleOAuthProvider implements MemberOAuthProvider {

    private static final String AUTHORIZE_URL = "https://appleid.apple.com/auth/authorize";
    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String ISSUER = "https://appleid.apple.com";

    private final MemberOAuthProviderSpec spec;
    private final OAuthHttpClient http;
    private final OAuthJson json;

    public AppleOAuthProvider(MemberOAuthProviderSpec spec, OAuthHttpClient http, OAuthJson json) {
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
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", config.getRedirectUri());
        params.put("response_type", "code");
        params.put("scope", StringUtils.hasText(config.getScope()) ? config.getScope() : "name email");
        params.put("response_mode", "form_post");
        params.put("state", state);
        return json.buildUrl(base, params);
    }

    @Override
    public OAuthUserInfo exchange(MemberOAuthProviderConfig config, String code, String codeVerifier) {
        String tokenUrl = StringUtils.hasText(config.getTokenUrl()) ? config.getTokenUrl() : TOKEN_URL;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", config.getClientId());
        form.put("client_secret", clientSecret(config));
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", config.getRedirectUri());
        String body = http.postForm(tokenUrl, form, null);
        Map<String, Object> token = json.parseObject(body);
        String idToken = json.string(token, "id_token");
        if (!StringUtils.hasText(idToken)) {
            throw new OAuthException("apple did not return id_token");
        }
        if (!StringUtils.hasText(json.string(token, "access_token"))) {
            throw new OAuthException("apple did not return access_token");
        }

        String payload = decodePayload(idToken);
        Map<String, Object> claims = json.parseObject(payload);
        if (!ISSUER.equals(json.string(claims, "iss"))) {
            throw new OAuthException("apple id_token issuer mismatch");
        }
        String audience = json.string(claims, "aud");
        if (!config.getClientId().equals(audience)) {
            throw new OAuthException("apple id_token audience mismatch");
        }
        String exp = json.string(claims, "exp");
        if (StringUtils.hasText(exp) && Long.parseLong(exp) * 1000L < System.currentTimeMillis()) {
            throw new OAuthException("apple id_token expired");
        }

        String openId = json.string(claims, "sub");
        if (!StringUtils.hasText(openId)) {
            throw new OAuthException("apple id_token missing sub");
        }
        String email = json.string(claims, "email");
        String nickname = StringUtils.hasText(email)
                ? email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length())
                : displayName() + "用户";

        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(code());
        info.setOpenId(openId);
        info.setNickname(nickname);
        info.setEmail(email);
        info.setEmailVerified(StringUtils.hasText(email) && json.bool(claims, "email_verified"));
        info.setRawData(json.truncate(payload));
        return info;
    }

    /** 用团队私钥生成 Apple 的 client secret JWT。 */
    private String clientSecret(MemberOAuthProviderConfig config) {
        Map<String, Object> extra = new LinkedHashMap<>();
        String extraJson = config.getEntity().getExtraJson();
        if (StringUtils.hasText(extraJson)) {
            extra.putAll(json.parseObject(extraJson));
        }
        String teamId = json.string(extra, "teamId");
        String keyId = json.string(extra, "keyId");
        if (!StringUtils.hasText(teamId) || !StringUtils.hasText(keyId)) {
            throw new OAuthException("apple login requires extra_json.teamId and extra_json.keyId");
        }

        String header = "{\"alg\":\"ES256\",\"kid\":\"" + keyId + "\"}";
        long now = System.currentTimeMillis() / 1000L;
        String claims = "{\"iss\":\"" + teamId + "\",\"iat\":" + now
                + ",\"exp\":" + (now + 3600)
                + ",\"aud\":\"" + ISSUER + "\",\"sub\":\"" + config.getClientId() + "\"}";
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(claims.getBytes(StandardCharsets.UTF_8));

        try {
            PrivateKey privateKey = loadPrivateKey(config.getClientSecret());
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] jose = derToJose(signature.sign(), 32);
            return signingInput + "." + base64Url(jose);
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthException("cannot sign apple client secret", e);
        }
    }

    private PrivateKey loadPrivateKey(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new OAuthException("apple private key is missing");
        }
        try {
            String normalized = secret
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new OAuthException("cannot load apple private key (expect PKCS8 PEM)", e);
        }
    }

    private String decodePayload(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new OAuthException("invalid apple id_token");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(pad(parts[1]));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String pad(String value) {
        int remainder = value.length() % 4;
        return remainder == 0 ? value : value + "====".substring(remainder);
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** JWT ES256 要求 R||S 定长 64 字节；Java 返回的是 DER SEQUENCE。 */
    private byte[] derToJose(byte[] der, int partLength) {
        int offset = 0;
        if (der[offset++] != 0x30) {
            throw new OAuthException("invalid ECDSA signature");
        }
        offset = skipLength(der, offset);
        if (der[offset++] != 0x02) {
            throw new OAuthException("invalid ECDSA signature");
        }
        int rLength = der[offset++];
        byte[] r = Arrays.copyOfRange(der, offset, offset + rLength);
        offset += rLength;
        if (der[offset++] != 0x02) {
            throw new OAuthException("invalid ECDSA signature");
        }
        int sLength = der[offset++];
        byte[] s = Arrays.copyOfRange(der, offset, offset + sLength);

        byte[] result = new byte[partLength * 2];
        copyPadded(r, result, 0, partLength);
        copyPadded(s, result, partLength, partLength);
        return result;
    }

    private int skipLength(byte[] der, int offset) {
        int first = der[offset++] & 0xff;
        if ((first & 0x80) == 0) {
            return offset;
        }
        int count = first & 0x7f;
        for (int i = 0; i < count; i++) {
            offset++;
        }
        return offset;
    }

    private void copyPadded(byte[] source, byte[] target, int targetOffset, int length) {
        BigInteger value = new BigInteger(1, source);
        byte[] bytes = value.toByteArray();
        if (bytes.length > length) {
            bytes = Arrays.copyOfRange(bytes, bytes.length - length, bytes.length);
        }
        System.arraycopy(bytes, 0, target, targetOffset + (length - bytes.length), bytes.length);
    }
}
