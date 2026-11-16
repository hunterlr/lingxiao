/**
 * 树工具：后端返回的部门 / 菜单树、平铺列表都走这里转换成前端结构。
 *
 * <p>ID 是字符串（后端 Jackson 全局把 Long 转成 String），
 * 所有比较一律用 {@code String(a) === String(b)}，不要用 {@code ===} 比数字。</p>
 */

/** 平铺列表转树；父节点不在列表里的节点作为根返回。 */
export function listToTree(list, options = {}) {
  const { idKey = 'id', parentKey = 'parentId', childrenKey = 'children', rootValue = '0' } = options
  const nodes = new Map()
  const roots = []

  for (const item of list || []) {
    nodes.set(String(item[idKey]), { ...item, [childrenKey]: [] })
  }

  for (const item of list || []) {
    const node = nodes.get(String(item[idKey]))
    const parentId = String(item[parentKey])
    const parent = nodes.get(parentId)
    if (parent && parent !== node && parentId !== String(rootValue)) {
      parent[childrenKey].push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}

/** 深度优先查找从根到目标节点的路径（菜单高亮 / 面包屑用）。 */
export function findTrail(nodes, predicate, trail = []) {
  for (const node of nodes || []) {
    const current = [...trail, node]
    if (predicate(node)) {
      return current
    }
    if (node.children && node.children.length) {
      const found = findTrail(node.children, predicate, current)
      if (found) {
        return found
      }
    }
  }
  return null
}

/** 收集某个节点及其全部后代 ID。 */
export function collectIds(node, idKey = 'id') {
  const ids = []
  const walk = (item) => {
    if (!item) return
    ids.push(String(item[idKey]))
    ;(item.children || []).forEach(walk)
  }
  walk(node)
  return ids
}

/** 过滤树：保留满足条件的节点及其祖先（菜单搜索用）。 */
export function filterTree(nodes, predicate) {
  const result = []
  for (const node of nodes || []) {
    const children = filterTree(node.children, predicate)
    if (predicate(node) || children.length) {
      result.push({ ...node, children })
    }
  }
  return result
}
