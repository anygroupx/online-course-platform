export const isSelfOperatedHeisha = (order) => order?.providerType === 'heisha' && order.fulfillmentMode === 'SELF_OPERATED';
export const fulfillmentActionNames = Object.freeze({
  START: '开始处理', PROGRESS: '更新进度', COMPLETE: '标记完成', ATTENTION: '标记异常', RESUME: '恢复处理',
  CANCEL_REFUND: '取消并全额退款',
});
export function fulfillmentActions(order) {
  if (!isSelfOperatedHeisha(order) || order.pendingOperationId) return [];
  if (order.status === 'ACTIVE' && order.completed >= order.quantity) return ['COMPLETE', 'ATTENTION'];
  if (order.status === 'PENDING') {
    const actions = [];
    if (order.verificationStatus === 'VERIFIED') actions.push('START');
    actions.push('ATTENTION');
    if (order.completed === 0) actions.push('CANCEL_REFUND');
    return actions;
  }
  return ({ ACTIVE: ['PROGRESS', 'COMPLETE', 'ATTENTION'], ATTENTION: ['RESUME'] })[order.status] || [];
}
export function fulfillmentCommand(order, action, completed, note = '') {
  if (!fulfillmentActions(order).includes(action)) throw new Error('当前状态不支持此操作');
  if (!Number.isSafeInteger(order.version) || order.version < 0) throw new Error('请刷新订单后重试');
  const command = { action, orderVersion: order.version, note: note.trim() || null };
  if (['ATTENTION', 'RESUME', 'CANCEL_REFUND'].includes(action) && !command.note) throw new Error('请填写处理备注');
  if (action === 'PROGRESS') {
    if (!Number.isSafeInteger(completed) || completed <= order.completed || completed > order.quantity) throw new Error('请输入大于当前进度且不超过总量的整数');
    command.completed = completed;
  }
  return command;
}

export function fulfillmentVerificationActions(order, status = order?.verificationStatus) {
  if (!isSelfOperatedHeisha(order) || order.status !== 'PENDING' || order.pendingOperationId) return [];
  return ({ PENDING: ['VERIFY', 'NEEDS_INFO', 'REJECT'], NEEDS_INFO: ['VERIFY', 'REJECT'] })[status] || [];
}
export function canSupplementFulfillment(order) {
  return isSelfOperatedHeisha(order) && order.status === 'PENDING'
    && order.verificationStatus === 'NEEDS_INFO' && !order.pendingOperationId;
}
