/**
 * 管理员订单筛选配置
 *
 * 支持的字段类型：
 * - input: 文本输入框
 * - select: 下拉选择器
 * - date/datetime: 日期选择器
 * - daterange/datetimerange: 日期范围选择器
 * - number: 数字输入框
 * - switch: 开关
 * - checkbox: 多选框组
 * - radio: 单选框组
 */

export const adminOrdersFilterConfig = {
  // 常用筛选（桌面端始终显示，移动端在抽屉内）
  common: [
    {
      key: "orderNo",
      label: "订单编号",
      type: "input",
      placeholder: "输入订单编号",
      width: "200px",
      clearable: true,
    },
    {
      key: "platformId",
      label: "平台",
      type: "select",
      placeholder: "选择平台",
      width: "150px",
      // 方式1：从 options prop 中获取
      optionsKey: "platformList",
      labelKey: "name",
      valueKey: "id",
    },
    {
      key: "orderStatus",
      label: "订单状态",
      type: "select",
      placeholder: "选择状态",
      width: "120px",
      // 方式2：动态函数获取
      optionsGetter: (options) => options.statusOptions?.order_status || [],
    },
  ],

  // 高级筛选（桌面端折叠面板，移动端在抽屉内）
  advanced: [
    {
      key: "studentAccount",
      label: "学生账号",
      type: "input",
      placeholder: "输入学生账号",
      width: "150px",
    },
    {
      key: "dockStatus",
      label: "对接状态",
      type: "select",
      placeholder: "选择对接状态",
      width: "120px",
      optionsGetter: (options) => options.statusOptions?.dock_status || [],
    },
    {
      key: "userId",
      label: "代理账号",
      type: "select",
      placeholder: "选择代理",
      width: "150px",
      optionsKey: "agentList",
      labelKey: "username",
      valueKey: "id",
      // 自定义 label 渲染
      optionLabelFormatter: (agent) => {
        return agent.username + (agent.nickname ? ` (${agent.nickname})` : "");
      },
    },
    {
      key: "dateRange",
      label: "创建时间",
      type: "datetimerange",
      width: "350px",
      startPlaceholder: "开始时间",
      endPlaceholder: "结束时间",
      format: "YYYY-MM-DD HH:mm:ss",
      valueFormat: "YYYY-MM-DD HH:mm:ss",
    },
  ],
};

// 默认查询表单值
export const defaultQueryForm = {
  orderNo: "",
  platformId: null,
  studentAccount: "",
  orderStatus: null,
  dockStatus: null,
  userId: null,
  dateRange: [],
  page: 1,
  pageSize: 10,
};
