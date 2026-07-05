import request from '@/utils/request'

/**
 * 生成充值卡密
 */
export function generateCards(data) {
  return request({
    url: '/cards/generate',
    method: 'post',
    data
  })
}

/**
 * 查询充值卡密列表
 */
export function queryCards(data) {
  return request({
    url: '/cards',
    method: 'get',
    params: data
  })
}

/**
 * 获取卡密详情
 */
export function getCard(id) {
  return request({
    url: `/cards/${id}`,
    method: 'get'
  })
}

/**
 * 禁用卡密
 */
export function disableCard(id) {
  return request({
    url: `/cards/${id}/disable`,
    method: 'post'
  })
}

/**
 * 用户自助充值
 */
export function rechargeByCard(data) {
  return request({
    url: '/cards/recharge',
    method: 'post',
    data
  })
}
