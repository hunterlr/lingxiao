package com.lingxiao.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>三个插件缺一不可：</p>
 * <ol>
 *   <li><b>分页</b>：所有列表接口统一分页，禁止把全表查出来再内存分页；</li>
 *   <li><b>乐观锁</b>：配合 {@code @Version}，防止并发编辑互相覆盖；</li>
 *   <li><b>防全表更新</b>：拦截没有 where 条件的 update / delete，避免误操作清库。</li>
 * </ol>
 *
 * <p>v1.0 只连 MySQL；后续多库时把 {@link DbType#MYSQL} 换成按数据源动态判断。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
