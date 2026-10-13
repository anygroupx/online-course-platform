import request from '@/utils/request';
// 资金写入只提交白名单请求；拥有者始终由服务端登录身份决定。
export const getCourseWallets = params => request({url:'/course-wallets',method:'get',params});
export const getCourseWallet = id => request({url:`/course-wallets/${id}`,method:'get'});
export const getCourseWalletLedger = (id,params) => request({url:`/course-wallets/${id}/ledger`,method:'get',params});
export const getCourseTransfers = params => request({url:'/course-wallets/transfers',method:'get',params});
export const transferIntoCourseWallet = (id,data) => request({url:`/course-wallets/${id}/transfer-in`,method:'post',data});
export const getCourseContracts = params => request({url:'/admin/course-private-contracts',method:'get',params});
export const getCourseContractPriceReference = params => request({url:'/admin/course-private-contracts/price-reference',method:'get',params});
export const createCourseContract = data => request({url:'/admin/course-private-contracts',method:'post',data});
export const updateCourseContract = (id,data) => request({url:`/admin/course-private-contracts/${id}`,method:'put',data});
export const getCourseContractHistory = (id,params) => request({url:`/admin/course-private-contracts/${id}/history`,method:'get',params});
export const getAdminCourseWallets = params => request({url:'/admin/course-wallets',method:'get',params});
export const getAdminCourseWalletLedger = (id,params) => request({url:`/admin/course-wallets/${id}/ledger`,method:'get',params});
export const releaseCourseWallet = (id,data) => request({url:`/admin/course-wallets/${id}/release`,method:'post',data});
export const getAdminCourseTransfers = params => request({url:'/admin/course-wallet-transfers',method:'get',params});
