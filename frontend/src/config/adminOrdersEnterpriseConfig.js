// Source: AURA-X-KYS 企业级订单管理完整配置示例
// 展示如何使用新的企业级通用组件构建复杂的管理页面

import { Document, Check, Money, Calendar } from "@element-plus/icons-vue";
import StatusDisplay from "@/components/StatusDisplay.vue";

/**
 * 统计卡片配置
 * 用于 TableStatistics 组件
 */
export const statisticsConfig = [
  {
    key: "total",
    label: "总订单数",
    value: 0,
    icon: Document,
    iconClass: "primary",
    color: "#409eff",
  },
  {
    key: "completed",
    label: "已完成",
    value: 0,
    icon: Check,
    iconClass: "success",
    color: "#67c23a",
  },
  {
    key: "totalAmount",
    label: "总金额",
    value: 0,
    icon: Money,
    iconClass: "warning",
    color: "#e6a23c",
    extra: "元",
  },
  {
    key: "today",
    label: "今日订单",
    value: 0,
    icon: Calendar,
    iconClass: "info",
    color: "#909399",
  },
];

/**
 * 筛选配置
 * 用于 EnterpriseFilter 组件
 */
export const filterConfig = {
  // 常用筛选（始终显示）
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
      optionsKey: "platformList", // 从父组件 options 中获取
      labelKey: "platformName",
      valueKey: "id",
    },
    {
      key: "orderStatus",
      label: "订单状态",
      type: "select",
      placeholder: "请选择订单状态",
      clearable: true,
      width: "150px",
      // 直接配置选项
      options: [
        { label: "待支付", value: 0 },
        { label: "处理中", value: 1 },
        { label: "已完成", value: 2 },
        { label: "已取消", value: 3 },
        { label: "失败", value: 4 },
      ],
    },
  ],
  // 高级筛选（折叠面板）
  advanced: [
    {
      key: "studentAccount",
      label: "学生账号",
      type: "input",
      placeholder: "请输入学生账号",
      clearable: true,
      width: "200px",
    },
    {
      key: "dockStatus",
      label: "对接状态",
      type: "select",
      placeholder: "请选择对接状态",
      clearable: true,
      width: "150px",
      options: [
        { label: "未对接", value: 0 },
        { label: "对接中", value: 1 },
        { label: "已对接", value: 2 },
        { label: "对接失败", value: 3 },
      ],
    },
    {
      key: "userId",
      label: "代理账号",
      type: "select",
      placeholder: "请选择代理账号",
      clearable: true,
      width: "150px",
      optionsKey: "agentList",
      labelKey: "username",
      valueKey: "id",
    },
    {
      key: "dateRange",
      label: "创建时间",
      type: "daterange",
      startPlaceholder: "开始日期",
      endPlaceholder: "结束日期",
      width: "240px",
      format: "YYYY-MM-DD",
      valueFormat: "YYYY-MM-DD",
    },
  ],
};

/**
 * 表格列配置
 * 用于 EnterpriseTable 组件
 */
export const columnsConfig = [
  {
    key: "id",
    label: "ID",
    width: 80,
    sortable: true,
    visible: true,
  },
  {
    key: "orderNo",
    label: "订单编号",
    width: 200,
    sortable: true,
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
    width: 150,
    visible: true,
  },
  {
    key: "courseName",
    label: "课程名称",
    minWidth: 200,
    visible: true,
  },
  {
    key: "amount",
    label: "金额",
    width: 100,
    sortable: true,
    visible: true,
    // 使用格式化函数
    formatter: (row) => {
      return `¥${row.amount}`;
    },
  },
  {
    key: "progress",
    label: "进度",
    width: 100,
    visible: true,
    formatter: (row) => {
      return row.progress ? `${row.progress}%` : "-";
    },
  },
  {
    key: "orderStatus",
    label: "订单状态",
    width: 150,
    visible: true,
    // 使用自定义组件
    component: StatusDisplay,
    componentProps: (row) => ({
      status: row.orderStatus,
      type: "order_status",
    }),
  },
  {
    key: "dockStatus",
    label: "对接状态",
    width: 150,
    visible: true,
    component: StatusDisplay,
    componentProps: (row) => ({
      status: row.dockStatus,
      type: "dock_status",
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
    sortable: true,
    visible: true,
  },
];

/**
 * 行操作配置
 * 用于 EnterpriseTable 组件
 */
export const rowActionsConfig = [
  {
    key: "view",
    label: "查看",
    type: "primary",
    size: "small",
    link: true,
    show: true,
  },
  {
    key: "edit",
    label: "编辑",
    type: "primary",
    size: "small",
    link: true,
    // 动态显示：只有待支付订单才显示编辑
    show: (row) => row.orderStatus === 0,
  },
  {
    key: "retry",
    label: "补单",
    type: "warning",
    size: "small",
    link: true,
    // 动态显示：失败订单才显示补单
    show: (row) => row.orderStatus === 4,
  },
  {
    key: "delete",
    label: "删除",
    type: "danger",
    size: "small",
    link: true,
    // 动态显示：已取消和失败的订单才能删除
    show: (row) => [3, 4].includes(row.orderStatus),
  },
];

/**
 * 批量操作配置
 * 用于 TableBatchActions 组件
 */
export const batchActionsConfig = [
  {
    key: "complete",
    label: "批量完成",
    type: "success",
    icon: Check,
    // 动态显示：只有处理中的订单才能批量完成
    show: (selectedRows) => {
      return selectedRows.every((row) => row.orderStatus === 1);
    },
  },
  {
    key: "cancel",
    label: "批量取消",
    type: "warning",
    // 动态显示：待支付和处理中的订单才能取消
    show: (selectedRows) => {
      return selectedRows.every((row) => [0, 1].includes(row.orderStatus));
    },
  },
  {
    key: "delete",
    label: "批量删除",
    type: "danger",
    // 动态显示：已取消和失败的订单才能删除
    show: (selectedRows) => {
      return selectedRows.every((row) => [3, 4].includes(row.orderStatus));
    },
  },
  {
    key: "export",
    label: "导出",
    type: "primary",
    show: true,
  },
];

/**
 * 分页配置
 * 用于 EnterpriseTable 组件
 */
export const paginationConfig = {
  currentPage: 1,
  pageSize: 10,
  total: 0,
  pageSizes: [10, 20, 50, 100],
};
