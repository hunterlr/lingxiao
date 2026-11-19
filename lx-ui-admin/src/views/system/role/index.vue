<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.role.roleName')">
          <el-input
            v-model="query.roleName"
            :placeholder="t('page.role.namePlaceholder')"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.role.roleKey')">
          <el-input
            v-model="query.roleKey"
            :placeholder="t('page.role.keyPlaceholder')"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <DictSelect v-model="query.status" dict-type="sys_common_status" width="130px" :placeholder="t('common.allStatus')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.role.total', { total }) }}</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:role:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            {{ t('page.role.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="roleId">
        <el-table-column prop="roleName" :label="t('page.role.roleName')" min-width="150">
          <template #default="{ row }">
            {{ row.roleName }}
            <el-tag v-if="isBuiltin(row)" size="small" type="warning" style="margin-left: 6px">{{ t('page.role.builtin') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleKey" :label="t('page.role.roleKey')" min-width="130">
          <template #default="{ row }">
            <span class="text-mono">{{ row.roleKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dataScope" :label="t('page.role.dataScope')" width="140">
          <template #default="{ row }">{{ dataScopeLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="80" align="center" />
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <DictTag :value="row.status" dict-type="sys_common_status" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('common.remark')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="200" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:role:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('page.role.editGrant') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:system:role:remove'"
                link
                type="danger"
                :disabled="isBuiltin(row)"
                @click="handleDelete(row)"
              >
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.role.edit') : t('page.role.add')"
      width="760px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        v-loading="dialogLoading"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('page.role.roleName')" prop="roleName">
              <el-input v-model="form.roleName" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.role.roleKey')" prop="roleKey">
              <el-input
                v-model="form.roleKey"
                maxlength="64"
                :disabled="isBuiltinRole"
                :placeholder="t('page.role.keyPlaceholderEdit')"
              />
              <div v-if="isBuiltinRole" class="form-tip">{{ t('page.role.builtinKeyTip') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.sort')" prop="sort">
              <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.role.dataScope')" prop="dataScope">
              <el-select v-model="form.dataScope" style="width: 100%">
                <el-option
                  v-for="item in dataScopeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.status')">
              <DictSelect
                v-model="form.status"
                dict-type="sys_common_status"
                :disabled="isBuiltinRole"
              />
              <div v-if="isBuiltinRole" class="form-tip">{{ t('page.role.builtinDisableTip') }}</div>
            </el-form-item>
          </el-col>
          <el-col v-if="showCustomDept" :span="24">
            <el-form-item :label="t('page.role.customDept')">
              <div class="menu-tree-box">
                <div class="menu-tree-toolbar">
                  <span class="text-secondary">{{ t('page.role.customDeptTip') }}</span>
                  <div>
                    <el-button link type="primary" @click="toggleDeptAll(true)">{{ t('page.role.selectAll') }}</el-button>
                    <el-button link type="primary" @click="toggleDeptAll(false)">{{ t('page.role.clearAll') }}</el-button>
                  </div>
                </div>
                <el-scrollbar height="220px">
                  <el-tree
                    ref="deptTreeRef"
                    :data="deptTree"
                    node-key="id"
                    show-checkbox
                    check-strictly
                    default-expand-all
                    :props="{ label: 'label', children: 'children' }"
                    :empty-text="t('page.role.emptyDept')"
                  />
                </el-scrollbar>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('common.remark')">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.role.menuPerm')">
              <div class="menu-tree-box">
                <div class="menu-tree-toolbar">
                  <span class="text-secondary">
                    {{ t('page.role.menuPermTip') }}
                  </span>
                  <div>
                    <el-button link type="primary" @click="toggleAll(true)">{{ t('page.role.selectAll') }}</el-button>
                    <el-button link type="primary" @click="toggleAll(false)">{{ t('page.role.clearAll') }}</el-button>
                  </div>
                </div>
                <el-scrollbar height="280px">
                  <el-tree
                    ref="menuTreeRef"
                    :data="menuTree"
                    node-key="id"
                    show-checkbox
                    check-strictly
                    default-expand-all
                    :props="{ label: 'label', children: 'children' }"
                    :empty-text="t('page.role.emptyMenu')"
                  >
                    <template #default="{ data }">
                      <span class="tree-node">
                        <el-tag
                          size="small"
                          :type="typeTag(data.type)"
                          effect="plain"
                          class="tree-type"
                        >
                          {{ typeLabel(data.type) }}
                        </el-tag>
                        <span>{{ data.label }}</span>
                        <span v-if="data.perms" class="text-mono tree-perm">{{ data.perms }}</span>
                      </span>
                    </template>
                  </el-tree>
                </el-scrollbar>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import { addRole, deleteRole, getRole, listRoles, updateRole } from '@/api/role'
import { getMenuTree } from '@/api/menu'
import { getDeptTree } from '@/api/dept'
import { useI18n } from 'vue-i18n'

/**
 * 角色管理。
 *
 * <p>“保存角色”与“菜单授权”是一个动作：请求体里带 {@code menuIds}，
 * 服务端先清空旧授权再重建。前端不做单独的授权接口，避免两个入口
 * 出现“角色改了但权限没保存”的半成品状态。</p>
 *
 * <p>菜单树用 {@code check-strictly}（父子不联动）：
 * 角色可能只被授权某个子菜单而不包含父目录，联动勾选会强行放大权限。</p>
 *
 * <p>数据范围选“自定义”时额外提交 {@code deptIds}：部门树同样用
 * {@code check-strictly}，因为自定义范围是精确集合，勾父部门不等于勾下级。</p>
 */
const { t } = useI18n()

const dataScopeOptions = [
  { value: '1', label: t('page.role.scopeAll') },
  { value: '2', label: t('page.role.scopeCustom') },
  { value: '3', label: t('page.role.scopeDept') },
  { value: '4', label: t('page.role.scopeDeptChild') },
  { value: '5', label: t('page.role.scopeSelf') }
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, roleName: '', roleKey: '', status: '' })

const menuTree = ref([])
const deptTree = ref([])

async function loadList() {
  loading.value = true
  try {
    const data = await listRoles({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      roleName: query.roleName || undefined,
      roleKey: query.roleKey || undefined,
      status: query.status || undefined
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.roleName = ''
  query.roleKey = ''
  query.status = ''
  handleSearch()
}

function dataScopeLabel(value) {
  const hit = dataScopeOptions.find((item) => item.value === String(value))
  return hit ? hit.label : value || '-'
}

function isBuiltin(row) {
  return String(row.roleId) === '1'
}

function typeTag(type) {
  if (type === 'M') return 'primary'
  if (type === 'C') return 'success'
  return 'info'
}

function typeLabel(type) {
  if (type === 'M') return t('page.role.typeDir')
  if (type === 'C') return t('page.role.typeMenu')
  return t('page.role.typeButton')
}

onMounted(async () => {
  loadList()
  const [menus, depts] = await Promise.all([
    getMenuTree(false).catch(() => []),
    getDeptTree().catch(() => [])
  ])
  menuTree.value = menus || []
  deptTree.value = depts || []
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)
const menuTreeRef = ref(null)
const deptTreeRef = ref(null)
const isBuiltinRole = computed(() => isEdit.value && String(editingId.value) === '1')
const showCustomDept = computed(() => form.dataScope === '2')

const emptyForm = () => ({
  roleName: '',
  roleKey: '',
  sort: 0,
  dataScope: '1',
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  roleName: [{ required: true, message: t('page.role.nameRequired'), trigger: 'blur' }],
  roleKey: [
    { required: true, message: t('page.role.keyRequired'), trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_:-]*$/,
      message: t('page.role.keyPattern'),
      trigger: 'blur'
    }
  ]
}

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
}

function openAdd() {
  resetForm()
  dialogVisible.value = true
  nextTick(() => {
    menuTreeRef.value && menuTreeRef.value.setCheckedKeys([])
    deptTreeRef.value && deptTreeRef.value.setCheckedKeys([])
  })
}

async function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.roleId
  Object.assign(form, {
    roleName: row.roleName,
    roleKey: row.roleKey,
    sort: row.sort ?? 0,
    dataScope: String(row.dataScope || '1'),
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getRole(row.roleId)
    Object.assign(form, {
      roleName: detail.roleName,
      roleKey: detail.roleKey,
      sort: detail.sort ?? 0,
      dataScope: String(detail.dataScope || '1'),
      status: detail.status || '0',
      remark: detail.remark || ''
    })
    if (!deptTree.value.length) {
      deptTree.value = (await getDeptTree().catch(() => [])) || []
    }
    await nextTick()
    const ids = (detail.menuIds || []).map((id) => String(id))
    menuTreeRef.value && menuTreeRef.value.setCheckedKeys(ids)
    const deptIds = (detail.deptIds || []).map((id) => String(id))
    deptTreeRef.value && deptTreeRef.value.setCheckedKeys(deptIds)
  } finally {
    dialogLoading.value = false
  }
}

function toggleAll(checked) {
  if (!menuTreeRef.value) return
  if (!checked) {
    menuTreeRef.value.setCheckedKeys([])
    return
  }
  const allIds = []
  const walk = (nodes) => {
    for (const node of nodes || []) {
      allIds.push(String(node.id))
      if (node.children) walk(node.children)
    }
  }
  walk(menuTree.value)
  menuTreeRef.value.setCheckedKeys(allIds)
}

function toggleDeptAll(checked) {
  if (!deptTreeRef.value) return
  if (!checked) {
    deptTreeRef.value.setCheckedKeys([])
    return
  }
  const allIds = []
  const walk = (nodes) => {
    for (const node of nodes || []) {
      allIds.push(String(node.id))
      if (node.children) walk(node.children)
    }
  }
  walk(deptTree.value)
  deptTreeRef.value.setCheckedKeys(allIds)
}

function handleDialogClosed() {
  formRef.value && formRef.value.clearValidate()
  menuTreeRef.value && menuTreeRef.value.setCheckedKeys([])
  deptTreeRef.value && deptTreeRef.value.setCheckedKeys([])
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const deptIds = form.dataScope === '2'
      ? (deptTreeRef.value ? deptTreeRef.value.getCheckedKeys().map((id) => String(id)) : [])
      : null
    if (form.dataScope === '2' && deptIds.length === 0) {
      ElMessage.warning(t('page.role.customDeptRequired'))
      return
    }
    saving.value = true
    try {
      const menuIds = menuTreeRef.value ? menuTreeRef.value.getCheckedKeys() : []
      const payload = {
        roleName: form.roleName.trim(),
        roleKey: form.roleKey.trim(),
        sort: form.sort,
        dataScope: form.dataScope,
        status: form.status,
        remark: form.remark || null,
        menuIds,
        deptIds
      }
      if (!isEdit.value) {
        await addRole(payload)
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updateRole(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.role.deleteConfirm', { name: row.roleName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteRole(row.roleId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>

<style scoped>
.menu-tree-box {
  width: 100%;
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  padding: 8px 12px;
  background: var(--lx-panel-muted);
}

.menu-tree-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 6px;
  border-bottom: 1px dashed var(--lx-border);
  margin-bottom: 6px;
  font-size: 12px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.tree-type {
  transform: scale(0.9);
}

.tree-perm {
  font-size: 11px;
  opacity: 0.7;
}
</style>
