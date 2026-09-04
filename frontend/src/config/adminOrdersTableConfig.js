/**
 * 管理员订单列表配置
 *
 * 列类型说明：
 * - key: 字段名（必填）
 * - label: 列标题（必填）
 * - width: 列宽度
 * - minWidth: 最小宽度
 * - sortable: 是否可排序
 * - align: 对齐方式 (left/center/right)
 * - fixed: 固定列 (left/right/false)
 * - hidden: 是否隐藏
 * - hideInTable: 桌面端隐藏
 * - formatter: 格式化函数 (row, column, index) => string
 * - component: 自定义组件
 * - componentProps: 组件属性
 */

import StatusDisplay from "@/components/StatusDisplay.vue";

export const adminOrdersColumns = [
  {
    key: "orderNo",
    label: "订单编号",
    width: 180,
    sortable: true,
    fixed: "left",
  },
  {
    key: "platformName",
    label: "平台",
    width: 120,
    formatter: (row) => row.platform?.name || "-",
  },
  {
    key: "courseName",
    label: "课程名称",
    minWidth: 200,
  },
  {
    key: "studentAccount",
    label: "学生账号",
    width: 150,
  },
  {
    key: "orderStatus",
    label: "订单状态",
    width: 100,
    align: "center",
    component: StatusDisplay,
    componentProps: (row) => ({
      status: row.orderStatus,
      type: "order_status",
    }),
  },
  {
    key: "dockStatus",
    label: "对接状态",
    width: 100,
    align: "center",
    component: StatusDisplay,
    componentProps: (row) => ({
      status: row.dockStatus,
      type: "dock_status",
    }),
  },
  {
    key: "agentName",
    label: "代理账号",
    width: 120,
    formatter: (row) => {
      if (!row.user) return "-";
      return (
        row.user.username + (row.user.nickname ? ` (${row.user.nickname})` : "")
      );
    },
  },
  {
    key: "price",
    label: "价格",
    width: 100,
    align: "right",
    formatter: (row) => `¥${row.price || 0}`,
  },
  {
    key: "originalPrice",
    label: "原价",
    width: 100,
    align: "right",
    formatter: (row) => (row.originalPrice ? `¥${row.originalPrice}` : "-"),
  },
  {
    key: "remark",
    label: "备注",
    minWidth: 150,
    formatter: (row) => row.remark || "-",
  },
  {
    key: "createdAt",
    label: "创建时间",
    width: 180,
    sortable: true,
  },
  {
    key: "updatedAt",
    label: "更新时间",
    width: 180,
    sortable: true,
  },
];

// 行操作配置
export const adminOrdersActions = [
  {
    key: "view",
    label: "查看",
    icon: "View",
    type: "primary",
    link: true,
  },
  {
    key: "status",
    label: "状态",
    icon: "Edit",
    type: "warning",
    link: true,
    show: (row) => row.orderStatus !== 2, // 已完成不显示
  },
  {
    key: "dock",
    label: "对接",
    icon: "Connection",
    type: "success",
    link: true,
  },
  {
    key: "remark",
    label: "备注",
    icon: "EditPen",
    link: true,
  },
  {
    key: "retry",
    label: "重试",
    icon: "RefreshRight",
    type: "info",
    link: true,
    show: (row) => row.orderStatus === 4, // 仅失败订单显示
  },
  {
    key: "delete",
    label: "删除",
    icon: "Delete",
    type: "danger",
    link: true,
  },
];

// 分页配置
export const defaultPagination = {
  currentPage: 1,
  pageSize: 10,
  total: 0,
  pageSizes: [10, 20, 50, 100],
};

// 移动端显示的列（优先显示）
export const mobileVisibleColumns = [
  "orderNo",
  "platformName",
  "orderStatus",
  "createdAt",
];

// 表格配置（el-table props）
export const tableConfig = {
  stripe: true,
  border: true,
  highlightCurrentRow: true,
};
