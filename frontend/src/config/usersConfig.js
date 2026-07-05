// Source: AURA-X-KYS 代理管理配置
// Users.vue 企业级通用组件配置

import {
  Key,
  CreditCard,
  Lock,
  CircleCheck,
  CircleClose,
} from "@element-plus/icons-vue";

/**
 * 筛选配置
 */
export const filterConfig = {
  // 常用筛选
  common: [
    {
      key: "keyword",
      label: "搜索",
      type: "input",
      placeholder: "搜索用户名/昵称",
      clearable: true,
      width: "200px",
    },
  ],
  advanced: [],
};

/**
 * 表格列配置
 */
export const columnsConfig = [
  {
    key: "id",
    label: "UID",
    width: 80,
    visible: true,
  },
  {
    key: "username",
    label: "用户名",
    width: 120,
    visible: true,
  },
  {
    key: "nickname",
    label: "昵称",
    width: 120,
    visible: true,
  },
  {
    key: "balance",
    label: "余额",
    width: 100,
    visible: true,
    formatter: (row) => `¥${row.balance}`,
  },
  {
    key: "rate",
    label: "费率",
    width: 80,
    visible: true,
  },
  {
    key: "totalRecharge",
    label: "总充值",
    width: 100,
    visible: true,
    formatter: (row) => `¥${row.totalRecharge || 0}`,
  },
  {
    key: "apiKey",
    label: "API密钥",
    width: 120,
    visible: true,
    // 使用插槽渲染，在视图文件中实现
  },
  {
    key: "inviteCode",
    label: "邀请码",
    width: 120,
    visible: true,
    // 使用插槽渲染，在视图文件中实现
  },
  {
    key: "inviteRate",
    label: "邀请费率",
    width: 100,
    visible: true,
    formatter: (row) => row.inviteRate || "未设置",
  },
  {
    key: "status",
    label: "状态",
    width: 80,
    visible: true,
    // 使用插槽渲染，在视图文件中实现
  },
  {
    key: "createTime",
    label: "创建时间",
    width: 160,
    visible: true,
  },
];

/**
 * 行操作配置
 *
 * 注意：EnterpriseTable 支持函数形式的配置
 * - label: 可以是字符串或函数 (row) => string
 * - type: 可以是字符串或函数 (row) => string
 * - icon: 可以是组件或函数 (row) => component
 * - show: 函数 (row) => boolean 控制显示
 * - disabled: 函数 (row) => boolean 控制禁用
 */
export const rowActionsConfig = [
  {
    key: "recharge",
    label: "充值",
    type: "primary",
    size: "small",
  },
  {
    key: "resetPassword",
    label: "重置密码",
    type: "default",
    size: "small",
  },
  {
    key: "inviteCode",
    label: "邀请码",
    type: "success",
    size: "small",
  },
  {
    key: "toggleStatus",
    label: "状态",
    type: "default",
    size: "small",
  },
];

/**
 * 移动端卡片显示列
 */
export const mobileColumns = ["username", "balance", "rate", "status"];
