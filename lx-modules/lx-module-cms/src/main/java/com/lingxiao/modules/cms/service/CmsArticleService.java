package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.web.util.HtmlSanitizer;
import com.lingxiao.modules.cms.dto.CmsArticleRecycleVO;
import com.lingxiao.modules.cms.dto.CmsArticleVersionVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsArticleVersion;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsModel;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleVersionMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * CMS 文章服务。
 *
 * <p><b>正文安全：</b>正文由 WangEditor 提交 HTML，服务端用
 * {@link HtmlSanitizer} 在写入与读取两侧都清洗（与公告一致），
 * 不依赖前端、也不依赖数据库。</p>
 *
 * <p><b>状态流转：</b>新建一律草稿；普通编辑只改内容，不改状态与发布时间；
 * 发布 / 下线 / 定时走独立接口。这样“改个错别字”不会把已下线文章悄悄重新发布。</p>
 *
 * <p><b>列表不查正文：</b>{@code content} 是 longtext，列表页用不到；
 * 分页查询显式排除该列，避免一页几十篇正文把内存和网络拖慢。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsArticleService {

    /** 文章状态：草稿 / 已发布 / 已下线 / 定时待发布 */
    public static final String STATUS_DRAFT = "0";
    public static final String STATUS_PUBLISHED = "1";
    public static final String STATUS_OFFLINE = "2";
    public static final String STATUS_SCHEDULED = "3";

    /** 版本来源：创建 / 编辑 / 发布 / 回滚 / 回收站恢复 */
    public static final String VERSION_CREATE = "create";
    public static final String VERSION_EDIT = "edit";
    public static final String VERSION_PUBLISH = "publish";
    public static final String VERSION_ROLLBACK = "rollback";
    public static final String VERSION_RESTORE = "restore";

    private final CmsArticleMapper articleMapper;
    private final CmsArticleTagMapper articleTagMapper;
    private final CmsArticleVersionMapper versionMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;
    private final CmsSiteService siteService;
    private final CmsCategoryService categoryService;
    private final CmsModelService modelService;
    private final CmsContentExtService extService;
    private final CmsContentI18nService i18nService;

    /** 每篇文章保留的版本数；Spring 注入，单测直接使用默认值 */
    @Value("${lx.cms.version-keep:30}")
    private int versionKeep = 30;

    public PageResult<CmsArticle> page(long pageNum, long pageSize, Long siteId, Long categoryId,
                                       Long tagId, String keyword, String status) {
        Page<CmsArticle> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<CmsArticle> wrapper = new LambdaQueryWrapper<CmsArticle>()
                .select(CmsArticle.class, field -> !"content".equals(field.getColumn()))
                .eq(siteId != null, CmsArticle::getSiteId, siteId)
                .eq(StringUtils.hasText(status), CmsArticle::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(CmsArticle::getTitle, keyword)
                        .or().like(CmsArticle::getSummary, keyword));
        if (categoryId != null) {
            // 选父栏目时把子栏目文章一并列出来
            wrapper.in(CmsArticle::getCategoryId, categoryService.subtreeIds(categoryId));
        }
        if (tagId != null) {
            // 用 EXISTS 子查询而不是 JOIN：分页 count 不会被一对多关联放大
            wrapper.apply("EXISTS (SELECT 1 FROM cms_article_tag t "
                    + "WHERE t.article_id = cms_article.article_id AND t.tag_id = {0})", tagId);
        }
        wrapper.orderByDesc(CmsArticle::getCreateTime);
        Page<CmsArticle> result = articleMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public CmsArticle detail(Long articleId) {
        CmsArticle article = mustGet(articleId);
        article.setContent(HtmlSanitizer.clean(article.getContent()));
        article.setTagIds(articleTagMapper.selectTagIds(articleId));
        if (article.getTagIds() == null) {
            article.setTagIds(new ArrayList<>());
        }
        article.setExt(extService.load("article", articleId));
        return article;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsArticle article) {
        siteService.mustGet(article.getSiteId());
        CmsCategory category = mustCategory(article.getCategoryId(), article.getSiteId());
        ensureSlugUnique(article.getSiteId(), article.getSlug(), null);

        article.setArticleId(null);
        article.setCategoryId(category.getCategoryId());
        article.setTitle(article.getTitle().trim());
        article.setContent(HtmlSanitizer.clean(article.getContent()));
        article.setStatus(STATUS_DRAFT);
        article.setPublishTime(null);
        article.setScheduleTime(null);
        article.setViewCount(0L);
        article.setSort(article.getSort() == null ? 0 : article.getSort());
        fillAuthor(article);
        article.setCreateBy(LoginHelper.getUserId());
        articleMapper.insert(article);

        replaceTags(article.getArticleId(), article.getSiteId(), article.getTagIds());
        saveExt(article, category);
        snapshot(article, VERSION_CREATE);
        return article.getArticleId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long articleId, CmsArticle article) {
        CmsArticle existing = mustGet(articleId);
        // 站点不允许通过编辑文章改动：跨站点移动内容应走“移动”接口，避免栏目 / 标签错配
        Long siteId = existing.getSiteId();
        CmsCategory category = mustCategory(article.getCategoryId(), siteId);
        ensureSlugUnique(siteId, article.getSlug(), articleId);

        existing.setCategoryId(category.getCategoryId());
        existing.setTitle(article.getTitle().trim());
        existing.setSlug(article.getSlug());
        existing.setSummary(article.getSummary());
        existing.setCover(article.getCover());
        existing.setContent(HtmlSanitizer.clean(article.getContent()));
        existing.setSource(article.getSource());
        existing.setSourceUrl(article.getSourceUrl());
        existing.setSort(article.getSort() == null ? existing.getSort() : article.getSort());
        existing.setRemark(article.getRemark());
        existing.setUpdateBy(currentUserId());
        articleMapper.updateById(existing);

        replaceTags(articleId, siteId, article.getTagIds());
        saveExt(existing, category);
        snapshot(existing, VERSION_EDIT);
    }

    /**
     * 发布：草稿 / 已下线 → 已发布；首次发布写 publishTime，重新发布保留原时间。
     *
     * <p>手动发布会顺带取消定时：既然人已经决定现在发，计划时间不应再留着。</p>
     *
     * <p>发布后写一条 publish 快照；内容与最近一次快照完全相同（含状态）时由哈希去重跳过。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long articleId) {
        CmsArticle existing = mustGet(articleId);
        LambdaUpdateWrapper<CmsArticle> update = new LambdaUpdateWrapper<CmsArticle>()
                .eq(CmsArticle::getArticleId, articleId)
                .set(CmsArticle::getStatus, STATUS_PUBLISHED)
                .set(CmsArticle::getScheduleTime, null)
                .set(CmsArticle::getUpdateBy, currentUserId());
        if (existing.getPublishTime() == null) {
            update.set(CmsArticle::getPublishTime, LocalDateTime.now());
        }
        articleMapper.update(null, update);
        snapshot(mustGet(articleId), VERSION_PUBLISH);
    }

    /** 下线：已发布 → 已下线；草稿调用也允许，结果就是“不会出现在门户”。 */
    public void offline(Long articleId) {
        mustGet(articleId);
        articleMapper.update(null, new LambdaUpdateWrapper<CmsArticle>()
                .eq(CmsArticle::getArticleId, articleId)
                .set(CmsArticle::getStatus, STATUS_OFFLINE)
                .set(CmsArticle::getScheduleTime, null)
                .set(CmsArticle::getUpdateBy, currentUserId()));
    }

    /**
     * 定时发布：草稿 / 已下线 → 定时待发布。
     *
     * <p>已发布文章必须先下线：直接排期等于“悄悄替换线上内容”，
     * 运营需要明确知道自己在改动什么。重复排期视为改时间，允许。</p>
     */
    public void schedule(Long articleId, LocalDateTime scheduleTime) {
        CmsArticle existing = mustGet(articleId);
        if (scheduleTime == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请选择定时发布时间");
        }
        if (!scheduleTime.isAfter(LocalDateTime.now())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "定时发布时间必须晚于当前时间");
        }
        if (STATUS_PUBLISHED.equals(existing.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "文章已发布，请先下线再设置定时发布");
        }
        int updated = articleMapper.scheduleById(articleId, scheduleTime, currentUserId(),
                LocalDateTime.now());
        if (updated == 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "文章状态已变化，请刷新后重试");
        }
    }

    /** 取消定时：定时待发布 → 草稿；其它状态调用直接拒绝，避免误清线上文章。 */
    public void cancelSchedule(Long articleId) {
        CmsArticle existing = mustGet(articleId);
        if (!STATUS_SCHEDULED.equals(existing.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "只有定时待发布的文章可以取消定时");
        }
        int updated = articleMapper.cancelScheduleById(articleId, currentUserId(),
                LocalDateTime.now());
        if (updated == 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "文章状态已变化，请刷新后重试");
        }
    }

    /**
     * 扫描并发布到点的定时文章。
     *
     * <p>抢占靠 Mapper 的条件 UPDATE（status='3' → '1'），多实例同时扫描时
     * 只有一个实例影响行数为 1，不需要额外的全局锁表。</p>
     *
     * <p>每次最多处理 {@code limit} 篇，避免一次扫描把数据库拖住；
     * 剩余的下一轮继续。</p>
     *
     * @return 本轮实际发布篇数
     */
    public int publishDueScheduled(int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> dueIds = articleMapper.selectDueScheduledIds(now, Math.max(limit, 1));
        if (dueIds == null || dueIds.isEmpty()) {
            return 0;
        }
        int published = 0;
        for (Long articleId : dueIds) {
            // 条件 UPDATE 抢到的实例才写发布快照：没抢到的实例不重复记版本
            if (articleMapper.publishScheduledById(articleId, now) > 0) {
                published++;
                snapshot(mustGet(articleId), VERSION_PUBLISH);
            }
        }
        return published;
    }

    /**
     * 删除文章：进回收站，不做物理删除。
     *
     * <p>标签关联保留，恢复后不用重新打标；只有“彻底删除”才清关联。
     * 记录删除人昵称快照，避免以后用户改名 / 删号后审计信息丢失。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long articleId) {
        mustGet(articleId);
        LocalDateTime now = LocalDateTime.now();
        int updated = articleMapper.softDeleteById(articleId, currentUserId(),
                currentOperatorName(), now);
        if (updated == 0) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "文章不存在或已删除");
        }
    }

    /** 回收站分页：标题 / slug 关键字，只查删除审计，不带正文。 */
    public PageResult<CmsArticleRecycleVO> recyclePage(long pageNum, long pageSize,
                                                       Long siteId, String keyword) {
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        Page<CmsArticleRecycleVO> page = new Page<>(current, size);
        String pattern = likePattern(keyword);
        Page<CmsArticleRecycleVO> result = articleMapper.selectRecyclePage(page, siteId, pattern);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords());
    }

    /**
     * 恢复：回收站 → 草稿。
     *
     * <p>不按删除前状态恢复：一篇已发布文章恢复后立刻回门户，容易在运营
     * 没准备好时曝光；统一落草稿，需要时再显式发布。publish_time 保留。</p>
     *
     * <p>恢复前二次校验：站点和栏目必须仍然存在（栏目已删则只能彻底删除），
     * slug 不能与其它文章冲突。正常流程下 slug 唯一性已包含回收站，
     * 这里是防历史数据 / 手工改库。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long articleId) {
        CmsArticle deleted = mustGetDeleted(articleId);
        siteService.mustGet(deleted.getSiteId());
        CmsCategory category = deleted.getCategoryId() == null
                ? null : categoryMapper.selectById(deleted.getCategoryId());
        if (category == null || !Objects.equals(category.getSiteId(), deleted.getSiteId())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "所属栏目已删除，无法恢复，请直接彻底删除该文章");
        }
        ensureSlugAvailableForRestore(deleted);
        int updated = articleMapper.restoreById(articleId, currentUserId(), LocalDateTime.now());
        if (updated == 0) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "回收站中不存在该文章");
        }
        snapshot(mustGet(articleId), VERSION_RESTORE);
    }

    /** 彻底删除：仅限回收站记录；先清标签关联与历史版本，再物理删除正文。 */
    @Transactional(rollbackFor = Exception.class)
    public void purge(Long articleId) {
        mustGetDeleted(articleId);
        articleTagMapper.deleteByArticleId(articleId);
        versionMapper.deleteByArticleId(articleId);
        extService.deleteByOwner("article", articleId);
        i18nService.deleteByOwner("article", articleId);
        int removed = articleMapper.hardDeleteDeletedById(articleId);
        if (removed == 0) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "回收站中不存在该文章");
        }
    }

    /* ==================== 内容版本 ==================== */

    /** 版本列表：校验文章存在，只返回摘要字段，不查正文。 */
    public PageResult<CmsArticleVersionVO> versionPage(Long articleId, long pageNum, long pageSize) {
        mustGet(articleId);
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        Page<CmsArticleVersionVO> page = new Page<>(current, size);
        Page<CmsArticleVersionVO> result = versionMapper.selectVersionPage(page, articleId);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords());
    }

    /** 版本详情：返回正文（读取侧再清洗一次）与标签 ID 快照。 */
    public CmsArticleVersion versionDetail(Long articleId, Long versionId) {
        mustGet(articleId);
        CmsArticleVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(version.getArticleId(), articleId)) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        version.setContent(HtmlSanitizer.clean(version.getContent()));
        version.setTagIdList(parseTagIds(version.getTagIds()));
        return version;
    }

    /**
     * 回滚到指定版本。
     *
     * <p>回滚会覆盖当前内容并统一回到草稿：不能因为点了一次回滚就把旧内容
     * 直接推上线，需要运营重新确认发布。{@code publish_time} 作为首次发布时间保留。</p>
     *
     * <p>回滚前校验：版本属于该文章、栏目仍存在且属于同一站点、slug 未被其它文章占用。
     * 版本中的标签若已被删除或跨站点，会被跳过（内容优先），并在日志里留痕。</p>
     *
     * <p>回滚成功后写一条 rollback 快照，当前内容仍可从版本列表里找回。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long articleId, Long versionId) {
        CmsArticle current = mustGet(articleId);
        CmsArticleVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(version.getArticleId(), articleId)) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        CmsCategory category = version.getCategoryId() == null
                ? null : categoryMapper.selectById(version.getCategoryId());
        if (category == null || !Objects.equals(category.getSiteId(), current.getSiteId())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "版本中的栏目已删除或不属于当前站点，无法回滚");
        }
        ensureSlugUnique(current.getSiteId(), version.getSlug(), articleId);

        current.setCategoryId(category.getCategoryId());
        current.setTitle(version.getTitle());
        current.setSlug(version.getSlug());
        current.setSummary(version.getSummary());
        current.setCover(version.getCover());
        current.setContent(HtmlSanitizer.clean(version.getContent()));
        current.setSource(version.getSource());
        current.setSourceUrl(version.getSourceUrl());
        current.setStatus(STATUS_DRAFT);
        current.setScheduleTime(null);
        current.setUpdateBy(currentUserId());
        articleMapper.updateById(current);

        restoreTagsLenient(articleId, current.getSiteId(), parseTagIds(version.getTagIds()));
        snapshot(current, VERSION_ROLLBACK);
    }

    // ==================== 内部方法 ====================

    /** 按栏目绑定的模型校验扩展字段并落库；只保留模型已定义字段。 */
    private void saveExt(CmsArticle article, CmsCategory category) {
        CmsModel model = modelService.resolveForCategory(category);
        Map<String, Object> ext = modelService.validateExt(
                model.getModelId(), article.getExt(), true);
        extService.save("article", article.getArticleId(), article.getSiteId(),
                model.getModelId(), ext);
    }

    private CmsArticle mustGet(Long articleId) {
        CmsArticle article = articleId == null ? null : articleMapper.selectById(articleId);
        if (article == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private CmsArticle mustGetDeleted(Long articleId) {
        CmsArticle article = articleId == null ? null : articleMapper.selectDeletedById(articleId);
        if (article == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "回收站中不存在该文章");
        }
        return article;
    }

    /** 恢复前校验 slug：唯一性口径与新建 / 编辑一致，都包含回收站。 */
    private void ensureSlugAvailableForRestore(CmsArticle deleted) {
        if (!StringUtils.hasText(deleted.getSlug())) {
            return;
        }
        long conflict = articleMapper.countBySlugIncludingDeleted(
                deleted.getSiteId(), deleted.getSlug().trim(), deleted.getArticleId());
        if (conflict > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "slug 已被其他文章占用，请先处理占用方再恢复");
        }
    }

    /** 当前操作人 ID；单测无登录上下文时留空，真实请求已被权限拦截。 */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 删除人昵称快照；取不到时退回用户名，再退回 system。 */
    private String currentOperatorName() {
        try {
            String nickname = LoginHelper.getNickname();
            if (StringUtils.hasText(nickname)) {
                return nickname;
            }
            String username = LoginHelper.getUsername();
            return StringUtils.hasText(username) ? username : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    /** 关键字转 LIKE 参数；截断 50 字符，避免超长 LIKE 拖慢后台列表。 */
    private String likePattern(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String value = keyword.trim();
        if (value.length() > 50) {
            value = value.substring(0, 50);
        }
        return "%" + value + "%";
    }

    private CmsCategory mustCategory(Long categoryId, Long siteId) {
        CmsCategory category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "栏目不存在");
        }
        if (!Objects.equals(category.getSiteId(), siteId)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "栏目不属于当前站点");
        }
        return category;
    }

    /** 首次创建时把当前登录人写成作者快照；后端已指定作者时不覆盖。 */
    private void fillAuthor(CmsArticle article) {
        if (article.getAuthorId() == null) {
            article.setAuthorId(LoginHelper.getUserId());
        }
        if (!StringUtils.hasText(article.getAuthorName())) {
            String nickname = LoginHelper.getNickname();
            article.setAuthorName(StringUtils.hasText(nickname) ? nickname : LoginHelper.getUsername());
        }
    }

    private void ensureSlugUnique(Long siteId, String slug, Long excludeId) {
        if (!StringUtils.hasText(slug)) {
            return;
        }
        // 唯一性口径包含回收站：回收中的 slug 不被新文章占用，恢复时才不会撞车。
        long count = articleMapper.countBySlugIncludingDeleted(siteId, slug.trim(), excludeId);
        if (count > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "文章 slug 已存在：" + slug);
        }
    }

    /**
     * 写一条内容快照。
     *
     * <p>哈希包含状态与标签：状态从草稿变已发布也算一次不同快照；
     * 内容完全没变的普通编辑会被相邻哈希去重掉。版本号按文章自增，
     * 超出 {@code lx.cms.version-keep} 后删最旧的。</p>
     */
    private void snapshot(CmsArticle article, String changeType) {
        Long articleId = article.getArticleId();
        List<Long> tagIds = parseTagIdList(articleTagMapper.selectTagIds(articleId));
        List<Long> sortedTags = new ArrayList<>(new TreeSet<>(tagIds));
        String tagText = joinTagIds(sortedTags);
        String hash = hashSnapshot(article, tagText);
        if (hash.equals(versionMapper.selectLatestHash(articleId))) {
            return;
        }

        int nextNo = versionMapper.selectMaxVersionNo(articleId) + 1;
        CmsArticleVersion version = new CmsArticleVersion();
        version.setArticleId(articleId);
        version.setSiteId(article.getSiteId());
        version.setVersionNo(nextNo);
        version.setChangeType(changeType);
        version.setCategoryId(article.getCategoryId());
        version.setTitle(article.getTitle());
        version.setSlug(article.getSlug());
        version.setSummary(article.getSummary());
        version.setCover(article.getCover());
        version.setContent(article.getContent());
        version.setSource(article.getSource());
        version.setSourceUrl(article.getSourceUrl());
        version.setTagIds(tagText);
        version.setStatus(article.getStatus());
        version.setContentHash(hash);
        version.setOperatorId(currentUserId());
        version.setOperatorName(currentOperatorName());
        version.setCreateTime(LocalDateTime.now());
        versionMapper.insert(version);

        int keep = Math.max(versionKeep, 1);
        int threshold = nextNo - keep;
        if (threshold > 0) {
            versionMapper.deleteUpToVersion(articleId, threshold);
        }
    }

    /** 快照哈希：内容字段 + 标签 + 状态，空值按空串参与计算。 */
    private String hashSnapshot(CmsArticle article, String tagText) {
        StringBuilder payload = new StringBuilder();
        appendHashPart(payload, article.getTitle());
        appendHashPart(payload, article.getSlug());
        appendHashPart(payload, article.getSummary());
        appendHashPart(payload, article.getCover());
        appendHashPart(payload, article.getContent());
        appendHashPart(payload, article.getSource());
        appendHashPart(payload, article.getSourceUrl());
        appendHashPart(payload, article.getCategoryId());
        appendHashPart(payload, tagText);
        appendHashPart(payload, article.getStatus());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[bytes.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xff;
                hex[i * 2] = alphabet[value >>> 4];
                hex[i * 2 + 1] = alphabet[value & 0x0f];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必须提供 SHA-256；真出现说明运行环境异常，直接失败比静默降级安全
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private void appendHashPart(StringBuilder payload, Object value) {
        payload.append(value == null ? "" : value).append('\u0001');
    }

    /**
     * 回滚标签：只恢复仍然存在且属于当前站点的标签。
     * 标签被删不影响正文回滚，但会在日志里记录跳过的数量。
     */
    private void restoreTagsLenient(Long articleId, Long siteId, List<Long> tagIds) {
        articleTagMapper.deleteByArticleId(articleId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(tagIds);
        List<CmsTag> tags = tagMapper.selectBatchIds(distinct);
        List<Long> valid = new ArrayList<>();
        for (CmsTag tag : tags) {
            if (Objects.equals(tag.getSiteId(), siteId)) {
                valid.add(tag.getTagId());
            }
        }
        if (valid.size() < distinct.size()) {
            log.warn("版本回滚跳过 {} 个已删除或跨站点的标签，articleId={}",
                    distinct.size() - valid.size(), articleId);
        }
        if (!valid.isEmpty()) {
            articleTagMapper.insertBatch(articleId, valid);
        }
    }

    private List<Long> parseTagIds(String text) {
        List<Long> result = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        for (String part : text.split(",")) {
            try {
                result.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // 手工改库产生的脏值直接跳过，不让单个坏 ID 拖垮回滚
            }
        }
        return result;
    }

    private List<Long> parseTagIdList(List<Long> tagIds) {
        return tagIds == null ? new ArrayList<>() : tagIds;
    }

    private String joinTagIds(List<Long> tagIds) {
        StringBuilder text = new StringBuilder();
        for (Long tagId : tagIds) {
            if (tagId == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(tagId);
        }
        return text.toString();
    }

    /**
     * 替换文章标签：先删旧关联，再按去重后的新列表插入。
     * 标签必须属于同一站点，防止把 A 站标签挂到 B 站文章上。
     */
    private void replaceTags(Long articleId, Long siteId, List<Long> tagIds) {
        articleTagMapper.deleteByArticleId(articleId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId != null) {
                distinct.add(tagId);
            }
        }
        if (distinct.isEmpty()) {
            return;
        }
        List<CmsTag> tags = tagMapper.selectBatchIds(distinct);
        if (tags.size() != distinct.size()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "存在无效的标签");
        }
        for (CmsTag tag : tags) {
            if (!Objects.equals(tag.getSiteId(), siteId)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "标签不属于当前站点");
            }
        }
        articleTagMapper.insertBatch(articleId, new ArrayList<>(distinct));
    }
}
