package com.lingxiao.modules.generator.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.generator.dto.DbTableVO;
import com.lingxiao.modules.generator.dto.GenPreviewVO;
import com.lingxiao.modules.generator.dto.ImportResultVO;
import com.lingxiao.modules.generator.dto.ImportTableRequest;
import com.lingxiao.modules.generator.entity.GenTable;
import com.lingxiao.modules.generator.entity.GenTableColumn;
import com.lingxiao.modules.generator.service.CodeGenService;
import com.lingxiao.modules.generator.service.GenTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 代码生成器接口。
 *
 * <p>路径挂在 {@code /api/admin/tool/gen} 下，与菜单 22“代码生成”一致；
 * 权限码沿用 V1 已种好的 {@code platform:tool:gen:list}，本轮补 import / edit /
 * remove / preview / download 五个按钮权限。</p>
 *
 * <p>下载接口不写 {@code @Log}：操作日志切面会把返回值序列化进 json_result，
 * ZIP 字节数组会先被 Base64 撑大再截断，纯属浪费内存。下载本身有 Nginx /
 * 浏览器记录，且生成内容已经在预览接口留痕。</p>
 *
 * <p>模块开关关闭时整个 Controller 不装配，接口返回 404。</p>
 */
@RestController
@RequestMapping("/api/admin/tool/gen")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.generator.enabled", havingValue = "true")
public class GenController {

    private final GenTableService genTableService;
    private final CodeGenService codeGenService;

    @RequiresPermission("platform:tool:gen:list")
    @GetMapping("/list")
    public R<PageResult<GenTable>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String tableComment) {
        return R.ok(genTableService.page(pageNum, pageSize, tableName, tableComment));
    }

    /** 数据库里可导入的表；已导入的也会返回，由前端置灰。 */
    @RequiresPermission("platform:tool:gen:list")
    @GetMapping("/db/list")
    public R<List<DbTableVO>> dbList(@RequestParam(required = false) String keyword) {
        return R.ok(genTableService.listCandidateTables(keyword));
    }

    @RequiresPermission("platform:tool:gen:list")
    @GetMapping("/{tableId}")
    public R<GenTable> detail(@PathVariable Long tableId) {
        return R.ok(genTableService.detail(tableId));
    }

    @RequiresPermission("platform:tool:gen:import")
    @Log(title = "代码生成", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public R<ImportResultVO> importTables(@Valid @RequestBody ImportTableRequest request) {
        return R.ok(genTableService.importTables(request));
    }

    @RequiresPermission("platform:tool:gen:edit")
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @PutMapping("/{tableId}")
    public R<Void> update(@PathVariable Long tableId, @RequestBody GenTable table) {
        genTableService.updateTable(tableId, table);
        return R.ok();
    }

    @RequiresPermission("platform:tool:gen:edit")
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @PutMapping("/{tableId}/columns")
    public R<Void> updateColumns(@PathVariable Long tableId,
                                 @RequestBody List<GenTableColumn> columns) {
        genTableService.updateColumns(tableId, columns);
        return R.ok();
    }

    @RequiresPermission("platform:tool:gen:remove")
    @Log(title = "代码生成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tableId}")
    public R<Void> remove(@PathVariable Long tableId) {
        genTableService.delete(tableId);
        return R.ok();
    }

    @RequiresPermission("platform:tool:gen:preview")
    @GetMapping("/preview/{tableId}")
    public R<List<GenPreviewVO>> preview(@PathVariable Long tableId) {
        return R.ok(codeGenService.preview(tableId));
    }

    /**
     * 下载 ZIP。
     *
     * <p>文件名只用业务名（正则校验过），不拼接表注释，避免
     * Content-Disposition 中文编码在不同浏览器上表现不一致。</p>
     */
    @RequiresPermission("platform:tool:gen:download")
    @GetMapping("/download/{tableId}")
    public ResponseEntity<byte[]> download(@PathVariable Long tableId) {
        GenTable table = genTableService.requireTable(tableId);
        byte[] bytes = codeGenService.download(tableId);
        String filename = table.getBusinessName() + "-code.zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }
}
