package com.course.platform.common;

import com.course.platform.common.security.TotpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotpUtilTest {

    @Test
    @DisplayName("生成密钥并验证当前时间窗验证码")
    void generateAndVerifyCurrent() {
        String secret = TotpUtil.generateSecret();
        String code = TotpUtil.generateCode(secret);
        assertTrue(TotpUtil.verify(secret, code));
    }

    @Test
    @DisplayName("空/非法验证码应失败")
    void invalidCodes() {
        String secret = TotpUtil.generateSecret();
        assertFalse(TotpUtil.verify(secret, null));
        assertFalse(TotpUtil.verify(secret, ""));
        assertFalse(TotpUtil.verify(secret, "abcdef"));
    }
}
