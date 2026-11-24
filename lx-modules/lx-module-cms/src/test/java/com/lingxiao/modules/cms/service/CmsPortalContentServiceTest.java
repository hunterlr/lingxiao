package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.dto.portal.PortalArticleDetailVO;
import com.lingxiao.modules.cms.dto.portal.PortalBlockVO;
import com.lingxiao.modules.cms.dto.portal.PortalNavVO;
import com.lingxiao.modules.cms.dto.portal.PortalPageVO;
import com.lingxiao.modules.cms.dto.portal.PortalPreviewVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsBlock;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsNav;
import com.lingxiao.modules.cms.entity.CmsNavItem;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.entity.CmsPreviewTicket;
import com.lingxiao.modules.cms.entity.CmsSite;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsBannerMapper;
import com.lingxiao.modules.cms.mapper.CmsBlockMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsNavItemMapper;
import com.lingxiao.modules.cms.mapper.CmsNavMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 门户内容测试：单页可见性、导航失效目标过滤、区块清洗、草稿预览。
 */
@DisplayName("CmsPortalContentService 门户内容")
class CmsPortalContentServiceTest {

    private CmsSiteService siteService;
    private CmsPageMapper pageMapper;
    private CmsNavMapper navMapper;
    private CmsNavItemMapper navItemMapper;
    private CmsBlockMapper blockMapper;
    private CmsBannerMapper bannerMapper;
    private CmsCategoryMapper categoryMapper;
    private CmsArticleMapper articleMapper;
    private CmsArticleTagMapper articleTagMapper;
    private CmsContentExtService extService;
    private CmsPreviewTicketService previewTicketService;
    private CmsPortalContentService service;

    @BeforeEach
    void setUp() {
        siteService = mock(CmsSiteService.class);
        pageMapper = mock(CmsPageMapper.class);
        navMapper = mock(CmsNavMapper.class);
        navItemMapper = mock(CmsNavItemMapper.class);
        blockMapper = mock(CmsBlockMapper.class);
        bannerMapper = mock(CmsBannerMapper.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        articleMapper = mock(CmsArticleMapper.class);
        articleTagMapper = mock(CmsArticleTagMapper.class);
        extService = mock(CmsContentExtService.class);
        previewTicketService = mock(CmsPreviewTicketService.class);
        service = new CmsPortalContentService(siteService, pageMapper, navMapper,
                navItemMapper, blockMapper, bannerMapper, categoryMapper, articleMapper,
                mock(CmsTagMapper.class), articleTagMapper, extService, previewTicketService);

        CmsSite site = new CmsSite();
        site.setSiteId(1L);
        site.setSiteKey("main");
        when(siteService.detailByKey("main")).thenReturn(site);
        when(siteService.mustGet(1L)).thenReturn(site);
    }

    @Test
    @DisplayName("单页：未发布按不存在处理")
    void rejectsDraftPage() {
        when(pageMapper.selectOne(any())).thenReturn(null);

        ServiceException error = catchThrowableOfType(
                () -> service.page("main", "about"), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.page.notfound");
    }

    @Test
    @DisplayName("单页：返回清洗正文与扩展字段")
    void returnsPublishedPage() {
        CmsPage page = new CmsPage();
        page.setPageId(50L);
        page.setSiteId(1L);
        page.setPageKey("about");
        page.setPageName("关于我们");
        page.setContent("<p>介绍</p><script>x</script>");
        when(pageMapper.selectOne(any())).thenReturn(page);
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("contact", "hello@example.com");
        when(extService.load("page", 50L)).thenReturn(ext);

        PortalPageVO vo = service.page("main", "about");

        assertThat(vo.getContent()).contains("<p>介绍</p>").doesNotContain("<script");
        assertThat(vo.getExt()).containsEntry("contact", "hello@example.com");
    }

    @Test
    @DisplayName("导航：目标栏目停用后整项不下发")
    void filtersDisabledCategoryTarget() {
        CmsNav nav = new CmsNav();
        nav.setNavId(1L);
        nav.setSiteId(1L);
        nav.setNavKey("main");
        nav.setNavName("主导航");
        when(navMapper.selectOne(any())).thenReturn(nav);
        CmsNavItem item = new CmsNavItem();
        item.setItemId(10L);
        item.setNavId(1L);
        item.setSiteId(1L);
        item.setParentId(0L);
        item.setItemName("新闻");
        item.setTargetType("category");
        item.setTargetId(9L);
        when(navItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(1L);
        category.setCategoryKey("news");
        category.setStatus("1");
        when(categoryMapper.selectById(9L)).thenReturn(category);

        PortalNavVO vo = service.nav("main", "main");

        assertThat(vo.getItems()).isEmpty();
    }

    @Test
    @DisplayName("导航：栏目目标固定输出 /category/{key}，不直接透出相对 path")
    void resolvesCategoryUrlByStableKey() {
        CmsNav nav = new CmsNav();
        nav.setNavId(1L);
        nav.setSiteId(1L);
        nav.setNavKey("main");
        nav.setNavName("主导航");
        when(navMapper.selectOne(any())).thenReturn(nav);
        CmsNavItem item = new CmsNavItem();
        item.setItemId(10L);
        item.setNavId(1L);
        item.setSiteId(1L);
        item.setParentId(0L);
        item.setItemName("新闻");
        item.setTargetType("category");
        item.setTargetId(9L);
        when(navItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(1L);
        category.setCategoryKey("news");
        category.setPath("news");
        category.setStatus("0");
        when(categoryMapper.selectById(9L)).thenReturn(category);

        PortalNavVO vo = service.nav("main", "main");

        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getUrl()).isEqualTo("/category/news");
    }

    @Test
    @DisplayName("区块：正文清洗后返回")
    void blockSanitizesContent() {
        CmsBlock block = new CmsBlock();
        block.setBlockId(1L);
        block.setSiteId(1L);
        block.setBlockKey("home-hero");
        block.setBlockName("首页推荐");
        block.setBlockType("html");
        block.setContent("<p>推荐</p><script>x</script>");
        when(blockMapper.selectOne(any())).thenReturn(block);

        PortalBlockVO vo = service.block("main", "home-hero");

        assertThat(vo.getContent()).contains("<p>推荐</p>").doesNotContain("<script");
    }

    @Test
    @DisplayName("预览：草稿文章可返回，且不校验发布状态")
    void previewReturnsDraftArticle() {
        CmsPreviewTicket ticket = new CmsPreviewTicket();
        ticket.setSiteId(1L);
        ticket.setTargetType(CmsPreviewTicketService.TARGET_ARTICLE);
        ticket.setTargetId(5L);
        when(previewTicketService.consume("abc")).thenReturn(ticket);
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setSiteId(1L);
        article.setTitle("草稿");
        article.setContent("<p>草稿正文</p>");
        article.setStatus(CmsArticleService.STATUS_DRAFT);
        when(articleMapper.selectById(5L)).thenReturn(article);
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Collections.emptyList());
        when(extService.load("article", 5L)).thenReturn(Collections.emptyMap());

        PortalPreviewVO vo = service.preview("abc");

        assertThat(vo.getTargetType()).isEqualTo("article");
        PortalArticleDetailVO detail = vo.getArticle();
        assertThat(detail.getTitle()).isEqualTo("草稿");
        assertThat(detail.getContent()).contains("草稿正文");
        assertThat(detail.getViewCount()).isZero();
        org.mockito.Mockito.verify(articleMapper, org.mockito.Mockito.never())
                .increaseViewCount(any());
    }

    @Test
    @DisplayName("预览：栏目模板票据返回栏目、模板与最近已发布文章")
    void previewReturnsCategoryTemplate() {
        CmsPreviewTicket ticket = new CmsPreviewTicket();
        ticket.setSiteId(1L);
        ticket.setTargetType(CmsPreviewTicketService.TARGET_CATEGORY);
        ticket.setTargetId(9L);
        ticket.setTemplate("list.news");
        when(previewTicketService.consume("abc")).thenReturn(ticket);

        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(1L);
        category.setCategoryKey("news");
        category.setCategoryName("新闻");
        category.setStatus("0");
        when(categoryMapper.selectById(9L)).thenReturn(category);

        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setSiteId(1L);
        article.setCategoryId(9L);
        article.setTitle("已发布");
        article.setStatus(CmsArticleService.STATUS_PUBLISHED);
        when(articleMapper.selectList(any()))
                .thenReturn(Collections.singletonList(article));
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Collections.emptyList());

        PortalPreviewVO vo = service.preview("abc");

        assertThat(vo.getTargetType()).isEqualTo("category");
        assertThat(vo.getTemplate()).isEqualTo("list.news");
        assertThat(vo.getCategory().getCategoryKey()).isEqualTo("news");
        assertThat(vo.getArticles()).hasSize(1);
        assertThat(vo.getArticles().get(0).getTitle()).isEqualTo("已发布");
        assertThat(vo.getArticles().get(0).getCategoryName()).isEqualTo("新闻");
    }

    @Test
    @DisplayName("预览：文章详情模板由栏目列表模板推导")
    void previewArticleResolvesDetailTemplate() {
        CmsPreviewTicket ticket = new CmsPreviewTicket();
        ticket.setSiteId(1L);
        ticket.setTargetType(CmsPreviewTicketService.TARGET_ARTICLE);
        ticket.setTargetId(5L);
        when(previewTicketService.consume("abc")).thenReturn(ticket);

        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setSiteId(1L);
        article.setCategoryId(9L);
        article.setTitle("图集文章");
        article.setStatus(CmsArticleService.STATUS_DRAFT);
        when(articleMapper.selectById(5L)).thenReturn(article);

        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(1L);
        category.setCategoryKey("gallery");
        category.setTemplate("list.gallery");
        category.setStatus("0");
        when(categoryMapper.selectById(9L)).thenReturn(category);
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Collections.emptyList());
        when(extService.load("article", 5L)).thenReturn(Collections.emptyMap());

        PortalPreviewVO vo = service.preview("abc");

        assertThat(vo.getArticle().getTemplate()).isEqualTo("detail.gallery");
        assertThat(vo.getTemplate()).isEqualTo("detail.gallery");
    }
}
