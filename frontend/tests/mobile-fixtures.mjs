export const UID = '550e8400-e29b-41d4-a716-446655440000';
export const KEY = '0123456789abcdef'.repeat(3);
export const user = { uid: UID, username: 'mobile-test-user', nickname: '移动端回归测试', balance: 500, totalRecharge: 1234.56, rate: 1,
  apiEnabled: false, apiKeyPrefix: null, apiKeyExpiresAt: null, totalOrders: 16, inviteCode: 'MOBILE_TEST_INVITE', inviteRate: 1,
  isAdmin: true, agentStats: { totalAgents: 12, todayRegistered: 3, todayLogin: 4, todayOrders: 10 } };
export const order = { id: '101', orderNo: 'ORD12345678901234567890', platformId: '1', platformName: '示例课程平台',
  courseName: '移动端集成测试课程', schoolName: '示例大学', studentAccount: 'student-mobile-test', studentName: '测试学生',
  amount: 2, progress: '35%', orderStatus: 1, dockStatus: 1, retryCount: 0, createTime: '2026-09-06 10:00:00',
  userId: UID, username: 'mobile-test-user', isSelfOperated: true, countdownEndTime: '2027-09-06T23:59:59', countdownDuration: 60, examStatus: 0, status: 1 };
const platform = { id: '1', name: '示例课程平台', description: '可用课程平台', basePrice: 2, categoryId: '1', categoryName: '默认分类', status: 1, sortOrder: 1, apiProviderId: '1', platformType: 'OTHER' };
const provider = { id: '1', name: '示例接口', providerType: '29', apiUrl: 'https://provider.example', usernameMasked: 'te***st', hasApiKey: true, status: 1, balance: 100, verifiedAt: '2026-09-06 09:00:00', verifiedBy: '7', lastCheckReason: 'OK' };
const announcement = { id: '1', title: '移动端公告：长内容与弹窗操作回归', content: '<p>这是一条本地测试公告，不包含真实用户数据。</p>'.repeat(12), type: 1, priority: 1, status: 1, createTime: order.createTime };
const stats = { totalOrders: 16, todayOrders: 3, totalUsers: 12, todayNewUsers: 2, totalAmount: 1234.56, todayAmount: 50, pendingOrders: 4, processingOrders: 5, completedOrders: 7, runningCount: 1, pendingExam: 1, completed: 7, total: 16 };
const paged = (records) => ({ records, total: records.length, current: 1, size: 20 });
const variables = (type) => ['待处理', '进行中', '已完成', '已取消'].map((name, index) => ({ id: `${type}-${index}`, variableType: type, variableKey: `status_${index}`, variableName: name, variableValue: String(index), enabled: true, color: '#409eff', isDefault: index === 0 }));
export const routes = ['/dashboard', '/profile', '/api-guide', '/orders', '/courses', '/users', '/logs', '/price-list', '/recharge', '/settings', '/admin/platforms', '/admin/categories', '/admin/api-providers', '/admin/orders', '/admin/cards', '/admin/announcements', '/admin/variables', '/admin/countdown', '/admin/aqks', '/admin/customer-service', '/payment/orders', '/payment/callback', '/login', '/register', '/guest-order', '/privacy-policy', '/service-agreement'];
export function fixture(url, request, unknown) {
  const route = url.pathname.replace(/^\/api/, '');
  if (route === '/user/info') return user;
  if (route === '/theme/variables' || route === '/system/config') return [];
  if (route === '/courses') return [platform];
  if (route === '/courses/query') return { studentName: '测试学生', studentAccount: 'student', courses: [{ id: 'c1', name: '测试课程', selected: true }] };
  if (route === '/admin/platforms') return paged([platform]);
  if (route === '/admin/platform-categories') return paged([{ id: '1', name: '默认分类', sortOrder: 1, status: 1 }]);
  if (route === '/admin/api-providers') return paged([provider]);
  if (route === '/statistics' || route.endsWith('/statistics')) return stats;
  if (route === '/users') return paged([{ ...user, uid: '550e8400-e29b-41d4-a716-446655440001', status: 1, createTime: order.createTime }]);
  if (route === '/orders/query' || route === '/admin/orders/query-all') return paged([order]);
  if (route === '/admin/orders/agent-accounts') return [{ uid: UID, username: user.username }];
  if (route === '/admin/orders/countdown' || route === '/admin/orders/exam-countdown') return [order];
  if (route.includes('countdown-history')) return [];
  if (route.startsWith('/admin/countdown-config/')) return {};
  if (route === '/logs' || route === '/logs/search') return paged([{ id: '1', username: user.username, operationType: '下单', operationDesc: '移动端回归测试日志：这是一条较长的订单操作记录。', createTime: order.createTime, ipAddress: '127.0.0.1', amountChange: -2, balanceAfter: 500 }]);
  if (route === '/announcement/latest' || route === '/announcement/top') return [announcement];
  if (route === '/announcement/system') return null;
  if (route === '/announcement/page') return paged([announcement]);
  if (route.startsWith('/announcement/')) return announcement;
  if (route === '/admin/variables/types') return ['order_status', 'dock_status'];
  if (route.startsWith('/admin/variables/type/')) return variables(route.split('/').at(-1));
  if (route === '/admin/variables') return paged(variables('order_status'));
  if (route === '/cards' || route === '/cards/query') return paged([{ id: '1', cardNo: '1234567890123456', amount: 100, status: 0, createTime: order.createTime }]);
  if (route === '/payment/orders') return paged([{ orderNo: 'PAY12345678901234567890', amount: 10, status: 'PAID', paymentType: 'WAP', createTime: order.createTime, paidTime: order.createTime }]);
  if (route === '/payment/config') return [];
  if (route === '/customer-service/unread-count') return 0;
  if (route === '/customer-service/admin/sessions') return [{ sessionId: 'chat-test', userName: '测试用户', userAccount: 'test-account', status: 1, lastMessageContent: '手机端咨询信息', unreadCount: 1, createTime: order.createTime }];
  if (route === '/customer-service/session') return { sessionId: 'chat-test', status: 2 };
  if (route.endsWith('/messages')) return [{ content: '这是一条很长的测试消息。'.repeat(12), senderType: 2, senderName: '客服', createTime: order.createTime }];
  if (route.endsWith('/read') || route === '/customer-service/message') return true;
  if (route === '/api-keys/enable' || route === '/api-keys/rotate') { user.apiEnabled = true; user.apiKeyPrefix = KEY.slice(0,8); user.apiKeyExpiresAt = '2027-09-06 10:00:00'; return KEY; }
  if (route === '/register/validate-invite-code') return { valid: true };
  if (route === '/admin/aqks/running/batch') return { '101': false };
  if (route.startsWith('/external/')) {
    if (route.endsWith('/getmoney')) return { money: 500 };
    if (route.endsWith('/get-platforms')) return [{ id: '1', name: platform.name, price: 2 }];
    if (route.endsWith('/query-courses')) return { courses: [] };
    if (route.endsWith('/chadan')) return [{ orderNo: order.orderNo }];
    return { orderNo: order.orderNo, progress: '35%' };
  }
  unknown.add(`${request.method()} ${url.pathname}`);
  return [];
}
