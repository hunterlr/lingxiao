<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="部门名称">
          <el-input
            v-model="query.deptName"
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
            部门层级由闭包表维护：移动部门时后端会重建整棵子树，前端只传新的 parentId
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:dept:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd(null)"
          >
            新增部门
          </el-button>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        row-key="deptId"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="deptName" label="部门名称" min-width="200" />
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column prop="leader" label="负责人" min-width="110">
          <template #default="{ row }">{{ row.leader || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" min-width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <DictTag :value="row.status" dict-type="sys_common_status" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:dept:add'"
                link
                type="primary"
                @click="openAdd(row)"
              >
                新增下级
              </el-button>
              <el-button
                v-hasPerm="'platform:system:dept:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPerm="'platform:system:dept:remove'"
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
      :title="isEdit ? '编辑部门' : '新增部门'"
      width="640px"
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
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            check-strictly
            placeholder="请选择上级部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门名称" prop="deptName">
              <el-input v-model="form.deptName" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="orderNum">
              <el-input-number v-model="form.orderNum" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="form.leader" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <DictSelect v-model="form.status" dict-type="sys_common_status" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import { addDept, deleteDept, getDept, getDeptTree, listDepts, updateDept } from '@/api/dept'
import { listToTree, collectIds } from '@/utils/tree'

/**
 * 部门管理。
 *
 * <p>展示用树表格，新增 / 编辑时用树选择器选上级。
 * 删除受后端约束：有子部门或仍有用户挂在该部门下会拒绝，
 * 前端不提前猜，把服务端提示原样展示。</p>
 */
const loading = ref(false)
const rows = ref([])
const query = reactive({ deptName: '', status: '' })

const deptTree = ref([])

async function loadData() {
  loading.value = true
  try {
    const [list, tree] = await Promise.all([
      listDepts({ deptName: query.deptName || undefined, status: query.status || undefined }),
      getDeptTree().catch(() => [])
    ])
    rows.value = listToTree(list || [], {
      idKey: 'deptId',
      parentKey: 'parentId',
      rootValue: 0
    })
    deptTree.value = tree || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  query.deptName = ''
  query.status = ''
  loadData()
}

onMounted(loadData)

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  parentId: '0',
  deptName: '',
  orderNum: 0,
  leader: '',
  phone: '',
  email: '',
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  parentId: [{ required: true, message: '请选择上级部门', trigger: 'change' }],
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

/** 编辑时不允许把部门挂到自己或自己的后代下（会成环）。 */
const parentOptions = computed(() => {
  const excluded = new Set()
  if (isEdit.value && editingId.value) {
    const findSelf = (nodes) => {
      for (const node of nodes || []) {
        if (String(node.id) === String(editingId.value)) {
          collectIds(node).forEach((id) => excluded.add(String(id)))
          return true
        }
        if (findSelf(node.children)) return true
      }
      return false
    }
    findSelf(deptTree.value)
  }

  const prune = (nodes) =>
    (nodes || [])
      .filter((node) => !excluded.has(String(node.id)))
      .map((node) => ({ ...node, children: prune(node.children) }))

  return [{ id: '0', label: '顶级部门', children: prune(deptTree.value) }]
})

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
}

function openAdd(parent) {
  resetForm()
  if (parent) {
    form.parentId = String(parent.deptId)
  }
  dialogVisible.value = true
}

async function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.deptId
  Object.assign(form, {
    parentId: String(row.parentId || '0'),
    deptName: row.deptName,
    orderNum: row.orderNum ?? 0,
    leader: row.leader || '',
    phone: row.phone || '',
    email: row.email || '',
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getDept(row.deptId)
    Object.assign(form, {
      parentId: String(detail.parentId || '0'),
      deptName: detail.deptName,
      orderNum: detail.orderNum ?? 0,
      leader: detail.leader || '',
      phone: detail.phone || '',
      email: detail.email || '',
      status: detail.status || '0',
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
        deptName: form.deptName.trim(),
        orderNum: form.orderNum,
        leader: form.leader || null,
        phone: form.phone || null,
        email: form.email || null,
        status: form.status,
        remark: form.remark || null
      }
      if (!isEdit.value) {
        await addDept(payload)
        ElMessage.success('新增成功')
      } else {
        await updateDept(editingId.value, payload)
        ElMessage.success('保存成功（如调整了上级，子树层级已同步重建）')
      }
      dialogVisible.value = false
      await loadData()
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除部门「${row.deptName}」吗？存在子部门或部门下仍有用户时，服务端会拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteDept(row.deptId)
  ElMessage.success('删除成功')
  await loadData()
}
</script>
