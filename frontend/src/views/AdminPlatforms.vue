<template>
  <div class="admin-platforms">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程平台管理</span>
          <div class="header-actions">
            <el-radio-group
              v-model="viewMode"
              size="small"
              style="margin-right: 10px"
            >
              <el-radio-button label="list">列表视图</el-radio-button>
              <el-radio-button label="tree">树状视图</el-radio-button>
            </el-radio-group>
            <el-button
              type="primary"
              @click="handleCategoryManagement"
              plain
              style="margin-right: 10px"
            >
              分类管理
            </el-button>
            <el-button type="primary" :icon="Plus" @click="handleCreate">
              添加平台
            </el-button>
            <el-button
              type="success"
              :icon="Download"
              @click="handleImport"
              style="margin-left: 10px"
            >
              一键导入
            </el-button>
          </div>
        </div>
      </template>

      <!-- 树状视图 -->
      <el-tree
        v-if="viewMode === 'tree'"
        lazy
        :load="loadNode"
        :props="treeProps"
        class="platform-tree"
        :expand-on-click-node="false"
      >
        <template #default="{ node, data }">
          <div class="tree-node">
            <div class="node-content">
              <el-icon
                class="node-icon"
                :color="
                  data.isCategory
                    ? 'var(--color-warning)'
                    : 'var(--color-primary)'
                "
              >
                <Folder v-if="data.isCategory" />
                <Monitor v-else />
              </el-icon>

              <span
                class="node-label"
                :class="{
                  'is-category': data.isCategory,
                  'is-offline': !data.isCategory && data.status !== 1,
                }"
              >
                {{ data.name }}
                <span
                  v-if="data.isCategory && data.count !== undefined"
                  class="count-tag"
                >
                  ({{ data.count }})
                </span>
              </span>

              <template v-if="!data.isCategory">
                <span
                  class="status-dot"
                  :class="data.status === 1 ? 'online' : 'offline'"
                ></span>
                <span v-if="data.basePrice" class="price-tag"
                  >¥{{ data.basePrice }}</span
                >
              </template>
            </div>

            <div class="node-actions">
              <!-- 分类节点操作 -->
              <template v-if="data.isCategory">
                <el-tooltip content="快速添加平台" placement="top">
                  <el-button
                    link
                    type="primary"
                    :icon="Plus"
                    @click.stop="handleQuickAdd(data)"
                  />
                </el-tooltip>

                <el-dropdown
                  trigger="click"
                  @command="(cmd) => handleCategoryCommand(cmd, data)"
                >
                  <el-button link type="primary" :icon="More" @click.stop />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit"
                        >编辑分类</el-dropdown-item
                      >
                      <el-dropdown-item
                        command="delete"
                        v-if="data.id !== 'category_0'"
                        >删除分类(保留课程)</el-dropdown-item
                      >
                      <el-dropdown-item command="batchDelete" divided
                        >批量删除课程</el-dropdown-item
                      >
                      <el-dropdown-item
                        command="cascadeDelete"
                        style="color: var(--el-color-danger)"
                        v-if="data.id !== 'category_0'"
                        >级联删除(分类+课程)</el-dropdown-item
                      >
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>

              <!-- 平台节点操作 -->
              <template v-else>
                <el-tooltip content="编辑平台" placement="top">
                  <el-button
                    link
                    type="primary"
                    :icon="Edit"
                    @click.stop="handleEdit(data)"
                  />
                </el-tooltip>
                <el-tooltip content="删除平台" placement="top">
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    @click.stop="handleDelete(data)"
                  />
                </el-tooltip>
              </template>
            </div>
          </div>
        </template>
      </el-tree>

      <!-- 列表视图 -->
      <el-table
        v-if="viewMode === 'list'"
        :data="tableData"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="平台名称" width="150" />
        <el-table-column prop="basePrice" label="基础价格" width="100" />
        <el-table-column prop="rateType" label="费率类型" width="100">
          <template #default="scope">
            <el-tag
              :type="scope.row.rateType === 'MULTIPLY' ? 'success' : 'warning'"
            >
              {{ scope.row.rateType === "MULTIPLY" ? "乘法" : "加法" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="passwordEnabled" label="密码生成" width="100">
          <template #default="scope">
            <el-tag
              :type="scope.row.passwordEnabled === 1 ? 'success' : 'info'"
            >
              {{ scope.row.passwordEnabled === 1 ? "启用" : "禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="passwordRule"
          label="密码规则"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column prop="isSelfOperated" label="自营平台" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.isSelfOperated === 1 ? 'success' : 'info'">
              {{ scope.row.isSelfOperated === 1 ? "是" : "否" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
              {{ scope.row.status === 1 ? "上架" : "下架" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          label="说明"
          show-overflow-tooltip
        />
        <el-table-column
          label="操作"
          :width="getOperationColumnWidth()"
          fixed="right"
          class-name="operation-column"
        >
          <template #default="scope">
            <div class="operation-buttons">
              <div class="primary-actions">
                <el-button size="small" @click="handleEdit(scope.row)"
                  >编辑</el-button
                >
                <el-button
                  size="small"
                  type="danger"
                  @click="handleDelete(scope.row)"
                  >删除</el-button
                >
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="viewMode === 'list'"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      append-to-body
    >
      <el-form :model="form" label-width="120px" status-icon>
        <el-form-item label="平台名称">
          <el-input v-model="form.name" placeholder="请输入平台名称" />
        </el-form-item>
        <el-form-item label="基础价格">
          <el-input-number v-model="form.basePrice" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="所属分类">
          <el-select
            v-model="form.categoryId"
            placeholder="请选择分类"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="查课接口">
          <el-select
            v-model="form.queryApiId"
            placeholder="请选择查课接口"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in apiProviders"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="对接接口">
          <el-select
            v-model="form.dockApiId"
            placeholder="请选择对接接口"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in apiProviders"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="费率类型">
          <el-select v-model="form.rateType">
            <el-option label="乘法" value="MULTIPLY" />
            <el-option label="加法" value="ADD" />
          </el-select>
        </el-form-item>
        <el-form-item label="密码生成">
          <el-switch
            v-model="form.passwordEnabled"
            :active-value="1"
            :inactive-value="0"
          />
          <span
            style="
              font-size: 12px;
              color: var(--color-text-secondary);
              margin-left: 10px;
            "
            >启用后下单时可自动生成密码</span
          >
        </el-form-item>
        <el-form-item label="密码规则" v-if="form.passwordEnabled === 1">
          <el-input
            v-model="form.passwordRule"
            placeholder="如：{account}@ZII"
          />
        </el-form-item>
        <el-form-item label="是否自营">
          <el-switch
            v-model="form.isSelfOperated"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">上架</el-radio>
            <el-radio :label="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="一键导入平台/课程"
      width="500px"
      append-to-body
    >
      <el-form :model="importForm" label-width="120px" status-icon>
        <el-form-item label="API接口">
          <el-select
            v-model="importForm.apiProviderId"
            placeholder="请选择API接口"
            style="width: 100%"
          >
            <el-option
              v-for="item in apiProviders"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格倍率">
          <el-input-number
            v-model="importForm.priceMultiplier"
            :min="0.1"
            :step="0.1"
            :precision="2"
          />
          <div
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-top: 5px;
            "
          >
            导入的基础价格 = 原价 * 倍率
          </div>
        </el-form-item>
        <el-form-item label="指定分类ID">
          <el-input
            v-model="importForm.targetCategoryId"
            placeholder="输入远程平台的分类ID（可选）"
            clearable
          />
          <div
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-top: 5px;
            "
          >
            输入后只导入该分类下的课程（需精确匹配远程分类ID）
          </div>
        </el-form-item>
        <el-form-item label="同步分类">
          <el-switch
            v-model="importForm.syncCategories"
            :active-value="true"
            :inactive-value="false"
          />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
          >
            启用后自动创建远程分类到本地数据库
          </span>
        </el-form-item>
        <el-form-item label="排除分类">
          <el-select
            v-model="importForm.skipCategoryIds"
            placeholder="输入要排除的分类ID（多个）"
            multiple
            filterable
            allow-create
            style="width: 100%"
          >
          </el-select>
          <div
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-top: 5px;
            "
          >
            输入分类ID后按回车添加，排除的分类下的课程将不会被导入
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :icon="Download"
          :loading="importLoading"
          @click="submitImport"
        >
          开始导入
        </el-button>
      </template>
    </el-dialog>
    <!-- 分类编辑对话框 -->
    <el-dialog
      v-model="categoryDialogVisible"
      :title="categoryDialogTitle"
      width="500px"
      append-to-body
    >
      <el-form :model="categoryForm" label-width="100px" status-icon>
        <el-form-item label="分类名称">
          <el-input v-model="categoryForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="categoryForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCategorySubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import {
  Plus,
  Download,
  Refresh,
  Search,
  Upload,
  Folder,
  Monitor,
  Edit,
  Delete,
  More,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  queryPlatforms,
  createPlatform,
  updatePlatform,
  deletePlatform,
} from "@/api/course";
import axios from "@/utils/request";
import router from "../router";

const tableData = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const apiProviders = ref([]);
const categories = ref([]);
const viewMode = ref("list"); // 'list' 或 'tree'
const treeProps = {
  children: "children",
  label: "name",
  isLeaf: "isLeaf",
};
const dialogVisible = ref(false);
const dialogTitle = ref("添加平台");
const form = ref({
  name: "",
  basePrice: 0,
  rateType: "MULTIPLY",
  passwordEnabled: 0,
  passwordRule: "",
  isSelfOperated: 0,
  sortOrder: 10,
  status: 1,
  description: "",
  queryApiId: null,
  dockApiId: null,
  categoryId: null,
});

const importDialogVisible = ref(false);
const importLoading = ref(false);
const importForm = ref({
  apiProviderId: null,
  priceMultiplier: 1.0,
  targetCategoryId: null,
  syncCategories: true,
  skipCategoryIds: [],
});

// 分类管理相关
const categoryDialogVisible = ref(false);
const categoryDialogTitle = ref("编辑分类");
const categoryForm = ref({
  id: null,
  name: "",
  sortOrder: 0,
  status: 1,
});

const loadNode = async (node, resolve) => {
  if (node.level === 0) {
    // 加载根节点（分类）
    try {
      const res = await axios.get("/admin/platform-categories", {
        params: { page: 1, pageSize: 1000 },
      });
      if (res.code === 1) {
        const cats = res.data.records.map((c) => ({
          ...c,
          id: "category_" + c.id,
          realId: c.id,
          isCategory: true,
          isLeaf: false,
        }));

        // 添加"未分类"节点
        cats.push({
          id: "category_0",
          realId: 0,
          name: "未分类",
          isCategory: true,
          isLeaf: false,
          sortOrder: 9999,
        });

        // 更新本地分类列表供下拉框使用
        categories.value = res.data.records;

        return resolve(cats);
      }
      return resolve([]);
    } catch (error) {
      console.error("加载分类失败：", error);
      return resolve([]);
    }
  } else {
    // 加载子节点（平台）
    const categoryId = node.data.realId;
    try {
      const res = await queryPlatforms({
        categoryId: categoryId,
        page: 1,
        pageSize: 1000, // 加载该分类下所有平台
      });

      if (res.code === 1) {
        const platforms = res.data.records.map((p) => ({
          ...p,
          isCategory: false,
          isLeaf: true,
        }));
        return resolve(platforms);
      }
      return resolve([]);
    } catch (error) {
      console.error("加载平台失败：", error);
      return resolve([]);
    }
  }
};

const handleCategoryCommand = (command, data) => {
  switch (command) {
    case "edit":
      handleEditCategory(data);
      break;
    case "delete":
      handleDeleteCategory(data);
      break;
    case "batchDelete":
      handleBatchDelete(data);
      break;
    case "cascadeDelete":
      handleCascadeDelete(data);
      break;
  }
};

const handleQuickAdd = (categoryData) => {
  dialogTitle.value = "添加平台";
  form.value = {
    name: "",
    basePrice: 0,
    rateType: "MULTIPLY",
    passwordEnabled: 0,
    passwordRule: "",
    isSelfOperated: 0,
    sortOrder: 10,
    status: 1,
    description: "",
    queryApiId: null,
    dockApiId: null,
    categoryId: categoryData.realId === 0 ? null : categoryData.realId,
  };
  dialogVisible.value = true;
};

const handleBatchDelete = async (data) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除分类"${data.name}"下的所有课程平台吗？此操作不会删除分类本身。`,
      "批量删除确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    const res = await axios.delete(
      `/admin/platform-categories/${data.realId}/platforms`
    );
    if (res.code === 1) {
      ElMessage.success(`成功删除 ${res.data.deletedCount} 个课程平台`);
      // 刷新节点（这里简单处理，刷新整个页面或重新加载数据）
      if (viewMode.value === "tree") {
        // 理想情况下应该只刷新该节点，但 element-plus tree 刷新节点比较麻烦
        // 这里简单起见，提示用户手动刷新或切换视图
      }
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("批量删除失败：", error);
      ElMessage.error("批量删除失败");
    }
  }
};

const handleCascadeDelete = async (data) => {
  try {
    await ElMessageBox.confirm(
      `确定要级联删除分类"${data.name}"及其下的所有课程平台吗？此操作将同时删除分类和所有相关课程！`,
      "级联删除警告",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "error",
        distinguishCancelAndClose: true,
      }
    );

    const res = await axios.delete(
      `/admin/platform-categories/${data.realId}/cascade`
    );
    if (res.code === 1) {
      ElMessage.success(
        `成功删除分类及其 ${res.data.deletedPlatformsCount} 个课程平台`
      );
      // 刷新根节点
      // 这里简单起见，重新加载页面
      window.location.reload();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("级联删除失败：", error);
      ElMessage.error("级联删除失败");
    }
  }
};

const handleEditCategory = (data) => {
  categoryDialogTitle.value = "编辑分类";
  categoryForm.value = {
    id: data.realId,
    name: data.name,
    sortOrder: data.sortOrder || 0,
    status: 1,
  };
  categoryDialogVisible.value = true;
};

const handleDeleteCategory = async (data) => {
  try {
    await ElMessageBox.confirm(
      "确定要删除这个分类吗？分类下的平台可能会变为未分类。",
      "警告",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    await axios.delete(`/admin/platform-categories/${data.realId}`);
    ElMessage.success("删除成功");
    // 刷新
    window.location.reload();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除失败：", error);
      ElMessage.error("删除失败");
    }
  }
};

const handleCategorySubmit = async () => {
  try {
    if (categoryForm.value.id) {
      await axios.put("/admin/platform-categories", categoryForm.value);
      ElMessage.success("更新成功");
    }
    categoryDialogVisible.value = false;
    loadCategories();
    // 刷新页面以更新树
    if (viewMode.value === "tree") {
      window.location.reload();
    }
  } catch (error) {
    console.error("提交失败：", error);
    ElMessage.error("提交失败");
  }
};

const loadApiProviders = async () => {
  try {
    const res = await axios.get("/admin/api-providers", {
      params: { page: 1, pageSize: 100 },
    });
    if (res.code === 1) {
      apiProviders.value = res.data.records;
    }
  } catch (error) {
    console.error("加载API接口失败：", error);
  }
};

const loadData = async () => {
  try {
    const res = await queryPlatforms({
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    if (res.code === 1) {
      tableData.value = res.data.records;
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("加载数据失败：", error);
  }
};

const loadCategories = async () => {
  try {
    const res = await axios.get("/admin/platform-categories", {
      params: { page: 1, pageSize: 1000 },
    });
    if (res.code === 1) {
      categories.value = res.data.records || [];
    }
  } catch (error) {
    console.error("加载分类失败：", error);
    categories.value = [];
  }
};

const handleCategoryManagement = () => {
  viewMode.value = "tree";
  router.push("/admin/categories");
};

const handleCreate = () => {
  dialogTitle.value = "添加平台";
  form.value = {
    name: "",
    basePrice: 0,
    rateType: "MULTIPLY",
    passwordEnabled: 0,
    passwordRule: "",
    sortOrder: 10,
    status: 1,
    description: "",
    queryApiId: null,
    dockApiId: null,
    categoryId: null,
  };
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  dialogTitle.value = "编辑平台";
  form.value = { ...row };
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  try {
    // 复制表单数据并移除前端特有的字段
    const submitData = { ...form.value };
    delete submitData.isCategory;
    delete submitData.children;
    delete submitData.isLeaf;
    delete submitData.realId;

    if (submitData.id) {
      await updatePlatform(submitData);
      ElMessage.success("更新成功");
    } else {
      await createPlatform(submitData);
      ElMessage.success("创建成功");
    }
    dialogVisible.value = false;
    loadData();
    // 如果在树状视图，简单起见刷新页面
    if (viewMode.value === "tree") {
      window.location.reload();
    }
  } catch (error) {
    console.error("提交失败：", error);
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm("确定要删除这个平台吗？", "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    await deletePlatform(row.id);
    ElMessage.success("删除成功");
    loadData();
    // 如果在树状视图，简单起见刷新页面
    if (viewMode.value === "tree") {
      window.location.reload();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除失败：", error);
    }
  }
};

const handleImport = () => {
  importForm.value = {
    apiProviderId: null,
    priceMultiplier: 1.0,
    targetCategoryId: null,
    syncCategories: true,
    skipCategoryIds: [],
  };
  importDialogVisible.value = true;
};

const submitImport = async () => {
  if (!importForm.value.apiProviderId) {
    ElMessage.warning("请选择API接口");
    return;
  }
  importLoading.value = true;
  try {
    const params = {
      apiProviderId: importForm.value.apiProviderId,
      priceMultiplier: importForm.value.priceMultiplier,
      syncCategories: importForm.value.syncCategories,
    };
    if (importForm.value.targetCategoryId) {
      params.targetCategoryId = importForm.value.targetCategoryId;
    }
    if (
      importForm.value.skipCategoryIds &&
      importForm.value.skipCategoryIds.length > 0
    ) {
      params.skipCategoryIds = importForm.value.skipCategoryIds;
    }

    const res = await axios.post("/admin/docking/import-platforms", null, {
      params,
      timeout: 120000,
    });

    if (res.code === 1) {
      let message = `导入成功: 总数${res.data.total}, 成功${res.data.success}, 失败${res.data.fail}`;
      if (res.data.created !== undefined) {
        message += `, 新建${res.data.created}, 更新${res.data.updated}`;
      }
      if (res.data.categoryCreated !== undefined) {
        message += `, 创建分类${res.data.categoryCreated}个`;
      }
      ElMessage.success(message);
      importDialogVisible.value = false;
      loadData();
      loadCategories();
    } else {
      ElMessage.error(res.msg || "导入失败");
    }
  } catch (error) {
    console.error("导入失败:", error);
    ElMessage.error("导入失败");
  } finally {
    importLoading.value = false;
  }
};

const isMobile = ref(false);
const checkScreenSize = () => {
  isMobile.value = window.innerWidth <= 768;
};
const handleResize = () => {
  checkScreenSize();
};
const getOperationColumnWidth = () => {
  if (isMobile.value) {
    return 120;
  } else if (window.innerWidth <= 1200) {
    return 150;
  } else {
    return 180;
  }
};

onMounted(() => {
  checkScreenSize();
  window.addEventListener("resize", handleResize);
  loadData();
  loadApiProviders();
  loadCategories();
});

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
});
</script>

<style scoped>
.admin-platforms {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
}

.platform-tree {
  background: transparent;
  margin-top: 20px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
  height: 40px;
}

.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon {
  margin-right: 4px;
}

.tree-node:hover .node-actions {
  display: flex;
}

:deep(.el-tree-node__content) {
  height: 40px;
  border-radius: 4px;
  margin-bottom: 2px;
}

:deep(.el-tree-node__content:hover) {
  background-color: #f5f7fa;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

/* 操作列响应式样式 */
.operation-buttons {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.operation-buttons > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.operation-buttons .el-button {
  margin: 0;
  padding: 4px 8px;
  font-size: 12px;
  min-width: auto;
}

/* 移动端操作列优化 */
@media (max-width: 768px) {
  .operation-buttons {
    gap: 2px;
  }

  .operation-buttons .el-button {
    padding: 2px 6px;
    font-size: 11px;
    min-width: 40px;
  }

  .operation-buttons > div {
    gap: 2px;
  }
}

/* 中等屏幕优化 */
@media (max-width: 1200px) and (min-width: 769px) {
  .operation-buttons .el-button {
    padding: 3px 6px;
    font-size: 11px;
  }
}

/* 表格操作列固定宽度优化 */
.operation-column {
  min-width: 120px;
}

@media (min-width: 769px) {
  .operation-column {
    min-width: 150px;
  }
}

@media (min-width: 1201px) {
  .operation-column {
    min-width: 180px;
  }
}
</style>
