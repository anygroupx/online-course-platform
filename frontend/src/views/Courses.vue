<template>
  <div class="courses-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>查课/下单</span>
        </div>
      </template>

      <el-form
        :model="queryForm"
        :label-width="isMobile ? '80px' : '120px'"
        :label-position="isMobile ? 'top' : 'right'"
      >
        <el-form-item label="选择平台" class="platform-select-item">
          <el-select
            v-model="queryForm.platformId"
            placeholder="请先选择平台"
            class="platform-select"
            :class="{ 'is-mobile': isMobile }"
            filterable
            @change="handlePlatformChange"
          >
            <el-option
              v-for="platform in platformList"
              :key="platform.id"
              :label="formatPlatformLabel(platform)"
              :value="platform.id"
              class="platform-option"
            >
              <div class="platform-option-content">
                <span class="platform-name">{{ platform.name }}</span>
                <span class="platform-price">{{ (platform.basePrice * personalPriceMultiplier).toFixed(2) }}元</span>
              </div>
            </el-option>
          </el-select>

          <!-- 当前选中平台的价格提示 -->
          <div v-if="currentPlatform" class="platform-price-hint">
            <el-icon><PriceTag /></el-icon>
            <span>{{ (currentPlatform.basePrice * personalPriceMultiplier).toFixed(2) }}元/门</span>
          </div>

          <!-- 平台描述（支持折叠/展开） -->
          <div
            v-if="currentPlatform && currentPlatform.description"
            class="platform-desc"
            :class="{ 'is-collapsed': descCollapsed && isMobile }"
          >
            <div class="desc-content">
              {{ currentPlatform.description }}
            </div>
            <el-button
              v-if="isMobile && currentPlatform.description.length > 50"
              link
              type="primary"
              size="small"
              @click="descCollapsed = !descCollapsed"
              class="desc-toggle"
            >
              {{ descCollapsed ? '展开' : '收起' }}
              <el-icon><ArrowDown v-if="descCollapsed" /><ArrowUp v-else /></el-icon>
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="账号信息">
          <el-input
            v-model="queryForm.userInfo"
            type="textarea"
            :rows="4"
            placeholder="下单格式：&#10;学校 账号 密码&#10;或：账号 密码&#10;或：账号（自动生成密码）&#10;&#10;智能匹配：可在每行后加课程名，如：北京大学 111 222 心理健康 情绪管理"
          />
          <div
            style="
              font-size: 12px;
              color: var(--color-warning);
              margin-top: 8px;
            "
          >
            <div>下单格式：学校 账号 密码</div>
            <div>多账号下单可以换行输入</div>
            <div>
              智能匹配：可在每行后加课程名，如：北京大学 111 222 心理健康
              情绪管理
            </div>
            <div
              v-if="
                currentPlatform &&
                currentPlatform.passwordEnabled &&
                currentPlatform.passwordRule
              "
              style="color: var(--color-success); margin-top: 5px"
            >
              当前平台支持自动生成密码：{{
                currentPlatform.passwordRule.replace("{account}", "账号")
              }}
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <div
            :class="isMobile ? 'mobile-button-group' : 'desktop-button-group'"
          >
            <el-button
              type="primary"
              @click="handleQuery"
              :loading="queryLoading"
              :size="isMobile ? 'default' : 'default'"
            >
              <el-icon><Search /></el-icon>
              <span v-if="!isMobile">查询课程</span>
              <span v-else>查询</span>
            </el-button>
            <el-button
              type="success"
              @click="handleDirectSubmit"
              :loading="submitLoading"
              :size="isMobile ? 'default' : 'default'"
            >
              <el-icon><Check /></el-icon>
              <span v-if="!isMobile">立即提交</span>
              <span v-else>提交</span>
            </el-button>
            <el-button
              @click="handleReset"
              :size="isMobile ? 'default' : 'default'"
            >
              <el-icon><RefreshLeft /></el-icon>
              <span v-if="!isMobile">重置</span>
            </el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 查询结果 - 多账号分组展示 -->
    <div v-if="accountCourseList.length > 0" style="margin-top: 20px">
      <!-- 批量操作栏 -->
      <el-card style="margin-bottom: 20px">
        <div class="batch-actions" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px">
          <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
            <span style="font-weight: 600; color: var(--text-primary)">
              批量操作
            </span>
            <el-tag type="info" size="large">
              共 {{ accountCourseList.length }} 个账号
            </el-tag>
            <el-tag type="success" size="large">
              已查询成功 {{ successAccountCount }} 个
            </el-tag>
            <el-tag v-if="totalSelectedCount > 0" type="warning" size="large">
              已选择 {{ totalSelectedCount }} 门课程
            </el-tag>
          </div>
          <div style="display: flex; gap: 12px; flex-wrap: wrap">
            <el-button
              type="primary"
              @click="handleSelectAllCourses"
              :size="isMobile ? 'small' : 'default'"
            >
              <el-icon><Select /></el-icon>
              <span v-if="!isMobile">全选所有课程</span>
              <span v-else>全选</span>
            </el-button>
            <el-button
              type="success"
              @click="handleBatchSubmit"
              :loading="submitLoading"
              :disabled="totalSelectedCount === 0"
              :size="isMobile ? 'small' : 'default'"
            >
              <el-icon><Check /></el-icon>
              <span v-if="!isMobile">批量提交所有选中</span>
              <span v-else>批量提交</span>
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 各账号卡片 -->
      <el-card
        v-for="(accountData, index) in accountCourseList"
        :key="index"
        style="margin-bottom: 20px"
      >
        <template #header>
          <div class="card-header" :class="{ 'mobile-header': isMobile }">
            <div>
              <span style="font-weight: 600">
                账号 {{ index + 1 }}: {{ accountData.account.studentAccount }}
              </span>
              <el-tag
                v-if="accountData.account.schoolName"
                size="small"
                style="margin-left: 10px"
              >
                {{ accountData.account.schoolName }}
              </el-tag>
              <el-tag
                v-if="accountData.querySuccess"
                type="success"
                size="small"
                style="margin-left: 10px"
              >
                ✓ 查询成功
              </el-tag>
              <el-tag
                v-else
                type="danger"
                size="small"
                style="margin-left: 10px"
              >
                ✗ 查询失败
              </el-tag>
              <span
                v-if="accountData.querySuccess"
                style="margin-left: 10px; color: var(--text-secondary); font-size: 14px"
              >
                （共{{ accountData.courses.length }}门课程）
              </span>
            </div>
            <el-button
              v-if="accountData.querySuccess && accountData.courses.length > 0"
              type="success"
              @click="handleSubmitAccountSelected(accountData)"
              :loading="submitLoading"
              :size="isMobile ? 'small' : 'default'"
            >
              <el-icon><Check /></el-icon>
              <span v-if="!isMobile">提交选中课程</span>
              <span v-else>提交</span>
            </el-button>
          </div>
        </template>

        <!-- 查询失败提示 -->
        <el-alert
          v-if="!accountData.querySuccess"
          type="error"
          :title="`查询失败：${accountData.errorMessage}`"
          :closable="false"
          show-icon
        >
          <template #default>
            <div style="margin-top: 8px">
              <div>请检查：</div>
              <ul style="margin: 5px 0; padding-left: 20px">
                <li>账号密码是否正确</li>
                <li>学校名称是否准确</li>
                <li>平台是否支持该学校</li>
              </ul>
            </div>
          </template>
        </el-alert>

        <!-- 课程列表 -->
        <el-table
          v-if="accountData.querySuccess && accountData.courses.length > 0"
          :ref="(el) => setTableRef(el, index)"
          :data="accountData.courses"
          @selection-change="(selection) => handleAccountSelectionChange(index, selection)"
          :size="isMobile ? 'small' : 'default'"
        >
          <el-table-column type="selection" :width="isMobile ? 45 : 55" />
          <el-table-column
            prop="name"
            label="课程名称"
            show-overflow-tooltip
            :min-width="isMobile ? 120 : 200"
          />
          <el-table-column
            prop="description"
            label="课程描述"
            show-overflow-tooltip
            :min-width="150"
          />
          <el-table-column
            v-if="!isMobile"
            prop="endTime"
            label="结束时间"
            width="160"
          />
          <el-table-column
            label="操作"
            :width="isMobile ? 70 : 100"
            :fixed="isMobile ? 'right' : false"
          >
            <template #default="scope">
              <el-button
                size="small"
                type="primary"
                @click="handleSubmitSingleCourse(accountData.account, scope.row)"
              >
                <span v-if="!isMobile">单独提交</span>
                <span v-else>提交</span>
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 空状态提示（查询成功但无课程） -->
        <el-empty
          v-if="accountData.querySuccess && accountData.courses.length === 0"
          description="该账号暂无可选课程"
          :image-size="80"
        />

        <!-- 已选择课程提示 -->
        <div
          v-if="accountData.querySuccess && accountData.selectedCourses.length > 0"
          class="selection-info"
          style="
            margin-top: 15px;
            padding: 10px;
            background-color: var(--brand-blue-soft);
            border-radius: 4px;
          "
        >
          <div class="selection-info-text" style="font-size: 13px; color: var(--color-primary)">
             已选择 {{ accountData.selectedCourses.length }} 门课程
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import { Search, Check, RefreshLeft, Select, PriceTag, ArrowDown, ArrowUp } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useResponsive } from "@/composables/useResponsive";
import { getCoursePlatforms } from "@/api/course";
import { queryCourses } from "@/api/course";
import { createOrder } from "@/api/order";
import { getUserInfo } from "@/api/user";

// 个人定价系数（费率）
const personalPriceMultiplier = ref(1.0);

// 使用响应式 composable
const { isMobile } = useResponsive();

const platformList = ref([]);
const courseList = ref([]); // 保留用于兼容性
const selectedCourses = ref([]); // 保留用于兼容性
const accountCourseList = ref([]); // 新增：存储每个账号的课程列表 [{account, courses, selectedCourses}]
const tableRefs = ref({}); // 存储表格refs
const queryLoading = ref(false);
const submitLoading = ref(false);
const currentPlatform = ref(null);
const descCollapsed = ref(true); // 平台描述折叠状态（移动端默认折叠）

const queryForm = ref({
  platformId: null,
  userInfo: "",
});

// 计算属性：成功查询的账号数
const successAccountCount = computed(() => {
  return accountCourseList.value.filter(item => item.querySuccess).length;
});

// 计算属性：总共选中的课程数
const totalSelectedCount = computed(() => {
  return accountCourseList.value.reduce((total, item) => {
    return total + (item.selectedCourses?.length || 0);
  }, 0);
});

// 设置表格ref
const setTableRef = (el, index) => {
  if (el) {
    tableRefs.value[index] = el;
  }
};

// 加载用户费率
const loadUserRate = async () => {
  try {
    const res = await getUserInfo();
    if (res.code === 1 && res.data.rate) {
      personalPriceMultiplier.value = res.data.rate;
    }
  } catch (error) {
    console.error("加载用户费率失败：", error);
  }
};

const loadPlatforms = async () => {
  try {
    const res = await getCoursePlatforms();
    if (res.code === 1) {
      platformList.value = res.data;
    }
  } catch (error) {
    console.error("加载平台列表失败：", error);
  }
};

// 格式化平台标签（根据屏幕尺寸）
const formatPlatformLabel = (platform) => {
  const price = (platform.basePrice * personalPriceMultiplier.value).toFixed(2);
  if (isMobile.value) {
    // 移动端：简化显示
    return `${platform.name}（${price}元）`;
  }
  // PC端：完整显示
  return `${platform.name}（${price}元）`;
};

const handlePlatformChange = (platformId) => {
  currentPlatform.value = platformList.value.find((p) => p.id === platformId);
  descCollapsed.value = true; // 重置折叠状态
};

/**
 * 解析用户输入的账号信息
 * 格式：学校 账号 密码 或 账号 密码
 * 支持自动生成密码：如果密码为空且平台支持自动生成，则自动生成密码
 */
const parseUserInfo = (userInfo) => {
  const lines = userInfo
    .trim()
    .split("\n")
    .filter((line) => line.trim());
  const accounts = [];

  for (const line of lines) {
    const parts = line.trim().split(/\s+/);

    if (parts.length >= 1) {
      let account = {};

      if (parts.length === 1) {
        // 格式：账号（自动生成密码）
        account = {
          schoolName: "自动识别",
          studentAccount: parts[0],
          studentPassword: generatePasswordForAccount(parts[0]),
        };
      } else if (parts.length === 2) {
        // 格式：账号 密码
        account = {
          schoolName: "自动识别",
          studentAccount: parts[0],
          studentPassword: parts[1],
        };
      } else if (parts.length >= 3) {
        // 格式：学校 账号 密码 [课程名...]
        account = {
          schoolName: parts[0],
          studentAccount: parts[1],
          studentPassword: parts[2],
        };

        // 如果有第4个参数及以后，作为课程名
        if (parts.length > 3) {
          account.courseName = parts.slice(3).join(" ");
        }
      }

      accounts.push(account);
    }
  }

  return accounts;
};

// 为账号生成密码
const generatePasswordForAccount = (account) => {
  if (
    !currentPlatform.value ||
    !currentPlatform.value.passwordEnabled ||
    !currentPlatform.value.passwordRule
  ) {
    return ""; // 如果平台不支持自动生成，返回空字符串
  }

  // 使用密码规则生成密码，{account}替换为实际账号
  return currentPlatform.value.passwordRule.replace("{account}", account);
};

const handleQuery = async () => {
  if (!queryForm.value.platformId) {
    ElMessage.warning("请先选择课程平台");
    return;
  }
  if (!queryForm.value.userInfo.trim()) {
    ElMessage.warning("请输入账号信息");
    return;
  }

  queryLoading.value = true;
  try {
    const accounts = parseUserInfo(queryForm.value.userInfo);

    if (accounts.length === 0) {
      ElMessage.warning("账号信息格式错误，请按照格式输入");
      return;
    }

    // 清空之前的查询结果
    accountCourseList.value = [];
    let totalCourses = 0;
    let successCount = 0;

    // 为每个账号分别查询课程
    for (const account of accounts) {
      try {
        const res = await queryCourses({
          platformId: queryForm.value.platformId,
          schoolName: account.schoolName,
          studentAccount: account.studentAccount,
          studentPassword: account.studentPassword,
        });

        if (res.code === 1) {
          const courses = res.data.courses || [];
          accountCourseList.value.push({
            account: account,
            courses: courses,
            selectedCourses: [], // 每个账号独立的选中课程
            querySuccess: true, // 查询成功标记
            errorMessage: null,
          });
          totalCourses += courses.length;
          successCount++;
        } else {
          // 查询失败但有响应
          accountCourseList.value.push({
            account: account,
            courses: [],
            selectedCourses: [],
            querySuccess: false,
            errorMessage: res.msg || "查询失败",
          });
        }
      } catch (error) {
        console.error(`账号 ${account.studentAccount} 查询失败：`, error);
        // 查询失败也要添加到列表中
        accountCourseList.value.push({
          account: account,
          courses: [],
          selectedCourses: [],
          querySuccess: false,
          errorMessage: error.response?.data?.msg || error.message || "网络错误",
        });
      }
    }

    if (successCount > 0) {
      ElMessage.success(
        `成功查询 ${successCount}/${accounts.length} 个账号，共 ${totalCourses} 门课程`
      );
    } else {
      ElMessage.error("所有账号查询失败，请检查账号信息");
    }
  } catch (error) {
    console.error("查询失败：", error);
  } finally {
    queryLoading.value = false;
  }
};

const handleDirectSubmit = async () => {
  if (!queryForm.value.platformId) {
    ElMessage.warning("请先选择课程平台");
    return;
  }
  if (!queryForm.value.userInfo.trim()) {
    ElMessage.warning("请输入账号信息");
    return;
  }

  const accounts = parseUserInfo(queryForm.value.userInfo);

  if (accounts.length === 0) {
    ElMessage.warning("账号信息格式错误");
    return;
  }

  // 检查是否所有账号都有课程名
  const hasCourseName = accounts.every((acc) => acc.courseName);

  if (!hasCourseName) {
    ElMessage.warning("立即提交需要在每行后面加上课程名称");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `将提交${accounts.length}个订单，是否继续？`,
      "确认提交",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    submitLoading.value = true;
    let successCount = 0;

    for (const account of accounts) {
      try {
        await createOrder({
          platformId: queryForm.value.platformId,
          schoolName: account.schoolName,
          studentAccount: account.studentAccount,
          studentPassword: account.studentPassword,
          courseName: account.courseName,
        });
        successCount++;
      } catch (error) {
        console.error("提交订单失败：", error);
      }
    }

    ElMessage.success(`成功提交${successCount}/${accounts.length}个订单`);
    handleReset();
  } catch (error) {
    if (error !== "cancel") {
      console.error("提交失败：", error);
    }
  } finally {
    submitLoading.value = false;
  }
};

const handleReset = () => {
  queryForm.value = {
    platformId: null,
    userInfo: "",
  };
  courseList.value = [];
  selectedCourses.value = [];
  accountCourseList.value = []; // 清空账号课程列表
  tableRefs.value = {}; // 清空表格refs
  currentPlatform.value = null;
};

// 处理单个账号的课程选择变化
const handleAccountSelectionChange = (accountIndex, selection) => {
  accountCourseList.value[accountIndex].selectedCourses = selection;
};

// 全选所有课程
const handleSelectAllCourses = () => {
  // 为每个查询成功的账号选中所有课程
  accountCourseList.value.forEach((accountData, index) => {
    if (accountData.querySuccess && accountData.courses.length > 0) {
      accountData.selectedCourses = [...accountData.courses];

      // 同步表格的选中状态
      const tableRef = tableRefs.value[index];
      if (tableRef) {
        // 清空当前选中
        tableRef.clearSelection();
        // 选中所有行
        accountData.courses.forEach(row => {
          tableRef.toggleRowSelection(row, true);
        });
      }
    }
  });
  ElMessage.success("已全选所有课程");
};

// 批量提交所有选中的课程
const handleBatchSubmit = async () => {
  const totalSelected = totalSelectedCount.value;

  if (totalSelected === 0) {
    ElMessage.warning("请先选择要提交的课程");
    return;
  }

  // 统计有选中课程的账号
  const accountsWithSelection = accountCourseList.value.filter(
    item => item.selectedCourses && item.selectedCourses.length > 0
  );

  try {
    await ElMessageBox.confirm(
      `将为 ${accountsWithSelection.length} 个账号提交 ${totalSelected} 门课程，是否继续？`,
      "批量提交确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    submitLoading.value = true;
    let successCount = 0;
    let failCount = 0;

    // 为每个有选中课程的账号提交
    for (let i = 0; i < accountsWithSelection.length; i++) {
      const accountData = accountsWithSelection[i];
      const accountIndex = accountCourseList.value.indexOf(accountData);

      for (const course of accountData.selectedCourses) {
        try {
          await createOrder({
            platformId: queryForm.value.platformId,
            schoolName: accountData.account.schoolName,
            studentAccount: accountData.account.studentAccount,
            studentPassword: accountData.account.studentPassword,
            courseId: course.id,
            courseName: course.courseName || course.name,
          });
          successCount++;
        } catch (error) {
          console.error("提交订单失败：", error);
          failCount++;
        }
      }

      // 提交后清空该账号的选中状态
      accountData.selectedCourses = [];

      // 同时清空表格的选中状态
      const tableRef = tableRefs.value[accountIndex];
      if (tableRef) {
        tableRef.clearSelection();
      }
    }

    if (failCount > 0) {
      ElMessage.warning(
        `批量提交完成：成功 ${successCount} 个，失败 ${failCount} 个`
      );
    } else {
      ElMessage.success(`批量提交成功：共 ${successCount} 个订单`);
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("批量提交失败：", error);
    }
  } finally {
    submitLoading.value = false;
  }
};

// 提交单个账号选中的课程
const handleSubmitAccountSelected = async (accountData) => {
  if (accountData.selectedCourses.length === 0) {
    ElMessage.warning("请先选择课程");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `将为账号 ${accountData.account.studentAccount} 提交 ${accountData.selectedCourses.length} 门课程，是否继续？`,
      "确认提交",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    submitLoading.value = true;
    let successCount = 0;

    for (const course of accountData.selectedCourses) {
      try {
        await createOrder({
          platformId: queryForm.value.platformId,
          schoolName: accountData.account.schoolName,
          studentAccount: accountData.account.studentAccount,
          studentPassword: accountData.account.studentPassword,
          courseId: course.id,
          courseName: course.courseName || course.name,
        });
        successCount++;
      } catch (error) {
        console.error("提交订单失败：", error);
      }
    }

    ElMessage.success(
      `成功提交 ${successCount}/${accountData.selectedCourses.length} 个订单`
    );

    // 清空该账号的选中状态
    accountData.selectedCourses = [];

    // 同时清空表格的选中状态
    const accountIndex = accountCourseList.value.indexOf(accountData);
    const tableRef = tableRefs.value[accountIndex];
    if (tableRef) {
      tableRef.clearSelection();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("提交失败：", error);
    }
  } finally {
    submitLoading.value = false;
  }
};

// 提交单个账号的单门课程
const handleSubmitSingleCourse = async (account, course) => {
  try {
    await ElMessageBox.confirm(
      `将为账号 ${account.studentAccount} 提交课程 "${course.name}"，是否继续？`,
      "确认提交",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "info",
      }
    );

    submitLoading.value = true;
    try {
      await createOrder({
        platformId: queryForm.value.platformId,
        schoolName: account.schoolName,
        studentAccount: account.studentAccount,
        studentPassword: account.studentPassword,
        courseId: course.id,
        courseName: course.name,
      });
      ElMessage.success("订单提交成功");
    } catch (error) {
      console.error("提交订单失败：", error);
      ElMessage.error("订单提交失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("提交失败：", error);
    }
  } finally {
    submitLoading.value = false;
  }
};

// 保留原有的函数用于兼容（但已不再使用）
const handleSelectionChange = (selection) => {
  selectedCourses.value = selection;
};

const handleSubmitSelected = async () => {
  if (selectedCourses.value.length === 0) {
    ElMessage.warning("请先选择课程");
    return;
  }

  if (!queryForm.value.userInfo.trim()) {
    ElMessage.warning("账号信息丢失，请重新输入");
    return;
  }

  try {
    const accounts = parseUserInfo(queryForm.value.userInfo);
    if (accounts.length === 0) {
      ElMessage.warning("账号信息格式错误");
      return;
    }

    await ElMessageBox.confirm(
      `将为${accounts.length}个账号提交${
        selectedCourses.value.length
      }门课程，总计${
        accounts.length * selectedCourses.value.length
      }个订单，是否继续？`,
      "确认提交",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    submitLoading.value = true;
    let successCount = 0;

    // 为每个账号提交选中的课程
    for (const account of accounts) {
      for (const course of selectedCourses.value) {
        try {
          await createOrder({
            platformId: queryForm.value.platformId,
            schoolName: account.schoolName,
            studentAccount: account.studentAccount,
            studentPassword: account.studentPassword,
            courseId: course.id,
            courseName: course.courseName || course.name,
          });
          successCount++;
        } catch (error) {
          console.error("提交订单失败：", error);
        }
      }
    }

    ElMessage.success(
      `成功提交${successCount}/${
        accounts.length * selectedCourses.value.length
      }个订单`
    );
    handleReset();
  } catch (error) {
    if (error !== "cancel") {
      console.error("提交失败：", error);
    }
  } finally {
    submitLoading.value = false;
  }
};

const handleSubmitSingle = async (course) => {
  if (!queryForm.value.userInfo.trim()) {
    ElMessage.warning("账号信息丢失，请重新输入");
    return;
  }

  try {
    const accounts = parseUserInfo(queryForm.value.userInfo);
    if (accounts.length === 0) {
      ElMessage.warning("账号信息格式错误");
      return;
    }

    await ElMessageBox.confirm(
      `将为${accounts.length}个账号提交课程"${course.name}"，是否继续？`,
      "确认提交",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "info",
      }
    );

    let successCount = 0;
    for (const account of accounts) {
      try {
        await createOrder({
          platformId: queryForm.value.platformId,
          schoolName: account.schoolName,
          studentAccount: account.studentAccount,
          studentPassword: account.studentPassword,
          courseId: course.id,
          courseName: course.name,
        });
        successCount++;
      } catch (error) {
        console.error("提交订单失败：", error);
      }
    }

    ElMessage.success(`成功提交${successCount}/${accounts.length}个订单`);
  } catch (error) {
    if (error !== "cancel") {
      console.error("提交失败：", error);
    }
  }
};

onMounted(() => {
  loadUserRate(); // 加载用户费率
  loadPlatforms();
});
</script>

<style scoped>
.courses-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.card-header.mobile-header {
  font-size: 14px;
}

.desktop-button-group {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.mobile-button-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;
}

.mobile-button-group .el-button {
  flex: 1;
  min-width: 80px;
}

:deep(.el-textarea__inner) {
  font-family: Consolas, Monaco, "Courier New", monospace;
  border-radius: 8px;
  transition: all 0.3s;
}

:deep(.el-textarea__inner):focus {
  box-shadow: 0 0 0 2px rgba(78, 140, 255, 0.2);
}

:deep(.el-card) {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
}

:deep(.el-card):hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

:deep(.el-button) {
  border-radius: 6px;
  font-weight: 500;
  transition: all 0.3s;
}

:deep(.el-button:hover) {
  transform: translateY(-2px);
}

:deep(.el-select .el-input__wrapper) {
  border-radius: 8px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .courses-page {
    padding: 12px;
  }

  :deep(.el-card) {
    margin-bottom: 12px;
  }

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    padding: 0;
    margin-bottom: 6px;
    font-size: 13px;
  }

  :deep(.el-input__inner),
  :deep(.el-textarea__inner) {
    font-size: 14px;
  }

  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table th.el-table__cell) {
    padding: 8px 0;
    font-size: 12px;
  }

  :deep(.el-table td.el-table__cell) {
    padding: 8px 0;
  }

  :deep(.el-button) {
    padding: 8px 12px;
    font-size: 13px;
  }

  :deep(.el-button--small) {
    padding: 6px 10px;
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .courses-page {
    padding: 8px;
  }

  .card-header {
    font-size: 13px;
  }

  .mobile-button-group .el-button {
    font-size: 12px;
    padding: 8px 8px;
  }

  :deep(.el-form-item__label) {
    font-size: 12px;
  }

  :deep(.el-input__inner),
  :deep(.el-textarea__inner) {
    font-size: 13px;
  }
}

/* 平板适配 */
@media (min-width: 769px) and (max-width: 1200px) {
  .courses-page {
    padding: 16px;
  }

  :deep(.el-table) {
    font-size: 13px;
  }
}

/* 平台选择框响应式优化 */
.platform-select-item {
  position: relative;
}

.platform-select {
  width: 100%;
}

/* 下拉选项内容布局 */
.platform-option-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.platform-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 12px;
}

.platform-price {
  color: var(--el-color-primary);
  font-weight: 500;
  white-space: nowrap;
}

/* 价格提示 */
.platform-price-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 12px;
  background-color: rgba(78, 140, 255, 0.05);
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-color-primary);
}

html.dark .platform-price-hint {
  background-color: rgba(78, 140, 255, 0.1);
}

/* 平台描述样式优化 */
.platform-desc {
  margin-top: 8px;
  padding: 10px 12px;
  background-color: rgba(255, 255, 255, 0.5);
  border-radius: 6px;
  backdrop-filter: blur(10px);
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  transition: all 0.3s ease;
}

.platform-desc.is-collapsed {
  max-height: 60px;
  overflow: hidden;
  position: relative;
}

.platform-desc.is-collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 30px;
  background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.9));
}

html.dark .platform-desc.is-collapsed::after {
  background: linear-gradient(to bottom, transparent, rgba(0, 0, 0, 0.5));
}

.desc-content {
  word-break: break-word;
}

.desc-toggle {
  margin-top: 6px;
  padding: 0;
  height: auto;
  font-size: 12px;
}

/* Dark Mode Overrides for Inline Styles */
html.dark .platform-desc {
  background-color: rgba(255, 255, 255, 0.05) !important;
  color: var(--text-regular) !important;
}

/* 移动端优化 */
@media (max-width: 768px) {
  .platform-select.is-mobile :deep(.el-input__inner) {
    font-size: 14px;
  }

  .platform-option-content {
    font-size: 13px;
  }

  .platform-name {
    max-width: 60%;
  }

  .platform-price {
    font-size: 13px;
  }

  .platform-price-hint {
    font-size: 12px;
    padding: 5px 10px;
  }

  .platform-desc {
    font-size: 12px;
    padding: 8px 10px;
  }
}

/* 超小屏幕优化 */
@media (max-width: 480px) {
  .platform-option-content {
    font-size: 12px;
  }

  .platform-price {
    font-size: 12px;
  }
}

html.dark .selection-info {
  background-color: rgba(78, 140, 255, 0.1) !important;
}

html.dark .selection-info-text {
  color: var(--text-primary) !important;
}

/* 错误卡片样式 */
.error-card {
  border-color: var(--el-color-danger) !important;
}

.error-card :deep(.el-card__header) {
  background-color: rgba(240, 101, 101, 0.05);
}

html.dark .error-card :deep(.el-card__header) {
  background-color: rgba(240, 101, 101, 0.1);
}

/* 批量操作栏样式 */
.batch-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 768px) {
  .batch-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .batch-actions > div {
    width: 100%;
  }

  .batch-actions > div:last-child {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .batch-actions .el-button {
    width: 100%;
  }
}
</style>
