// Source: AURA-X-KYS 用户端订单管理配置
// Orders.vue 企业级通用组件配置

import { Download, View, Refresh, Close } from "@element-plus/icons-vue";
import StatusDisplay from "@/components/StatusDisplay.vue";

/**
 * 筛选配置
 */
export const filterConfig = {
  // 常用筛选
  common: [
    {
      key: "orderNo",
      label: "订单编号",
      type: "input",
      placeholder: "请输入订单编号",
      clearable: true,
      width: "200px",
    },
    {
      key: "platformId",
      label: "平台",
      type: "select",
      placeholder: "请选择平台",
      clearable: true,
      width: "150px",
      optionsKey: "platformList",
      labelKey: "name",
      valueKey: "id",
    },
    {
      key: "studentAccount",
      label: "学生账号",
      type: "input",
      placeholder: "请输入学生账号",
      clearable: true,
      width: "150px",
    },
    {
      key: "orderStatus",
      label: "订单状态",
      type: "select",
      placeholder: "请选择订单状态",
      clearable: true,
      width: "120px",
      // 动态从 variableStore 获取选项
      optionsGetter: (options) => options.orderStatusOptions || [],
    },
  ],
  advanced: [],
};

/**
 * 表格列配置
 */
export const columnsConfig = [
  {
    key: "orderNo",
    label: "订单编号",
    width: 200,
    visible: true,
  },
  {
    key: "platformName",
    label: "平台",
    width: 120,
    visible: true,
  },
  {
    key: "studentAccount",
    label: "学生账号",
    width: 120,
    visible: true,
  },
  {
    key: "studentPassword",
    label: "学生密码",
    width: 120,
    visible: true,
    formatter: (row) => row.studentPassword || "-",
  },
  {
    key: "courseName",
    label: "课程名称",
    minWidth: 150,
    visible: true,
  },
  {
    key: "amount",
    label: "金额",
    width: 100,
    visible: true,
    // 使用自定义插槽显示红色金额
  },
  {
    key: "progress",
    label: "进度",
    width: 100,
    visible: true,
    formatter: (row) => row.progress || "0%",
  },
  {
    key: "orderStatus",
    label: "订单状态",
    width: 100,
    visible: true,
    component: StatusDisplay,
    componentProps: (row) => ({
      value: row.orderStatus,
      type: "order_status",
    }),
  },
  {
    key: "retryCount",
    label: "补单次数",
    width: 100,
    visible: true,
  },
  {
    key: "createTime",
    label: "创建时间",
    width: 160,
    visible: true,
    sortable: true, // 启用排序
  },
];

/**
 * 默认排序配置
 */
export const defaultSort = {
  prop: "createTime",
  order: "descending", // descending = 降序（倒序），ascending = 升序
};

/**
 * 行操作配置
 */
export const rowActionsConfig = [
  {
    key: "view",
    label: "详情",
    type: "primary",
    size: "small",
    show: true,
  },
  {
    key: "retry",
    label: "补单",
    type: "warning",
    size: "small",
    // 动态禁用：补单次数>=5时禁用
    show: true,
    disabled: (row) => row.retryCount >= 5,
  },
  {
    key: "refresh",
    label: "刷新",
    type: "info",
    size: "small",
    show: true,
  },
  {
    key: "cancel",
    label: "取消",
    type: "danger",
    size: "small",
    // 只有待处理(0)和失败(4)的订单才能取消
    show: (row) => row.orderStatus === 0 || row.orderStatus === 4,
  },
];

/**
 * 批量操作配置
 */
export const batchActionsConfig = [
  {
    key: "export",
    label: "导出选中",
    type: "warning",
    icon: Download,
    show: true,
  },
];

/**
 * 分页配置
 */
export const paginationConfig = {
  currentPage: 1,
  pageSize: 10,
  total: 0,
  pageSizes: [10, 20, 50, 100],
};

/**
 * 移动端卡片显示的列
 */
export const mobileColumns = [
  "studentAccount",
  "platformName",
  "courseName",
  "amount",
];
