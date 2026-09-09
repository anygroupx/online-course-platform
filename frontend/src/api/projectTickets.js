import request from "@/utils/request";
const key = (value) => encodeURIComponent(value);
const root = (admin) => (admin ? "/admin" : "");
const read = async (url, params) =>
  (await request.get(url, { params, timeout: 125000 })).data;
const write = async (url, data = {}) =>
  (await request.post(url, data, { timeout: 125000, __sessionRetry: true }))
    .data;
export const listProjectTickets = (params, admin = false) =>
  read(`${root(admin)}/project-tickets`, params);
export const getProjectTicket = (id, admin = false) =>
  read(`${root(admin)}/project-tickets/${key(id)}`);
export const refreshProjectTicket = (id, admin = false) =>
  write(`${root(admin)}/project-tickets/${key(id)}/refresh`);
export const prepareProjectTicket = (accountId, form) =>
  write(`/project-accounts/${key(accountId)}/tickets`, form);
export const prepareProjectReply = (id, form) =>
  write(`/project-tickets/${key(id)}/reply-quotes`, form);
export const prepareTicketReview = (id, form) =>
  write(`/admin/project-tickets/${key(id)}/review-quotes`, form);
export const getTicketOperation = (id, admin = false) =>
  read(`${root(admin)}/project-ticket-operations/${key(id)}`);
export const confirmTicketOperation = (id, admin = false) =>
  write(`${root(admin)}/project-ticket-operations/${key(id)}/confirm`);
export const resolveTicketOperation = (id, form) =>
  write(`/admin/project-ticket-operations/${key(id)}/resolve`, form);
