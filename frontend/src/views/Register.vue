<template>
  <div class="register-container auth-scene fluent-spatial-stage" data-spatial-page="register">
    <div class="background-overlay"></div>
    <!-- 注册入口沿用同一空间品牌层，避免认证流程出现视觉断裂。 -->
    <section class="auth-story" data-depth="story" aria-label="产品介绍">
      <div class="story-kicker">
        <span></span>
        JOIN LEARNING OPERATIONS
      </div>
      <h1>从一枚邀请码，<br />进入协作工作台。</h1>
      <p>轻量注册、清晰权限，让每位成员快速找到自己的工作位置。</p>
      <div class="story-module" aria-hidden="true">
        <span class="story-module-top"></span>
        <span class="story-module-face"></span>
        <span class="story-module-light"></span>
      </div>
      <ul class="story-points">
        <li><span></span>邀请制访问</li>
        <li><span></span>角色化工作区</li>
        <li><span></span>跨设备一致体验</li>
      </ul>
    </section>

    <div class="register-box fluent-depth-card" data-depth="auth-form">
      <div class="register-header">
        <div class="logo-icon">
          <i></i><i></i><i></i><i></i>
        </div>
        <div>
          <span class="auth-eyebrow">CREATE ACCOUNT</span>
          <h2>创建新账号</h2>
          <p class="subtitle">使用邀请码加入工作台</p>
        </div>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="rules"
        class="register-form"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请确认密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="inviteCode">
          <el-input
            v-model="registerForm.inviteCode"
            placeholder="请输入邀请码"
            size="large"
            :prefix-icon="Key"
            clearable
            @blur="validateInviteCode"
          />
          <div v-if="inviteCodeStatus" class="invite-code-status">
            <i :class="inviteCodeStatus === 'valid' ? 'el-icon-success' : 'el-icon-error'"></i>
            <span :class="inviteCodeStatus === 'valid' ? 'valid-text' : 'invalid-text'">
              {{ inviteCodeStatus === 'valid' ? '邀请码有效' : '邀请码无效' }}
            </span>
          </div>
        </el-form-item>

        <el-form-item prop="nickname">
          <el-input
            v-model="registerForm.nickname"
            placeholder="请输入昵称（可选）"
            size="large"
            :prefix-icon="UserFilled"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleRegister"
            class="register-button"
          >
            <span v-if="!loading">立即注册</span>
            <span v-else>注册中...</span>
          </el-button>
        </el-form-item>

        <div class="register-footer">
          <span class="footer-text">已有账号？</span>
          <el-link type="primary" :underline="false" @click="goToLogin">立即登录</el-link>
          <div style="margin-top: 10px;">
            <el-link type="info" :underline="false" @click="$router.push('/service-agreement')" style="font-size: 12px;">服务协议</el-link>
          </div>
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
import { ref, reactive, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { User, Lock, Key, UserFilled } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { register, validateInviteCode as validateInviteCodeApi } from "@/api/user";

const router = useRouter();
const route = useRoute();

const registerFormRef = ref(null);
const loading = ref(false);
const inviteCodeStatus = ref(null);

const registerForm = reactive({
  username: "",
  password: "",
  confirmPassword: "",
  inviteCode: "",
  nickname: "",
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度在3到20个字符", trigger: "blur" },
    { pattern: /^[a-zA-Z0-9_]+$/, message: "用户名只能包含字母、数字和下划线", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度在6到20个字符", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请确认密码", trigger: "blur" },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error("两次输入密码不一致"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
  inviteCode: [
    { required: true, message: "请输入邀请码", trigger: "blur" },
    { min: 4, max: 20, message: "邀请码长度在4到20个字符", trigger: "blur" },
  ],
};

// 验证邀请码
const validateInviteCode = async () => {
  if (!registerForm.inviteCode) {
    inviteCodeStatus.value = null;
    return;
  }

  try {
    const response = await validateInviteCodeApi(registerForm.inviteCode);
    inviteCodeStatus.value = response.data ? "valid" : "invalid";
  } catch (error) {
    inviteCodeStatus.value = "invalid";
  }
};

// 处理注册
const handleRegister = async () => {
  if (!registerFormRef.value) return;

  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      // 再次验证邀请码
      if (inviteCodeStatus.value !== "valid") {
        ElMessage.error("请先验证邀请码");
        return;
      }

      loading.value = true;
      try {
        const { confirmPassword, ...registerData } = registerForm;
        await register(registerData);
        ElMessage.success("注册成功！请登录");
        router.push("/login");
      } catch (error) {
        console.error("注册失败：", error);
        ElMessage.error(error.response?.data?.message || "注册失败，请重试");
      } finally {
        loading.value = false;
      }
    }
  });
};

// 跳转到登录页面
const goToLogin = () => {
  router.push("/login");
};

// 页面挂载时检查URL参数中的邀请码
onMounted(() => {
  const inviteCodeFromUrl = route.query.inviteCode;
  if (inviteCodeFromUrl) {
    registerForm.inviteCode = inviteCodeFromUrl;
    // 自动验证邀请码
    validateInviteCode();
  }
});
</script>

<style scoped>
.register-container {
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

.register-box {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
}

html.dark .register-box {
  background: rgba(30, 41, 59, 0.95);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.logo-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto 15px;
  background: var(--primary-gradient);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
}

.register-header h2 {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 600;
}

.subtitle {
  margin: 0;
  color: var(--text-regular);
  font-size: 14px;
}

.register-form {
  margin-top: 20px;
}

.register-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  background: var(--primary-gradient);
  border: none;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.register-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--brand-primary) 40%, transparent);
}

.register-footer {
  text-align: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-color-light);
}

.footer-text {
  color: var(--text-secondary);
  font-size: 14px;
  margin-right: 8px;
}

.invite-code-status {
  display: flex;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
}

.invite-code-status i {
  margin-right: 4px;
}

.valid-text {
  color: var(--color-success);
}

.invalid-text {
  color: var(--color-danger);
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
  right: -50px;
  animation-delay: 7s;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  left: 10%;
  animation-delay: 14s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  33% {
    transform: translateY(-20px) rotate(120deg);
  }
  66% {
    transform: translateY(10px) rotate(240deg);
  }
}

/* 响应式设计 */
@media (max-width: 480px) {
  .register-box {
    width: 90%;
    padding: 30px 20px;
  }

  .register-header h2 {
    font-size: 24px;
  }
}
</style>

<style scoped lang="scss" src="../styles/auth-spatial.scss"></style>
