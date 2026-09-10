import request from '@/utils/request'
const id = (value) => encodeURIComponent(value)
const get = async (url, params, signal) => (await request.get(url, { params, signal })).data
export const projectOwners = (params, signal) => get('/admin/project-reports/owners', params, signal)
export const projectOwnerDetail = (owner, signal) => get(`/admin/project-reports/owners/${id(owner)}`, undefined, signal)
export const projectOwnerAccounts = (owner, params, signal) => get(`/admin/project-reports/owners/${id(owner)}/accounts`, params, signal)
export const projectOwnerClients = (owner, params, signal) => get(`/admin/project-reports/owners/${id(owner)}/clients`, params, signal)
export const projectLedger = (params, admin, signal) => get(admin ? '/admin/project-reports/ledger' : '/project-ledger', params, signal)
