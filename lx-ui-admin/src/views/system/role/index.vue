<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="角色名称">
          <el-input
            v-model="query.roleName"
            placeholder="角色名称"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色标识">
          <el-input
            v-model="query.roleKey"
            placeholder="如 admin"
            clearable
            style="width: 160px"
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
          <span class="text-secondary">共 {{ total }} 个角色</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:role:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            新增角色
          </el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="roleId">
        <el-table-column prop="roleName" label="角色名称" min-width="150">
          <template #default="{ row }">
            {{ row.roleName }}
            <el-tag v-if="isBuiltin(row)" size="small" type="warning" style="margin-left: 6px">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleKey" label="角色标识" min-width="130">
          <template #default="{ row }">
            <span class="text-mono">{{ row.roleKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dataScope" label="数据范围" width="140">
          <template #default="{ row }">{{ dataScopeLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <DictTag :value="row.status" dict-type="sys_common_status" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:role:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑 / 授权
              </el-button>
              <el-button
                v-hasPerm="'platform:system:role:remove'"
                link
                type="danger"
                :disabled="isBuiltin(row)"
                @click="handleDelete(row)"
              >
                删除
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
      :title="isEdit ? '编辑角色 / 菜单授权' : '新增角色'"
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
            <el-form-item label="角色名称" prop="roleName">
              <el-input v-model="form.roleName" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色标识" prop="roleKey">
              <el-input
                v-model="form.roleKey"
                maxlength="64"
                :disabled="isBuiltinRole"
                placeholder="如 operator"
              />
              <div v-if="isBuiltinRole" class="form-tip">内置角色不允许修改标识。</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sort">
              <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据范围" prop="dataScope">
              <el-select v-model="form.dataScope" style="width: 100%">
                <el-option
                  v-for="item in dataScopeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                  :disabled="item.disabled"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <DictSelect
                v-model="form.status"
                dict-type="sys_common_status"
                :disabled="isBuiltinRole"
              />
              <div v-if="isBuiltinRole" class="form-tip">内置超级管理员不允许停用。</div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="菜单权限">
              <div class="menu-tree-box">
                <div class="menu-tree-toolbar">
                  <span class="text-secondary">
                    勾选该角色可见的菜单与可用的按钮权限；目录不会单独生效。
                  </span>
                  <div>
                    <el-button link type="primary" @click="toggleAll(true)">全选</el-button>
                    <el-button link type="primary" @click="toggleAll(false)">清空</el-button>
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
                    empty-text="暂无菜单数据"
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
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import { addRole, deleteRole, getRole, listRoles, updateRole } from '@/api/role'
import { getMenuTree } from '@/api/menu'

/**
 * 角色管理。
 *
 * <p>“保存角色”与“菜单授权”是一个动作：请求体里带 {@code menuIds}，
 * 服务端先清空旧授权再重建。前端不做单独的授权接口，避免两个入口
 * 出现“角色改了但权限没保存”的半成品状态。</p>
 *
 * <p>菜单树用 {@code check-strictly}（父子不联动）：
 * 角色可能只被授权某个子菜单而不包含父目录，联动勾选会强行放大权限。</p>
 */
const dataScopeOptions = [
  { value: '1', label: '全部数据' },
  { value: '2', label: '自定义数据（未启用）', disabled: true },
  { value: '3', label: '本部门数据' },
  { value: '4', label: '本部门及以下' },
  { value: '5', label: '仅本人数据' }
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, roleName: '', roleKey: '', status: '' })

const menuTree = ref([])

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
  if (type === 'M') return '目录'
  if (type === 'C') return '菜单'
  return '按钮'
}

onMounted(async () => {
  loadList()
  menuTree.value = (await getMenuTree(false).catch(() => [])) || []
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)
const menuTreeRef = ref(null)
const isBuiltinRole = computed(() => isEdit.value && String(editingId.value) === '1')

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
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_:-]*$/,
      message: '以字母开头，只能包含字母、数字、下划线、冒号、短横线',
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
    await nextTick()
    const ids = (detail.menuIds || []).map((id) => String(id))
    menuTreeRef.value && menuTreeRef.value.setCheckedKeys(ids)
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

function handleDialogClosed() {
  formRef.value && formRef.value.clearValidate()
  menuTreeRef.value && menuTreeRef.value.setCheckedKeys([])
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
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
        menuIds
      }
      if (!isEdit.value) {
        await addRole(payload)
        ElMessage.success('新增成功')
      } else {
        await updateRole(editingId.value, payload)
        ElMessage.success('保存成功')
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
      `确认删除角色「${row.roleName}」吗？删除后已关联该角色的用户将失去对应权限。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteRole(row.roleId)
  ElMessage.success('删除成功')
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
