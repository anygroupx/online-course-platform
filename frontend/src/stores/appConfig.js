import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getClientBootstrap } from "@/api/clientConfig";
import {
  applyClientConfig,
  getClientConfigSnapshot,
  resetClientConfig,
} from "@/utils/clientConfigState";

export const useAppConfigStore = defineStore("appConfig", () => {
  const config = ref(getClientConfigSnapshot());
  const loaded = ref(false);
  const loading = ref(false);
  const loadFailed = ref(false);
  let loadPromise = null;

  const branding = computed(() => config.value.branding);
  const session = computed(() => config.value.session);

  const ensureLoaded = async ({ force = false } = {}) => {
    if (loaded.value && !force) return config.value;
    if (loadPromise) return loadPromise;

    loading.value = true;
    const pending = (async () => {
      try {
        const response = await getClientBootstrap();
        config.value = applyClientConfig(response?.data);
        loadFailed.value = false;
      } catch {
        config.value = resetClientConfig();
        loadFailed.value = true;
      } finally {
        loaded.value = true;
        loading.value = false;
      }
      return config.value;
    })();

    loadPromise = pending;
    try {
      return await pending;
    } finally {
      if (loadPromise === pending) loadPromise = null;
    }
  };

  const reload = () => ensureLoaded({ force: true });

  return {
    config,
    branding,
    session,
    loaded,
    loading,
    loadFailed,
    ensureLoaded,
    reload,
  };
});
