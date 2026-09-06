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
            >
              <el-radio-button label="list">列表视图</el-radio-button>
              <el-radio-button label="tree">树状视图</el-radio-button>
            </el-radio-group>
            <el-button
              type="primary"
              @click="handleCategoryManagement"
              plain
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
                    : 'var(--brand-primary)'
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
              color: var(--text-secondary);
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

    <!-- 商品查询与选择导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="查询并导入第三方商品"
      width="min(1000px, 96vw)"
      append-to-body
      destroy-on-close
    >
      <el-form :model="importForm" label-width="100px" class="import-query-form">
        <el-row :gutter="16">
          <el-col :xs="24" :md="10">
            <el-form-item label="API接口" required>
              <el-select
                v-model="importForm.apiProviderId"
                placeholder="请选择API接口"
                style="width: 100%"
                @change="handleImportProviderChange"
              >
                <el-option
                  v-for="item in apiProviders"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="远程分类ID">
              <el-input
                v-model="importForm.categoryId"
                placeholder="可选，不传查询全部"
                clearable
                @keyup.enter="queryImportProducts"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label-width="0">
              <el-button
                type="primary"
                :icon="Search"
                :loading="productLoading"
                style="width: 100%"
                @click="queryImportProducts"
              >
                查询商品
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <el-form-item label="价格倍率">
              <el-input-number
                v-model="importForm.priceMultiplier"
                :min="0.01"
                :step="0.1"
                :precision="2"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="同步分类">
              <el-switch v-model="importForm.syncCategories" />
              <span class="form-tip">自动创建远程分类</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="筛选商品">
              <el-input
                v-model="productKeyword"
                placeholder="商品ID、名称或分类"
                clearable
                :disabled="products.length === 0"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="import-summary">
        <span>查询到 {{ products.length }} 个商品</span>
        <span>已选择 {{ selectedProducts.length }} 个</span>
        <span v-if="products.length">
          已导入 {{ products.filter((item) => item.imported).length }} 个（再次导入将更新）
        </span>
      </div>

      <el-table
        ref="productTableRef"
        v-loading="productLoading"
        :data="filteredProducts"
        row-key="id"
        max-height="460"
        empty-text="请先选择接口并查询商品"
        @selection-change="handleProductSelectionChange"
      >
        <el-table-column type="selection" width="48" reserve-selection />
        <el-table-column prop="id" label="商品ID" width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="商品名称" min-width="210" show-overflow-tooltip />
        <el-table-column label="分类" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.categoryName || "未分类" }}
            <span v-if="scope.row.categoryId" class="category-id">({{ scope.row.categoryId }})</span>
          </template>
        </el-table-column>
        <el-table-column label="上游价格" width="105" align="right">
          <template #default="scope">¥{{ formatProductPrice(scope.row.price) }}</template>
        </el-table-column>
        <el-table-column label="导入价格" width="105" align="right">
          <template #default="scope">
            ¥{{ formatProductPrice(Number(scope.row.price || 0) * Number(importForm.priceMultiplier || 1)) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.imported" type="warning" effect="plain">已导入/更新</el-tag>
            <el-tag v-else type="success" effect="plain">新商品</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :icon="Download"
          :loading="importLoading"
          :disabled="selectedProducts.length === 0"
          @click="submitImport"
        >
          导入选中商品（{{ selectedProducts.length }}）
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
import { ref, onMounted, computed } from "vue";
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
  fetchProviderProducts,
  importSelectedProducts,
} from "@/api/course";
import axios from "@/utils/request";
import { useResponsive } from "@/composables/useResponsive";
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
const productLoading = ref(false);
const products = ref([]);
const selectedProducts = ref([]);
// 绑定已展示的商品列表，不能读取用户查询后又编辑过的分类输入框。
const productSourceCategoryId = ref(null);
const productKeyword = ref("");
const productTableRef = ref(null);
let productQueryVersion = 0;
const importForm = ref({
  apiProviderId: null,
  categoryId: "",
  priceMultiplier: 1.0,
  syncCategories: true,
});
const filteredProducts = computed(() => {
  const keyword = productKeyword.value.trim().toLowerCase();
  if (!keyword) return products.value;
  return products.value.filter((item) =>
    [item.id, item.name, item.categoryId, item.categoryName]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword))
  );
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

const resetImportProducts = () => {
  productSourceCategoryId.value = null;
  products.value = [];
  selectedProducts.value = [];
  productKeyword.value = "";
  productTableRef.value?.clearSelection();
};

const handleImport = () => {
  productQueryVersion += 1;
  productLoading.value = false;
  importForm.value = {
    apiProviderId: null,
    categoryId: "",
    priceMultiplier: 1.0,
    syncCategories: true,
  };
  resetImportProducts();
  importDialogVisible.value = true;
};

const handleImportProviderChange = () => {
  productQueryVersion += 1;
  productLoading.value = false;
  resetImportProducts();
};

const queryImportProducts = async () => {
  if (!importForm.value.apiProviderId) {
    ElMessage.warning("请选择API接口");
    return;
  }
  const providerId = importForm.value.apiProviderId;
  const queryVersion = ++productQueryVersion;
  productLoading.value = true;
  resetImportProducts();
  try {
    const params = { apiProviderId: providerId };
    if (importForm.value.categoryId?.trim()) {
      params.categoryId = importForm.value.categoryId.trim();
    }
    const res = await fetchProviderProducts(params);
    if (queryVersion !== productQueryVersion || importForm.value.apiProviderId !== providerId) return;
    productSourceCategoryId.value = params.categoryId || null;
    products.value = res.data || [];
    if (products.value.length === 0) {
      ElMessage.info("未查询到商品");
    } else {
      ElMessage.success(`查询到 ${products.value.length} 个商品`);
    }
  } catch (error) {
    if (queryVersion === productQueryVersion) {
      console.error("查询第三方商品失败:", error);
    }
  } finally {
    if (queryVersion === productQueryVersion) {
      productLoading.value = false;
    }
  }
};

const handleProductSelectionChange = (rows) => {
  selectedProducts.value = rows;
};

const formatProductPrice = (value) => {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(2) : "0.00";
};

const submitImport = async () => {
  if (selectedProducts.value.length === 0) {
    ElMessage.warning("请至少选择一个商品");
    return;
  }
  importLoading.value = true;
  try {
    const res = await importSelectedProducts({
      apiProviderId: importForm.value.apiProviderId,
      productIds: selectedProducts.value.map((item) => String(item.id)),
      categoryId: productSourceCategoryId.value,
      priceMultiplier: importForm.value.priceMultiplier,
      syncCategories: importForm.value.syncCategories,
    });
    const data = res.data || {};
    let message = `导入完成：成功 ${data.success || 0}，新建 ${data.created || 0}，更新 ${data.updated || 0}`;
    if (data.missing) message += `，失效 ${data.missing}`;
    if (data.fail) message += `，失败 ${data.fail}`;
    ElMessage.success(message);
    importDialogVisible.value = false;
    loadData();
    loadCategories();
  } catch (error) {
    console.error("导入失败:", error);
  } finally {
    importLoading.value = false;
  }
};

// 共享断点状态取代页面级 resize 监听，避免同页重复订阅窗口事件。
const { isMobile, screenWidth } = useResponsive();
const getOperationColumnWidth = () => {
  if (isMobile.value) {
    return 120;
  } else if (screenWidth.value < 1200) {
    return 150;
  } else {
    return 180;
  }
};

onMounted(() => {
  loadData();
  loadApiProviders();
  loadCategories();
});

</script>

<style scoped>
.admin-platforms {
  padding: 20px;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  min-width: 0;
  gap: 10px;
}
.header-actions > .el-button { margin: 0; }

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
@media (max-width: 767px) {
  .admin-platforms { padding: 0; }
  .card-header { align-items: stretch; flex-direction: column; gap: 12px; }
  .card-header > span { font-weight: 600; }
  .header-actions { display: flex; flex-wrap: wrap; gap: 8px; min-width: 0; }
  .header-actions > .el-radio-group { flex-basis: 100%; margin: 0 !important; }
  .header-actions > .el-button { flex: 1 1 auto; margin: 0 !important; }
  .operation-buttons { gap: 4px; }

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
@media (min-width: 768px) and (max-width: 1199px) {
  .operation-buttons .el-button {
    padding: 3px 6px;
    font-size: 11px;
  }
}

/* 表格操作列固定宽度优化 */
.operation-column {
  min-width: 120px;
}

@media (min-width: 768px) {
  .operation-column {
    min-width: 150px;
  }
}

@media (min-width: 1200px) {
  .operation-column {
    min-width: 180px;
  }
}

.import-query-form {
  padding: 4px 0 8px;
}

.form-tip {
  margin-left: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}

.import-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.category-id {
  color: var(--text-placeholder);
  font-size: 12px;
}

@media (max-width: 767px) {
  .import-query-form :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}
</style>
