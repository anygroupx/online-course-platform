package com.course.platform.application.service.security;

import com.course.platform.domain.dto.MfaCodeRequest;
import com.course.platform.domain.dto.MfaConfirmSetupRequest;
import com.course.platform.domain.dto.MfaVerifyLoginRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.domain.vo.MfaSetupVO;
import com.course.platform.domain.vo.MfaStatusVO;

public interface MfaService {
    MfaStatusVO status(Long userId);

    MfaSetupVO beginSetup(Long userId);

    void confirmSetup(Long userId, MfaConfirmSetupRequest request);

    void disable(Long userId, MfaCodeRequest request);

    boolean isEnabled(User user);

    String createChallenge(User user);

    LoginResponse verifyLogin(MfaVerifyLoginRequest request);
}
