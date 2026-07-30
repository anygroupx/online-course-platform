<template>
  <div class="login-container">
    <div class="background-overlay"></div>
    <div class="login-box">
      <div class="login-header">
        <div class="logo-icon">
          <i class="el-icon-reading"></i>
        </div>
        <h2>二开台</h2>
        <p v-if="mfaStep" class="mfa-hint">管理员账号已启用 MFA，请输入动态验证码</p>
      </div>

      <el-form
        v-if="!mfaStep"
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleLogin"
            class="login-button"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登录中...</span>
          </el-button>
        </el-form-item>

        <div class="login-footer">
          <span class="footer-text">还没有账号？</span>
          <el-link type="primary" :underline="false" @click="goToRegister">立即注册</el-link>
          <div style="margin-top: 10px;">
            <el-link type="info" :underline="false" @click="$router.push('/service-agreement')" style="font-size: 12px;">服务协议</el-link>
          </div>
        </div>
      </el-form>

      <el-form
        v-else
        ref="mfaFormRef"
        :model="mfaForm"
        :rules="mfaRules"
        class="login-form"
        @submit.prevent="handleMfaVerify"
      >
        <el-form-item prop="code">
          <el-input
            v-model="mfaForm.code"
            placeholder="请输入 6 位 TOTP 或备用恢复码"
            size="large"
            maxlength="16"
            clearable
            @keyup.enter="handleMfaVerify"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleMfaVerify"
            class="login-button"
          >
            <span v-if="!loading">验证并登录</span>
            <span v-else>验证中...</span>
          </el-button>
        </el-form-item>
        <div class="login-footer">
          <el-link type="info" :underline="false" @click="backToPassword">返回账号密码登录</el-link>
        </div>
      </el-form>
    </div>

    <div class="decorative-elements">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { User, Lock } from "@element-plus/icons-vue";
import { useUserStore } from "@/stores/user";

const router = useRouter();
const userStore = useUserStore();

const loginFormRef = ref(null);
const mfaFormRef = ref(null);
const loading = ref(false);
const mfaStep = ref(false);
const mfaChallengeId = ref("");

const loginForm = reactive({
  username: "",
  password: "",
});

const mfaForm = reactive({
  code: "",
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, message: "用户名至少3个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码至少6个字符", trigger: "blur" },
  ],
};

const mfaRules = {
  code: [
    { required: true, message: "请输入验证码", trigger: "blur" },
    { min: 6, message: "验证码至少6位", trigger: "blur" },
  ],
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        const result = await userStore.login(loginForm);
        if (result?.mfaRequired) {
          mfaStep.value = true;
          mfaChallengeId.value = result.mfaChallengeId;
          mfaForm.code = "";
        }
      } catch (error) {
        console.error("登录失败：", error);
      } finally {
        loading.value = false;
      }
    }
  });
};

const handleMfaVerify = async () => {
  if (!mfaFormRef.value) return;
  await mfaFormRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      await userStore.verifyMfaLogin({
        challengeId: mfaChallengeId.value,
        code: mfaForm.code,
      });
    } catch (error) {
      console.error("MFA 验证失败：", error);
    } finally {
      loading.value = false;
    }
  });
};

const backToPassword = () => {
  mfaStep.value = false;
  mfaChallengeId.value = "";
  mfaForm.code = "";
};

const goToRegister = () => {
  router.push("/register");
};
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-image: url("https://acg.yaohud.cn/dm/acg.php?return=img");
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  position: relative;
  overflow: hidden;
}

.background-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  z-index: 0;
}

.decorative-elements {
  position: absolute;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

.circle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(255, 255, 255, 0.15) 0%,
    rgba(255, 255, 255, 0.05) 50%,
    transparent 70%
  );
  animation: float 20s infinite ease-in-out;
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.circle-2 {
  width: 200px;
  height: 200px;
  bottom: -50px;
  right: 10%;
  animation-delay: 7s;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 20%;
  right: -50px;
  animation-delay: 14s;
}

@keyframes float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.6;
  }
  33% {
    transform: translate(30px, -30px) scale(1.1);
    opacity: 0.8;
  }
  66% {
    transform: translate(-20px, 20px) scale(0.9);
    opacity: 0.5;
  }
}

.login-box {
  width: 440px;
  padding: 50px 45px;
  background: rgba(255, 255, 255, 0.98);
  opacity: 0.8;
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 25px 70px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.2);
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.logo-icon {
  font-size: 42px;
  color: #409eff;
  margin-bottom: 10px;
}

.login-header h2 {
  margin: 0;
  font-size: 28px;
  color: #1f2937;
}

.mfa-hint {
  margin-top: 10px;
  color: #6b7280;
  font-size: 13px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.login-button {
  width: 100%;
  border-radius: 10px;
  font-weight: 600;
}

.login-footer {
  text-align: center;
  margin-top: 8px;
}

.footer-text {
  color: #6b7280;
  margin-right: 6px;
}
</style>
