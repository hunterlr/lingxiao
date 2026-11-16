<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="菜单名称">
          <el-input
            v-model="query.menuName"
            placeholder="按名称搜索"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <DictSelect v-model="query.status" dict-type="sys_common_status" width="130px" placeholder="全部状态" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            目录（M）只做分组，菜单（C）对应页面，按钮（F）只提供权限码
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:menu:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd(null)"
          >
            新增目录 / 菜单
          </el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        row-key="menuId"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="200">
          <template #default="{ row }">
            <span class="menu-name-cell">
              <AppIcon v-if="row.icon" :name="row.icon" :size="15" />
              <span>{{ row.menuName }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="menuType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.menuType)" size="small" effect="plain">
              {{ typeLabel(row.menuType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="70" align="center" />
        <el-table-column prop="perms" label="权限码" min-width="190">
          <template #default="{ row }">
            <span v-if="row.perms" class="text-mono">{{ row.perms }}</span>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由" min-width="130">
          <template #default="{ row }">
            <span v-if="row.path" class="text-mono">{{ row.path }}</span>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="component" label="组件" min-width="150">
          <template #default="{ row }">
            <span v-if="row.component" class="text-mono">{{ row.component }}</span>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="visible" label="显示" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.visible === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.visible === '0' ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <DictTag :value="row.status" dict-type="sys_common_status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-if="row.menuType !== 'F'"
                v-hasPerm="'platform:system:menu:add'"
                link
                type="primary"
                @click="openAdd(row)"
              >
                新增下级
              </el-button>
              <el-button
                v-hasPerm="'platform:system:menu:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPerm="'platform:system:menu:remove'"
                link
                type="danger"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑菜单' : '新增菜单'"
      width="720px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        v-loading="dialogLoading"
        :model="form"
        :rules="rules"
        label-width="96px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上级菜单" prop="parentId">
              <el-tree-select
                v-model="form.parentId"
                :data="parentOptions"
                node-key="id"
                :props="{ label: 'label', children: 'children' }"
                check-strictly
                placeholder="请选择上级"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group v-model="form.menuType">
                <el-radio-button value="M">目录</el-radio-button>
                <el-radio-button value="C">菜单</el-radio-button>
                <el-radio-button value="F">按钮</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="form.menuName" maxlength="64" placeholder="显示名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单标识" prop="menuKey">
              <el-input v-model="form.menuKey" maxlength="128" placeholder="如 system:user:list" />
            </el-form-item>
          </el-col>

          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item label="路由地址" prop="path">
              <el-input v-model="form.path" maxlength="255" placeholder="如 /system/user" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item label="组件路径" prop="component">
              <el-input v-model="form.component" maxlength="255" placeholder="如 system/user/index" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'M'" :span="12">
            <el-form-item label="权限码" prop="perms">
              <el-input
                v-model="form.perms"
                maxlength="128"
                placeholder="如 platform:system:user:add"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图标">
              <el-input v-model="form.icon" maxlength="64" placeholder="Element Plus 图标名 / 短名" />
              <div class="form-tip">支持 setting / user / picture 等短名，也支持 Setting 这类图标名。</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="orderNum">
              <el-input-number v-model="form.orderNum" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item label="显示状态">
              <el-radio-group v-model="form.visible">
                <el-radio value="0">显示</el-radio>
                <el-radio value="1">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单状态">
              <el-radio-group v-model="form.status">
                <el-radio value="0">正常</el-radio>
                <el-radio value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属模块">
              <el-input v-model="form.moduleCode" maxlength="32" placeholder="system / file / cms" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import AppIcon from '@/components/AppIcon.vue'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import { addMenu, deleteMenu, getMenu, getMenuTree, listMenus, updateMenu } from '@/api/menu'
import { listToTree, collectIds } from '@/utils/tree'

/**
 * 菜单管理。
 *
 * <p>菜单表同时承担“导航配置 + 权限码字典”两个职责，所以新增时要先选类型：
 * 目录（M）只填路由；菜单（C）还要填组件路径与页面权限码；
 * 按钮（F）只填权限码，不参与导航。</p>
 *
 * <p>编辑时会把“自己及后代”从上级候选里剔除，防止把节点挂到自己的子树下。</p>
 */
const loading = ref(false)
const rows = ref([])
const query = reactive({ menuName: '', status: '' })

const menuTree = ref([])

function buildTree(list) {
  return listToTree(list, {
    idKey: 'menuId',
    parentKey: 'parentId',
    childrenKey: 'children',
    rootValue: 0
  })
}

async function loadList() {
  loading.value = true
  try {
    const list = await listMenus({
      menuName: query.menuName || undefined,
      status: query.status || undefined
    })
    rows.value = buildTree(list || [])
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadList()
}

function handleReset() {
  query.menuName = ''
  query.status = ''
  loadList()
}

function typeLabel(type) {
  if (type === 'M') return '目录'
  if (type === 'C') return '菜单'
  return '按钮'
}

function typeTag(type) {
  if (type === 'M') return 'primary'
  if (type === 'C') return 'success'
  return 'info'
}

onMounted(async () => {
  loadList()
  menuTree.value = (await getMenuTree(true).catch(() => [])) || []
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  parentId: '0',
  menuType: 'C',
  menuName: '',
  menuKey: '',
  path: '',
  component: '',
  perms: '',
  icon: '',
  orderNum: 0,
  visible: '0',
  status: '0',
  moduleCode: 'system',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuKey: [{ required: true, message: '请输入菜单标识', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [
    {
      validator: (rule, value, callback) => {
        if (form.menuType === 'F') return callback()
        if (!value) return callback(new Error('目录与菜单必须填写路由地址'))
        if (!value.startsWith('/')) return callback(new Error('路由地址必须以 / 开头'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  component: [
    {
      validator: (rule, value, callback) => {
        if (form.menuType !== 'C') return callback()
        if (!value) return callback(new Error('菜单必须填写组件路径'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  perms: [
    {
      validator: (rule, value, callback) => {
        if (form.menuType === 'F' && !value) return callback(new Error('按钮必须填写权限码'))
        if (value && !/^[a-zA-Z0-9_:*.-]+$/.test(value)) {
          return callback(new Error('权限码只能包含字母、数字、冒号、下划线、点、短横线'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

/** 上级候选：根目录 + 目录/菜单树；编辑时剔除自身及后代。 */
const parentOptions = computed(() => {
  const selfAndDescendants = new Set()

  if (isEdit.value && editingId.value) {
    const findSelf = (nodes) => {
      for (const node of nodes || []) {
        if (String(node.id) === String(editingId.value)) {
          collectIds(node).forEach((id) => selfAndDescendants.add(String(id)))
          return true
        }
        if (findSelf(node.children)) return true
      }
      return false
    }
    findSelf(menuTree.value)
  }

  const prune = (nodes) =>
    (nodes || [])
      .filter((node) => !selfAndDescendants.has(String(node.id)))
      .map((node) => ({ ...node, children: prune(node.children) }))

  return [{ id: '0', label: '根目录', children: prune(menuTree.value) }]
})

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
}

function openAdd(parent) {
  resetForm()
  if (parent) {
    form.parentId = String(parent.menuId)
    form.moduleCode = parent.moduleCode || 'system'
    if (parent.menuType === 'C') {
      // 菜单下只能挂按钮
      form.menuType = 'F'
    }
  }
  dialogVisible.value = true
}

async function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.menuId
  Object.assign(form, {
    parentId: String(row.parentId || '0'),
    menuType: row.menuType,
    menuName: row.menuName,
    menuKey: row.menuKey,
    path: row.path || '',
    component: row.component || '',
    perms: row.perms || '',
    icon: row.icon || '',
    orderNum: row.orderNum ?? 0,
    visible: row.visible || '0',
    status: row.status || '0',
    moduleCode: row.moduleCode || 'system',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getMenu(row.menuId)
    Object.assign(form, {
      parentId: String(detail.parentId || '0'),
      menuType: detail.menuType,
      menuName: detail.menuName,
      menuKey: detail.menuKey,
      path: detail.path || '',
      component: detail.component || '',
      perms: detail.perms || '',
      icon: detail.icon || '',
      orderNum: detail.orderNum ?? 0,
      visible: detail.visible || '0',
      status: detail.status || '0',
      moduleCode: detail.moduleCode || 'system',
      remark: detail.remark || ''
    })
  } finally {
    dialogLoading.value = false
  }
}

function handleDialogClosed() {
  formRef.value && formRef.value.clearValidate()
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        parentId: form.parentId || '0',
        menuType: form.menuType,
        menuName: form.menuName.trim(),
        menuKey: form.menuKey.trim(),
        path: form.menuType === 'F' ? null : form.path || null,
        component: form.menuType === 'C' ? form.component || null : null,
        perms: form.menuType === 'M' ? null : form.perms || null,
        icon: form.menuType === 'F' ? null : form.icon || null,
        orderNum: form.orderNum,
        visible: form.menuType === 'F' ? '0' : form.visible,
        status: form.status,
        moduleCode: form.moduleCode || 'system',
        remark: form.remark || null
      }
      if (!isEdit.value) {
        await addMenu(payload)
        ElMessage.success('新增成功')
      } else {
        await updateMenu(editingId.value, payload)
        ElMessage.success('保存成功')
      }
      dialogVisible.value = false
      await loadList()
      menuTree.value = (await getMenuTree(true).catch(() => [])) || []
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.menuName}」吗？如果它还有下级菜单或已被角色引用，服务端会拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteMenu(row.menuId)
  ElMessage.success('删除成功')
  await loadList()
  menuTree.value = (await getMenuTree(true).catch(() => [])) || []
}
</script>

<style scoped>
.menu-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
