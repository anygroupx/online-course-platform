<template>
  <div
    class="login-container auth-scene auth-scene--random fluent-spatial-stage"
    data-spatial-page="login"
    @pointermove="handlePointerMove"
    @pointerleave="resetPointerEffect"
  >
    <div class="background-overlay"></div>
    <!-- 登录页保留纯视觉 3D 装置，移除营销文案以突出随机背景。 -->
    <section class="auth-story auth-story--visual-only" data-depth="story" aria-hidden="true">
      <div class="story-module" aria-hidden="true">
        <span class="story-module-top"></span>
        <span class="story-module-face"></span>
        <span class="story-module-light"></span>
      </div>
      <span class="login-orbit"></span>
      <span class="login-chip login-chip--left"></span>
      <span class="login-chip login-chip--right"></span>
    </section>

    <div class="login-box fluent-depth-card" data-depth="auth-form">
      <div class="login-header">
        <div class="logo-icon">
          <i></i><i></i><i></i><i></i>
        </div>
        <div>
          <span class="auth-eyebrow">{{ mfaStep ? "SECURITY CHECK" : "WELCOME BACK" }}</span>
          <h2>{{ mfaStep ? "验证动态口令" : "登录二开台" }}</h2>
          <p>{{ mfaStep ? "管理员账号已启用 MFA，请完成二次验证" : "继续进入学习运营控制台" }}</p>
        </div>
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

        <el-form-item class="turnstile-form-item">
          <CloudflareTurnstile
            ref="turnstileRef"
            v-model="loginForm.turnstileToken"
            action="login"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!loginForm.turnstileToken"
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
            class="login-button"
            @click="handleMfaVerify"
          >
            <span v-if="!loading">验证并登录</span>
            <span v-else>验证中...</span>
          </el-button>
        </el-form-item>

        <div class="login-footer">
          <el-link type="info" :underline="false" @click="backToPassword">
            返回账号密码登录
          </el-link>
        </div>
      </el-form>
    </div>

    <div class="decorative-elements" aria-hidden="true">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { User, Lock } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/stores/user";
import CloudflareTurnstile from "@/components/CloudflareTurnstile.vue";

const router = useRouter();
const userStore = useUserStore();

const loginFormRef = ref(null);
const turnstileRef = ref(null);
const mfaFormRef = ref(null);
const loading = ref(false);
const mfaStep = ref(false);
const mfaChallengeId = ref("");

const loginForm = reactive({
  username: "",
  password: "",
  turnstileToken: "",
});

const mfaForm = reactive({
  code: "",
});

// 指针只改变 CSS 变量，让视差动效与表单业务逻辑保持解耦。
const handlePointerMove = (event) => {
  if (
    window.matchMedia("(prefers-reduced-motion: reduce)").matches ||
    window.matchMedia("(pointer: coarse)").matches
  ) {
    return;
  }

  const bounds = event.currentTarget.getBoundingClientRect();
  const pointerX = (event.clientX - bounds.left) / bounds.width - 0.5;
  const pointerY = (event.clientY - bounds.top) / bounds.height - 0.5;

  event.currentTarget.style.setProperty(
    "--pointer-shift-x",
    `${(-pointerX * 18).toFixed(2)}px`
  );
  event.currentTarget.style.setProperty(
    "--pointer-shift-y",
    `${(-pointerY * 12).toFixed(2)}px`
  );
  event.currentTarget.style.setProperty(
    "--pointer-rotate-x",
    `${(-pointerY * 3).toFixed(2)}deg`
  );
  event.currentTarget.style.setProperty(
    "--pointer-rotate-y",
    `${(pointerX * 4).toFixed(2)}deg`
  );
};

// 离开交互区域后归位，避免卡片停留在倾斜状态。
const resetPointerEffect = (event) => {
  event.currentTarget.style.setProperty("--pointer-shift-x", "0px");
  event.currentTarget.style.setProperty("--pointer-shift-y", "0px");
  event.currentTarget.style.setProperty("--pointer-rotate-x", "0deg");
  event.currentTarget.style.setProperty("--pointer-rotate-y", "0deg");
};

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
    { min: 6, message: "验证码至少 6 位", trigger: "blur" },
  ],
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  if (!loginForm.turnstileToken) {
    ElMessage.warning("请先完成人机验证");
    return;
  }

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        const result = await userStore.login(loginForm);
        if (result?.mfaRequired) {
          mfaStep.value = true;
          mfaChallengeId.value = result.mfaChallengeId;
          mfaForm.code = "";
          loginForm.turnstileToken = "";
        }
      } catch (error) {
        console.error("登录失败：", error);
        // Turnstile 令牌只能使用一次，任何失败都必须重新获取。
        turnstileRef.value?.reset();
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

const backToPassword = async () => {
  mfaStep.value = false;
  mfaChallengeId.value = "";
  mfaForm.code = "";
  await nextTick();
  turnstileRef.value?.reset();
};

// 跳转到注册页面
const goToRegister = () => {
  router.push("/register");
};
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100svh;
  min-height: 100dvh;
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
  animation: slideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1);
}

html.dark .login-box {
  background: rgba(30, 41, 59, 0.95);
  box-shadow: 0 25px 70px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.05);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(50px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.login-header {
  text-align: center;
  margin-bottom: 35px;
}

.logo-icon {
  width: 70px;
  height: 70px;
  margin: 0 auto 20px;
  background: var(--primary-gradient);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  color: white;
  box-shadow: 0 10px 30px color-mix(in srgb, var(--brand-primary) 40%, transparent);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 10px 30px color-mix(in srgb, var(--brand-primary) 40%, transparent);
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 15px 40px color-mix(in srgb, var(--brand-primary) 52%, transparent);
  }
}

.login-header h2 {
  font-size: 34px;
  font-weight: 700;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
  letter-spacing: 1px;
}

.login-header p {
  font-size: 14px;
  color: var(--text-regular);
  font-weight: 400;
  line-height: 1.6;
}

.login-form {
  margin-top: 30px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  background: var(--primary-gradient);
  border: none;
  border-radius: 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  letter-spacing: 2px;
}

.login-button:hover:not(:disabled) {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px color-mix(in srgb, var(--brand-primary) 46%, transparent);
}

.login-button:active:not(:disabled) {
  transform: translateY(-1px);
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid var(--border-color-light);
}

.footer-text {
  font-size: 14px;
  color: var(--text-secondary);
  margin-right: 8px;
}

:deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 12px 15px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
}

html.dark :deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.05);
  box-shadow: none;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

:deep(.el-input__wrapper):hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: color-mix(in srgb, var(--brand-primary) 20%, transparent);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px var(--focus-ring);
  border-color: var(--brand-primary);
}

:deep(.el-input__inner) {
  font-size: 15px;
}

:deep(.el-form-item__error) {
  font-size: 12px;
  padding-top: 4px;
}

/* 响应式设计 */
@media (max-width: 767px) {
  .login-box {
    width: 90%;
    max-width: 400px;
    padding: 40px 30px;
  }

  .login-header h2 {
    font-size: 28px;
  }

  .logo-icon {
    width: 60px;
    height: 60px;
    font-size: 30px;
  }
}
</style>

<style scoped lang="scss" src="../styles/auth-spatial.scss"></style>

<style scoped>
/* 登录页恢复随机背景，空间装置继续使用共享认证动效。 */
.auth-scene--random {
  --pointer-shift-x: 0px;
  --pointer-shift-y: 0px;
  --pointer-rotate-x: 0deg;
  --pointer-rotate-y: 0deg;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(105deg, rgba(5, 14, 26, 0.16), rgba(5, 14, 26, 0.30)),
    url("https://acg.yaohud.cn/dm/acg.php?return=img") center / cover no-repeat fixed;
}

.auth-scene--random .background-overlay {
  background:
    radial-gradient(circle at 16% 24%, rgba(71, 158, 245, 0.16), transparent 32%),
    linear-gradient(90deg, rgba(5, 14, 26, 0.04), rgba(5, 14, 26, 0.22));
  backdrop-filter: blur(1px) saturate(1.08);
}

.auth-story--visual-only {
  position: absolute;
  z-index: 1;
  inset: 0;
  max-width: none;
  min-height: 100%;
  padding: 0;
  pointer-events: none;
  transform: translate3d(
    var(--pointer-shift-x),
    var(--pointer-shift-y),
    0
  );
  transition: transform 180ms ease-out;
}

.auth-story--visual-only .story-module {
  top: 12%;
  right: auto;
  left: 9%;
  width: 230px;
  height: 250px;
  opacity: 0.92;
  transform-origin: center;
}

.auth-scene--random .login-box {
  z-index: 4;
  margin: 0;
  transform:
    translateZ(42px)
    rotateX(var(--pointer-rotate-x))
    rotateY(var(--pointer-rotate-y));
  transition:
    transform 180ms ease-out,
    box-shadow var(--motion-base) ease;
}

.auth-scene--random .login-box:hover {
  box-shadow:
    inset 0 1px 0 rgba(220, 240, 255, 0.22),
    0 44px 100px rgba(0, 0, 0, 0.50),
    0 0 68px rgba(71, 158, 245, 0.14);
}

.login-orbit,
.login-chip {
  position: absolute;
  display: block;
  border: 1px solid rgba(196, 226, 255, 0.28);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.20), transparent),
    rgba(17, 42, 68, 0.34);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.24),
    0 24px 48px rgba(0, 0, 0, 0.24),
    0 0 34px rgba(71, 158, 245, 0.12);
  backdrop-filter: blur(14px);
}

.login-orbit {
  top: 13%;
  right: 9%;
  width: 154px;
  height: 154px;
  border-radius: 50%;
  background:
    radial-gradient(circle, transparent 42%, rgba(56, 213, 222, 0.18) 43% 46%, transparent 47%),
    rgba(17, 42, 68, 0.18);
  animation: login-orbit-spin 13s linear infinite;
}

.login-orbit::before,
.login-orbit::after {
  content: "";
  position: absolute;
  border: 1px solid rgba(121, 195, 255, 0.28);
  border-radius: 50%;
}

.login-orbit::before {
  inset: 18px -12px;
  transform: rotateX(66deg);
}

.login-orbit::after {
  inset: -12px 18px;
  transform: rotateY(66deg);
}

@keyframes login-orbit-spin {
  to {
    transform: rotate(360deg);
  }
}

.login-chip {
  width: 72px;
  height: 72px;
  border-radius: 18px;
  animation: login-chip-float 5.2s ease-in-out infinite;
}

.login-chip::after {
  content: "";
  position: absolute;
  inset: 22px;
  border-radius: 7px;
  background: linear-gradient(145deg, #479ef5, #38d5de);
  box-shadow: 0 0 22px rgba(56, 213, 222, 0.34);
}

.login-chip--left {
  bottom: 12%;
  left: 17%;
  animation-delay: -1.6s;
}

.login-chip--right {
  right: 16%;
  bottom: 15%;
  width: 54px;
  height: 54px;
  border-radius: 15px;
  animation-delay: -3.4s;
}

.login-chip--right::after {
  inset: 17px;
}

@keyframes login-chip-float {
  0%,
  100% {
    transform: translate3d(0, 0, 12px) rotate(8deg);
  }
  50% {
    transform: translate3d(0, -14px, 28px) rotate(-4deg);
  }
}

@media (max-width: 1060px) {
  .auth-scene--random {
    background-attachment: scroll;
  }

  .auth-story--visual-only {
    display: block;
  }

  .auth-story--visual-only .story-module {
    top: 8%;
    left: -56px;
    width: 170px;
    height: 190px;
  }

  .login-orbit {
    top: 7%;
    right: -42px;
    width: 112px;
    height: 112px;
  }

  .login-chip--left {
    bottom: 7%;
    left: 6%;
  }

  .login-chip--right {
    right: 5%;
    bottom: 8%;
  }
}

@media (max-width: 560px) {
  .auth-scene--random .login-box {
    transform: none;
  }

  .auth-scene--random .login-box:hover {
    box-shadow:
      inset 0 1px 0 rgba(220, 240, 255, 0.22),
      0 24px 56px rgba(0, 0, 0, 0.44);
  }
}

@media (max-height: 700px) and (max-width: 1060px) {
  .auth-scene--random {
    align-items: flex-start;
  }

  .auth-story--visual-only .story-module,
  .login-orbit {
    opacity: 0.44;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-story--visual-only,
  .auth-scene--random .login-box,
  .login-orbit,
  .login-chip {
    animation: none;
    transform: none;
    transition: none;
  }
}
</style>
