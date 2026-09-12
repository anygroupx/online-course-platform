export const getProjectDisplayName = (project = {}) =>
  project.displayName || project.name || "未命名项目";

export const calculateUserProjectPrice = (project = {}, userRate = 1) => {
  const basePrice = Number(project.basePrice || 0);
  const normalizedRate = Number(userRate || 1);
  const amount = project.rateType === "ADD"
    ? basePrice + normalizedRate
    : basePrice * normalizedRate;

  return Number.isFinite(amount) ? amount : 0;
};

export const formatUserProjectPrice = (project, userRate) =>
  calculateUserProjectPrice(project, userRate).toFixed(2);

export const matchesProjectSearch = (project = {}, keyword = "") => {
  const normalizedKeyword = String(keyword).trim().toLocaleLowerCase("zh-CN");
  if (!normalizedKeyword) return true;

  return [
    getProjectDisplayName(project),
    project.categoryName,
    project.description,
  ]
    .filter(Boolean)
    .some((value) =>
      String(value).toLocaleLowerCase("zh-CN").includes(normalizedKeyword)
    );
};
