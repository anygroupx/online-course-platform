const LEVELS = [
  { key: "diamond", label: "钻石级", rate: 0.5, tagType: "danger" },
  { key: "platinum", label: "铂金级", rate: 0.65, tagType: "warning" },
  { key: "gold", label: "黄金级", rate: 0.8, tagType: "warning" },
  { key: "silver", label: "白银级", rate: 1.0, tagType: "success" },
  { key: "bronze", label: "青铜级", rate: 1.5, tagType: "primary" },
  { key: "basic", label: "基础级", rate: 2.0, tagType: "info" },
];

export const USER_LEVELS = LEVELS.map((level) => ({ ...level }));

const normalizeRate = (value, fallback = 1) => {
  const rate = Number(value);
  return Number.isFinite(rate) && rate > 0 ? rate : fallback;
};

export const getUserLevel = (rate) => {
  const normalizedRate = normalizeRate(rate);
  return (
    LEVELS.find((level) => normalizedRate <= level.rate) ||
    LEVELS[LEVELS.length - 1]
  );
};

export const getUserLevelLabel = (rate) => getUserLevel(rate).label;

export const getUserLevelTagType = (rate) => getUserLevel(rate).tagType;

export const getAssignableUserLevels = (minimumRate, selectedRate) => {
  const normalizedMinimum = normalizeRate(minimumRate);
  const options = LEVELS.filter(
    (level) => level.rate + Number.EPSILON >= normalizedMinimum
  ).map((level) => ({ ...level }));

  const addCustomOption = (value, suffix) => {
    const normalizedValue = Number(value);
    if (
      !Number.isFinite(normalizedValue) ||
      normalizedValue <= 0 ||
      normalizedValue + Number.EPSILON < normalizedMinimum ||
      options.some((option) => Math.abs(option.rate - normalizedValue) < 0.000001)
    ) {
      return;
    }

    const level = getUserLevel(normalizedValue);
    options.push({
      ...level,
      key: `${level.key}-${normalizedValue}`,
      label: `${level.label}${suffix}`,
      rate: normalizedValue,
    });
  };

  addCustomOption(normalizedMinimum, "（同级）");
  addCustomOption(selectedRate, "（当前设置）");

  return options.sort((left, right) => left.rate - right.rate);
};

export const getDefaultAssignableRate = (minimumRate) =>
  getAssignableUserLevels(minimumRate)[0]?.rate || 2;
