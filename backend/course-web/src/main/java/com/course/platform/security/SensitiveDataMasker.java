package com.course.platform.security;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.ApiProviderVO;
import com.course.platform.domain.vo.CourseOrderVO;
import com.course.platform.domain.vo.PaymentConfigVO;
import com.course.platform.domain.vo.UserVO;
import org.springframework.util.StringUtils;

/**
 * 实体 -> 安全 VO 转换
 */
public final class SensitiveDataMasker {
    private SensitiveDataMasker() {}

    public static UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        boolean apiEnabled = StringUtils.hasText(user.getApiKeyHash());
        return UserVO.builder()
                .uid(user.getUid())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .balance(user.getBalance())
                .totalRecharge(user.getTotalRecharge())
                .rate(user.getRate())
                .apiEnabled(apiEnabled)
                .apiKeyPrefix(user.getApiKeyPrefix())
                .inviteCode(user.getInviteCode())
                .inviteRate(user.getInviteRate())
                .notice(user.getNotice())
                .status(user.getStatus())
                .role(user.getRole())
                .mustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1)
                .createTime(user.getCreateTime())
                .lastLoginTime(user.getLastLoginTime())
                .lastLoginIp(maskIp(user.getLastLoginIp()))
                .build();
    }

    public static CourseOrderVO toOrderVO(CourseOrder order) {
        return toOrderVO(order, null);
    }

    public static CourseOrderVO toOrderVO(CourseOrder order, String userUid) {
        if (order == null) {
            return null;
        }
        return CourseOrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userUid(userUid)
                .platformId(order.getPlatformId())
                .platformName(order.getPlatformName())
                .schoolName(order.getSchoolName())
                .studentName(order.getStudentName())
                .studentAccount(order.getStudentAccount())
                .studentPhoneMasked(maskPhone(order.getStudentPhone()))
                .courseId(order.getCourseId())
                .courseName(order.getCourseName())
                .courseStartTime(order.getCourseStartTime())
                .courseEndTime(order.getCourseEndTime())
                .examStartTime(order.getExamStartTime())
                .examEndTime(order.getExamEndTime())
                .totalChapters(order.getTotalChapters())
                .finishedChapters(order.getFinishedChapters())
                .progress(order.getProgress())
                .amount(order.getAmount())
                .isFastMode(order.getIsFastMode())
                .retryCount(order.getRetryCount())
                .orderStatus(order.getOrderStatus())
                .dockStatus(order.getDockStatus())
                .loginStatus(order.getLoginStatus())
                .remarks(order.getRemarks())
                .isSelfOperated(order.getIsSelfOperated())
                .countdownDuration(order.getCountdownDuration())
                .countdownStartTime(order.getCountdownStartTime())
                .countdownEndTime(order.getCountdownEndTime())
                .autoCompleteEnabled(order.getAutoCompleteEnabled())
                .examCountdownDuration(order.getExamCountdownDuration())
                .examCountdownStartTime(order.getExamCountdownStartTime())
                .examCountdownEndTime(order.getExamCountdownEndTime())
                .examAutoCompleteEnabled(order.getExamAutoCompleteEnabled())
                .createIp(maskIp(order.getCreateIp()))
                .createTime(order.getCreateTime())
                .updateTime(order.getUpdateTime())
                .build();
    }

    public static ApiProviderVO toApiProviderVO(ApiProvider provider) {
        if (provider == null) {
            return null;
        }
        return ApiProviderVO.builder()
                .id(provider.getId())
                .providerType(provider.getProviderType())
                .name(provider.getName())
                .apiUrl(provider.getApiUrl())
                .usernameMasked(maskMiddle(provider.getUsername()))
                .hasPassword(StringUtils.hasText(provider.getPassword()))
                .hasToken(StringUtils.hasText(provider.getToken()))
                .hasApiKey(StringUtils.hasText(provider.getApiKey()))
                .hasCookie(StringUtils.hasText(provider.getCookie()))
                .balance(provider.getBalance())
                .lastSyncTime(provider.getLastSyncTime())
                .status(provider.getStatus())
                .createTime(provider.getCreateTime())
                .updateTime(provider.getUpdateTime())
                .build();
    }

    public static PaymentConfigVO toPaymentConfigVO(PaymentConfig config) {
        if (config == null) {
            return null;
        }
        return PaymentConfigVO.builder()
                .id(config.getId())
                .configName(config.getConfigName())
                .envType(config.getEnvType())
                .appIdMasked(maskMiddle(config.getAppId()))
                .hasPrivateKey(StringUtils.hasText(config.getPrivateKey()))
                .hasAlipayPublicKey(StringUtils.hasText(config.getAlipayPublicKey()))
                .signType(config.getSignType())
                .format(config.getFormat())
                .charset(config.getCharset())
                .gatewayUrl(config.getGatewayUrl())
                .notifyUrl(config.getNotifyUrl())
                .returnUrl(config.getReturnUrl())
                .isActive(config.getIsActive())
                .status(config.getStatus())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }

    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        int idx = ip.lastIndexOf('.');
        if (idx > 0) {
            return ip.substring(0, idx) + ".*";
        }
        return "***";
    }

    public static String maskMiddle(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
