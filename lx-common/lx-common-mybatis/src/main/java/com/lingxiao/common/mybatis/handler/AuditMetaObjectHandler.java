package com.lingxiao.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 *
 * <p>只填时间，不填人：common-mybatis 不应该依赖 security 模块。
 * 创建人 / 修改人由业务 Service 在写入前显式设置，或在后续引入
 * {@code AuditUserProvider} 接口后统一填充。</p>
 *
 * <p>为什么用应用时间而不是数据库 {@code now()}：
 * 多库兼容、时区一致、单元测试可预测；数据库只负责存储。</p>
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
