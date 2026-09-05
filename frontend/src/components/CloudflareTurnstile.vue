<template>
  <div class="turnstile-field">
    <div
      ref="containerRef"
      class="turnstile-widget"
      aria-label="Cloudflare 人机验证"
    ></div>
    <p v-if="statusMessage" class="turnstile-status" role="status">
      {{ statusMessage }}
    </p>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";

const props = defineProps({
  modelValue: {
    type: String,
    default: "",
  },
  action: {
    type: String,
    required: true,
  },
  siteKey: {
    type: String,
    default: () => import.meta.env.VITE_TURNSTILE_SITE_KEY || "",
  },
});

const emit = defineEmits(["update:modelValue", "error"]);

const containerRef = ref(null);
const statusMessage = ref("");
let widgetId = null;
let renderedSize = null;
let resizeObserver = null;
let disposed = false;

const SCRIPT_ID = "cloudflare-turnstile-script";
const SCRIPT_URL =
  "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

// 页面间共享同一个脚本加载 Promise，避免登录/注册切换时重复注入。
const loadTurnstile = () => {
  if (window.turnstile) {
    return Promise.resolve(window.turnstile);
  }
  if (window.__turnstileScriptPromise) {
    return window.__turnstileScriptPromise;
  }

  window.__turnstileScriptPromise = new Promise((resolve, reject) => {
    const existingScript = document.getElementById(SCRIPT_ID);
    const script = existingScript || document.createElement("script");

    const handleLoad = () => {
      if (window.turnstile) {
        resolve(window.turnstile);
      } else {
        reject(new Error("Cloudflare Turnstile API 未初始化"));
      }
    };
    const handleError = () => {
      window.__turnstileScriptPromise = null;
      reject(new Error("Cloudflare Turnstile 脚本加载失败"));
    };

    script.addEventListener("load", handleLoad, { once: true });
    script.addEventListener("error", handleError, { once: true });

    if (!existingScript) {
      script.id = SCRIPT_ID;
      script.src = SCRIPT_URL;
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  });

  return window.__turnstileScriptPromise;
};

const clearToken = () => {
  emit("update:modelValue", "");
};

const renderWidget = async () => {
  if (!props.siteKey) {
    statusMessage.value = "人机验证尚未配置，请联系管理员";
    emit("error", "missing-site-key");
    return;
  }

  try {
    const turnstile = await loadTurnstile();
    if (disposed || !containerRef.value) return;
    // Flexible widgets have a 300px minimum; compact mode fits narrow phone cards.
    const size = containerRef.value.clientWidth < 300 ? "compact" : "flexible";
    if (widgetId !== null && renderedSize === size) return;
    if (widgetId !== null) {
      turnstile.remove(widgetId);
      clearToken();
    }
    renderedSize = size;
    widgetId = turnstile.render(containerRef.value, {
      sitekey: props.siteKey,
      action: props.action,
      theme: "auto",
      size,
      language: "zh-cn",
      retry: "auto",
      "refresh-expired": "auto",
      "response-field": false,
      callback: (token) => {
        statusMessage.value = "";
        emit("update:modelValue", token);
      },
      "expired-callback": () => {
        clearToken();
        statusMessage.value = "验证已过期，正在刷新";
      },
      "timeout-callback": () => {
        clearToken();
        statusMessage.value = "验证超时，请重试";
      },
      "error-callback": (code) => {
        clearToken();
        statusMessage.value = "人机验证加载失败，请稍后重试";
        emit("error", code);
      },
    });
  } catch (error) {
    clearToken();
    statusMessage.value = "人机验证服务暂不可用，请稍后重试";
    emit("error", error.message);
  }
};

const reset = () => {
  clearToken();
  statusMessage.value = "";
  if (widgetId !== null && window.turnstile) {
    window.turnstile.reset(widgetId);
  }
};

defineExpose({ reset });

onMounted(() => {
  renderWidget();
  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver(() => renderWidget());
    if (containerRef.value) resizeObserver.observe(containerRef.value);
  }
});

onBeforeUnmount(() => {
  disposed = true;
  resizeObserver?.disconnect();
  clearToken();
  if (widgetId !== null && window.turnstile) {
    window.turnstile.remove(widgetId);
  }
  widgetId = null;
});
</script>

<style scoped>
.turnstile-field {
  width: 100%;
}

.turnstile-widget {
  width: 100%;
  max-width: 100%;
  min-height: 65px;
  overflow: hidden;
}

.turnstile-widget :deep(iframe) {
  max-width: 100%;
}

.turnstile-status {
  margin: 8px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.5;
}
</style>
