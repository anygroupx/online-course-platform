import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 仅在本地环境文件中维护开发服务器地址，避免把机器相关配置写入仓库。
  const env = loadEnv(mode, process.cwd(), "");
  const allowedHosts = env.VITE_DEV_ALLOWED_HOSTS
    ? env.VITE_DEV_ALLOWED_HOSTS.split(",").map((host) => host.trim())
    : ["localhost"];

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "src"),
      },
    },
    server: {
      host: env.VITE_DEV_HOST || "127.0.0.1",
      port: Number(env.VITE_DEV_PORT || 15174),
      strictPort: true,
      allowedHosts,
      // 解决 Vue Router history 模式刷新 404 问题。
      historyApiFallback: true,
      proxy: {
        "/api": {
          target: env.VITE_API_PROXY_TARGET || "http://127.0.0.1:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
