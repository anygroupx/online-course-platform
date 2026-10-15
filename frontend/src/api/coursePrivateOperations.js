import request from '@/utils/request';
// 页面仅提交业务白名单，拥有者与审批主体由当前会话决定。
const own='/course-private-operations', admin='/admin/course-private-operations';
export const getPrivatePlans=params=>request({url:`${own}/plans`,params});
export const getPrivateRights=params=>request({url:`${own}/rights`,params});
export const getPrivateTerm=(id,isAdmin=false)=>request({url:`${isAdmin?admin:own}/terms/${id}`});
export const getPrivateApplications=(params,isAdmin=false)=>request({url:`${isAdmin?admin:own}/applications`,params});
export const submitPrivateApplication=(data,isAdmin=false)=>request({url:`${isAdmin?admin:own}/applications`,method:'post',data});
export const cancelPrivateApplication=id=>request({url:`${own}/applications/${id}/cancel`,method:'post'});
export const reviewPrivateApplication=(id,action,data)=>request({url:`${admin}/applications/${id}/${action}`,method:'post',data});
export const getPrivateReleases=(params,isAdmin=false)=>request({url:`${isAdmin?admin:own}/releases`,params});
export const submitPrivateRelease=data=>request({url:`${own}/releases`,method:'post',data});
export const cancelPrivateRelease=id=>request({url:`${own}/releases/${id}/cancel`,method:'post'});
export const reviewPrivateRelease=(id,approved,data)=>request({url:`${admin}/releases/${id}/${approved?'approve':'reject'}`,method:'post',data});
export const getPrivateContracts=params=>request({url:`${admin}/contracts`,params});
export const getAdminPrivateTerms=params=>request({url:`${admin}/terms`,params});
export const changePrivateContract=(id,data)=>request({url:`${admin}/contracts/${id}/state`,method:'post',data});
export const getAdminPrivatePlans=params=>request({url:`${admin}/plans`,params});
export const getAdminPrivatePlan=id=>request({url:`${admin}/plans/${id}`});
export const savePrivatePlan=(id,data)=>request({url:id?`${admin}/plans/${id}`:`${admin}/plans`,method:id?'put':'post',data});
export const publishPrivatePlan=(id,data)=>request({url:`${admin}/plans/${id}/publish`,method:'post',data});
export const disablePrivatePlan=(id,data)=>request({url:`${admin}/plans/${id}/disable`,method:'post',data});
export const getPrivateCosts=params=>request({url:`${admin}/costs`,params});
export const verifyPrivateCost=data=>request({url:`${admin}/costs`,method:'post',data});
export const getPrivateRisks=params=>request({url:`${admin}/risk-approvals`,params});
export const approvePrivateRisk=data=>request({url:`${admin}/risk-approvals`,method:'post',data});

// 已读写入仅对当前账户的已发布站内通知生效。
export const getPrivateNotifications=params=>request({url:`${own}/notifications`,params});
export const readPrivateNotification=id=>request({url:`${own}/notifications/${id}/read`,method:'post'});

export const getPrivateAnalytics=params=>request({url:`${admin}/analytics`,params});

export const resolvePrivateReview=(id,data)=>request({url:`${admin}/terms/${id}/resolve-review`,method:'post',data});

export const getPrivatePlanReference=(id,userUid)=>request({url:`${admin}/plans/${id}/price-reference`,params:{userUid}});

export const getPrivateTermOrders=(id,params,isAdmin=false)=>request({url:`${isAdmin?admin:own}/terms/${id}/orders`,params});
