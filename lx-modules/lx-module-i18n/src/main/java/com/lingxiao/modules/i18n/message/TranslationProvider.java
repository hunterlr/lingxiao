package com.lingxiao.modules.i18n.message;

/**
 * 译文来源抽象。
 *
 * <p>MessageSource、门户接口、发布服务都通过这个接口取“已发布译文”，
 * 不直接依赖 Mapper；测试里可以换成内存实现。</p>
 */
public interface TranslationProvider {

    /** 单键译文；未命中返回 null，由调用方决定回退资源包还是 key。 */
    String find(String languageCode, String keyCode);

    /** 某语言的全部已发布译文（keyCode -> valueText），用于门户整包下发。 */
    java.util.Map<String, String> published(String languageCode);
}
