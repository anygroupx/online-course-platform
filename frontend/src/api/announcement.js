import request from '@/utils/request'

/**
 * 公告相关API
 * Source: 基于现有系统架构设计
 */

// 查询最新公告列表（用户端）
export function getLatestAnnouncements(limit = 5) {
  return request({
    url: '/announcement/latest',
    method: 'get',
    params: { limit }
  })
}

// 查询系统公告（用户端）
export function getSystemAnnouncement() {
  return request({
    url: '/announcement/system',
    method: 'get'
  })
}

// 查询置顶公告（用户端）
export function getTopAnnouncements() {
  return request({
    url: '/announcement/top',
    method: 'get'
  })
}

// 查询公告详情
export function getAnnouncementById(id) {
  return request({
    url: `/announcement/${id}`,
    method: 'get'
  })
}

// 管理员接口
// 分页查询公告列表
export function getAnnouncementPage(params) {
  return request({
    url: '/announcement/page',
    method: 'get',
    params
  })
}

// 创建公告
export function createAnnouncement(data) {
  return request({
    url: '/announcement/create',
    method: 'post',
    data
  })
}

// 更新公告
export function updateAnnouncement(data) {
  return request({
    url: '/announcement/update',
    method: 'put',
    data
  })
}

// 删除公告
export function deleteAnnouncement(id) {
  return request({
    url: `/announcement/${id}`,
    method: 'delete'
  })
}

// 发布公告
export function publishAnnouncement(id) {
  return request({
    url: `/announcement/${id}/publish`,
    method: 'post'
  })
}

// 下线公告
export function offlineAnnouncement(id) {
  return request({
    url: `/announcement/${id}/offline`,
    method: 'post'
  })
}
