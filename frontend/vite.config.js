import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    port: 15174,
    host: "0.0.0.0",
    allowedHosts: ["tunnel.example.com"],
    // 解决Vue Router history模式刷新404问题
    historyApiFallback: true,
    proxy: {
      "/api": {
        // target: "http://192.0.2.10:8082",
        // target: "https://tunnel.example.com:14255",
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
});
