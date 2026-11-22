import type { PortalCategory } from '~/types/portal'

/** 深度优先展开栏目树。 */
export function flattenCategories(nodes: PortalCategory[] = []): PortalCategory[] {
  const result: PortalCategory[] = []
  for (const node of nodes) {
    result.push(node)
    if (node.children?.length) result.push(...flattenCategories(node.children))
  }
  return result
}

/** 按 categoryKey 查找栏目（含子栏目）。 */
export function findCategory(nodes: PortalCategory[] = [], key: string): PortalCategory | null {
  return flattenCategories(nodes).find((item) => item.categoryKey === key) || null
}

/** 查找从根到目标栏目的路径，用于面包屑。 */
export function findCategoryTrail(nodes: PortalCategory[] = [], key: string): PortalCategory[] {
  for (const node of nodes) {
    if (node.categoryKey === key) return [node]
    if (node.children?.length) {
      const trail = findCategoryTrail(node.children, key)
      if (trail.length) return [node, ...trail]
    }
  }
  return []
}

/** 取第一层栏目，用于首页快捷入口。 */
export function rootCategories(nodes: PortalCategory[] = []): PortalCategory[] {
  return nodes
}
