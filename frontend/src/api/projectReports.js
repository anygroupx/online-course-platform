import request from '@/utils/request'

const get = async (url, params, signal) => (await request.get(url, { params, signal })).data
export const ownerProjectUsage = (signal) => get('/project-clients/usage', undefined, signal)
export const projectUsageBalances = (page, signal) => get('/project-clients/usage/projects', { page, pageSize: 20 }, signal)
export const systemProjectOverview = (signal) => get('/admin/project-reports/overview', undefined, signal)
