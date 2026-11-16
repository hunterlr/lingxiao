package com.lingxiao.modules.system.support;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 富文本 HTML 白名单清洗器。
 *
 * <p>为什么必须做：公告 / 后续 CMS 文章的正文是前端富文本编辑器提交的 HTML。
 * 只要服务端原样入库、原样返回，任何一个拿到编辑权限的账号都能塞进
 * {@code <script>}、{@code onerror=}、{@code javascript:} 之类的载荷，
 * 最终在别的管理员的浏览器里执行。</p>
 *
 * <p>为什么不用正则：HTML 不是正则语言。{@code <scr<script>ipt>}、属性引号嵌套、
 * 大小写混写、实体编码都能绕过“删标签”式正则；正则也无法区分文本里的
 * {@code &lt;script&gt;} 和真实标签。唯一稳妥的做法是解析成 DOM，
 * 按白名单重建，这里用 Jsoup 的 {@link Safelist}。</p>
 *
 * <p>白名单在 {@link Safelist#relaxed()} 基础上做了两件事：</p>
 * <ol>
 *   <li>补充编辑器常用但 relaxed 未包含的语义标签（hr / del / ins / figure / video 等）；</li>
 *   <li>给链接强制加 {@code rel="nofollow noopener noreferrer"}，
 *       防止 {@code target="_blank"} 反向控制 opener 页面。</li>
 * </ol>
 *
 * <p>清洗点有两处：写入（create / update）拦新增脏数据，读取（page / detail）
 * 兜历史脏数据——上线前已经入库的内容没人保证干净。清洗不放在前端
 * （前端可绕过），也不放在 Controller（容易被新接口漏掉）。</p>
 *
 * <p>注意：{@code style} / {@code class} 不在白名单内，富文本里的字体颜色、
 * 居中等内联样式会被剥离。这是 v1.0 的取舍：CSS 里同样可以藏
 * {@code expression()} / {@code url()}，在没做 CSS 白名单前一律不放行。</p>
 */
public final class HtmlSanitizer {

    /** 白名单实例线程安全，构建一次复用；不要每次清洗都 new。 */
    private static final Safelist SAFELIST = buildSafelist();

    /**
     * 清洗时使用的基准地址。
     *
     * <p>不是给前端用的真实域名，只用于 Jsoup 判断相对地址的协议：
     * {@code src="/api/file/1"} 需要先解析成 {@code https://.../api/file/1}
     * 才能通过 http/https 协议白名单，否则相对地址会被整条属性删掉。
     * 配合 {@link Safelist#preserveRelativeLinks(boolean)}，输出里仍然保留
     * 原始相对地址，不会被改写成这个假域名。</p>
     */
    private static final String BASE_URI = "https://lingxiao.local/";

    private HtmlSanitizer() {
    }

    /**
     * 清洗 HTML。
     *
     * @param html 原始 HTML，{@code null} 原样返回（字段可空，不要把 null 变成空串）
     * @return 只保留白名单标签 / 属性的 HTML
     */
    public static String clean(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, BASE_URI, SAFELIST);
    }

    private static Safelist buildSafelist() {
        return Safelist.relaxed()
                // 相对链接原样保留，否则 <img src="/api/file/1"> 会被补成绝对地址
                .preserveRelativeLinks(true)
                // 编辑器可能产出的语义 / 媒体标签
                .addTags("hr", "s", "del", "ins", "figure", "figcaption", "video", "audio", "source")
                // 链接允许新窗口打开（已强制 noopener）
                .addAttributes("a", "target")
                .addAttributes("img", "alt", "title", "width", "height")
                .addAttributes("video", "controls", "width", "height", "poster")
                .addAttributes("source", "src", "type")
                .addAttributes("td", "colspan", "rowspan")
                .addAttributes("th", "colspan", "rowspan")
                .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
                // 显式声明协议，杜绝 javascript: / data: 伪协议
                .addProtocols("img", "src", "http", "https")
                .addProtocols("video", "src", "http", "https")
                .addProtocols("video", "poster", "http", "https")
                .addProtocols("source", "src", "http", "https");
    }
}
