import { useAuthStore } from '@/store/auth'

/**
 * 按钮权限指令。
 *
 * <p>用法：</p>
 * <pre>
 *   &lt;el-button v-hasPerm="'platform:system:user:add'"&gt;新增&lt;/el-button&gt;
 *   &lt;el-button v-hasPerm="['platform:system:user:edit', 'platform:system:user:add']"&gt;保存&lt;/el-button&gt;
 * </pre>
 *
 * <p>数组表示“任一命中即可”。超管权限 {@code *:*:*} 直接放行。</p>
 *
 * <p>只做“看得见 / 看不见”，不是安全边界：真正的鉴权在后端
 * {@code @RequiresPermission}，前端隐藏按钮只是减少误操作。</p>
 */
export const permission = {
  mounted(el, binding) {
    const auth = useAuthStore()
    if (!auth.hasPermission(binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
