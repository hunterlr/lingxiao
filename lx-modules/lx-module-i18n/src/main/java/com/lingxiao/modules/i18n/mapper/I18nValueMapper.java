package com.lingxiao.modules.i18n.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.i18n.dto.CoverageRow;
import com.lingxiao.modules.i18n.dto.KeyValueRow;
import com.lingxiao.modules.i18n.entity.I18nValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 译文 Mapper。
 *
 * <p>只有两处必须写 SQL：</p>
 * <ul>
 *   <li>语言包加载：一次 join 出 {@code key_code -> value_text}，避免 N+1；</li>
 *   <li>覆盖率：按命名空间聚合，SQL 比在 Java 里遍历更稳。</li>
 * </ul>
 *
 * <p>所有查询都固定 {@code tenant_id=0}（平台语言包）；租户覆盖等真正需要时再开参。</p>
 */
@Mapper
public interface I18nValueMapper extends BaseMapper<I18nValue> {

    /**
     * 已发布译文：语言包加载与门户公开接口共用。
     *
     * <p>只取 {@code published_text} 非空且键未停用的行；后台改草稿只动
     * {@code value_text}，不会影响这里。</p>
     */
    @Select("SELECT k.key_code AS keyCode, v.published_text AS valueText "
            + "FROM i18n_value v "
            + "JOIN i18n_key k ON k.key_id = v.key_id "
            + "WHERE v.tenant_id = 0 "
            + "  AND v.language_code = #{languageCode} "
            + "  AND v.published_text IS NOT NULL "
            + "  AND k.status = '0' ")
    List<KeyValueRow> selectPublishedRows(@Param("languageCode") String languageCode);

    /**
     * 指定命名空间的已发布译文。
     *
     * <p>门户按命名空间分片加载；后台发布后 5 秒内生效。</p>
     */
    @Select("SELECT k.key_code AS keyCode, v.published_text AS valueText "
            + "FROM i18n_value v "
            + "JOIN i18n_key k ON k.key_id = v.key_id "
            + "WHERE v.tenant_id = 0 "
            + "  AND v.language_code = #{languageCode} "
            + "  AND k.namespace = #{namespace} "
            + "  AND v.published_text IS NOT NULL "
            + "  AND k.status = '0' ")
    List<KeyValueRow> selectPublishedRowsByNamespace(@Param("languageCode") String languageCode,
                                                     @Param("namespace") String namespace);

    /**
     * 覆盖率：启用键总数 vs 非空译文数。
     *
     * <p>译文不区分草稿 / 已发布——覆盖率衡量“翻译工作量”，
     * 发布状态在语言包页面单独展示。</p>
     */
    @Select("SELECT k.namespace AS namespace, COUNT(*) AS totalKeys, "
            + "SUM(CASE WHEN v.value_text IS NOT NULL AND v.value_text <> '' THEN 1 ELSE 0 END) AS translated "
            + "FROM i18n_key k "
            + "LEFT JOIN i18n_value v ON v.key_id = k.key_id "
            + "  AND v.tenant_id = 0 AND v.language_code = #{languageCode} "
            + "WHERE k.status = '0' "
            + "  AND (#{namespace} IS NULL OR k.namespace = #{namespace}) "
            + "GROUP BY k.namespace "
            + "ORDER BY k.namespace")
    List<CoverageRow> selectCoverage(@Param("languageCode") String languageCode,
                                     @Param("namespace") String namespace);

    /** 发布时用于确定“该语言有哪些命名空间有译文”。 */
    @Select("SELECT DISTINCT k.namespace "
            + "FROM i18n_value v "
            + "JOIN i18n_key k ON k.key_id = v.key_id "
            + "WHERE v.tenant_id = 0 "
            + "  AND v.language_code = #{languageCode} "
            + "  AND v.value_text IS NOT NULL "
            + "  AND v.value_text <> '' "
            + "  AND k.status = '0' "
            + "ORDER BY k.namespace")
    List<String> selectNamespacesWithValues(@Param("languageCode") String languageCode);
}
