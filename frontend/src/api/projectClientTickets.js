import request from '@/utils/request'
const id = encodeURIComponent
const get = async (url, params) => (await request.get(url, { params })).data
// Writes are never retried by the shared authentication interceptor. Recovery is GET-only.
const post = async (url, data) => (await request.post(url, data, { __sessionRetry: true })).data
export const listClientTickets = (params) => get('/project-client-tickets', params)
export const clientTicket = (key) => get(`/project-client-tickets/${id(key)}`)
export const clientTicketReplies = (key, params) => get(`/project-client-tickets/${id(key)}/replies`, params)
export const clientTicketRequest = (key) => get(`/project-client-tickets/by-request/${id(key)}`)
export const createClientTicket = (data) => post('/project-client-tickets', data)
export const replyClientTicket = (key, data) => post(`/project-client-tickets/${id(key)}/replies`, data)
export const decideClientTicket = (key, data) => post(`/project-client-tickets/${id(key)}/decision`, data)
