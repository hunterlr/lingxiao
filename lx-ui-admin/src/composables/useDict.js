import { reactive } from 'vue'
import { listDictDataOptions } from '@/api/dict'

/**
 * 字典缓存。
 *
 * <p>菜单里的状态、性别等下拉项都来自 sys_dict_data。字典是低频变更数据，
 * 前端按类型缓存，避免每个页面挂载都打一次接口；需要强刷时调用
 * {@code loadDict(type, true)}。</p>
 *
 * <p>缓存放在模块作用域而不是 Pinia：它没有“业务状态”语义，
 * 只是接口结果的短期复用。</p>
 */
const cache = reactive({})
const pending = {}

export function loadDict(dictType, force = false) {
  if (!dictType) return Promise.resolve([])
  if (cache[dictType] && !force) return Promise.resolve(cache[dictType])
  if (pending[dictType]) return pending[dictType]

  pending[dictType] = listDictDataOptions(dictType)
    .then((list) => {
      cache[dictType] = list || []
      return cache[dictType]
    })
    .catch(() => [])
    .finally(() => {
      delete pending[dictType]
    })

  return pending[dictType]
}

/**
 * 页面用法：
 * <pre>
 *   const dict = useDict('sys_common_status', 'sys_user_sex')
 *   // 模板里直接 dict.sys_common_status（数组）
 * </pre>
 */
export function useDict(...types) {
  const dict = reactive({})
  for (const type of types) {
    dict[type] = cache[type] || []
    if (!cache[type]) {
      loadDict(type).then((list) => {
        dict[type] = list
      })
    }
  }
  return dict
}
