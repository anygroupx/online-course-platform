/**
 * XSS 防护工具
 * 用于清理和过滤用户输入，防止 XSS 攻击
 *
 * @author AI Assistant
 * @since 2025-01-20
 */

/**
 * HTML 实体转义
 * 将特殊字符转换为 HTML 实体
 */
export function escapeHtml(text) {
  if (!text) return "";

  const map = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#x27;",
    "/": "&#x2F;",
  };

  return String(text).replace(/[&<>"'/]/g, (char) => map[char]);
}

/**
 * 清理 HTML 标签
 * 移除所有 HTML 标签，只保留纯文本
 */
export function stripHtml(html) {
  if (!html) return "";

  const tmp = document.createElement("div");
  tmp.innerHTML = html;
  return tmp.textContent || tmp.innerText || "";
}

/**
 * 安全的 HTML 清理
 * 只允许安全的 HTML 标签和属性
 */
export function sanitizeHtml(dirty) {
  if (!dirty) return "";

  // 允许的标签白名单
  const allowedTags = [
    "p",
    "br",
    "strong",
    "em",
    "u",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "ul",
    "ol",
    "li",
    "a",
    "span",
    "div",
  ];

  // 允许的属性白名单
  const allowedAttrs = {
    a: ["href", "title", "target"],
    span: ["class"],
    div: ["class"],
  };

  const tmp = document.createElement("div");
  tmp.innerHTML = dirty;

  // 递归清理节点
  const clean = (node) => {
    // 如果是文本节点，直接返回
    if (node.nodeType === 3) {
      return document.createTextNode(node.textContent);
    }

    // 如果不是元素节点，跳过
    if (node.nodeType !== 1) {
      return null;
    }

    const tagName = node.tagName.toLowerCase();

    // 如果标签不在白名单中，只保留其子节点
    if (!allowedTags.includes(tagName)) {
      const fragment = document.createDocumentFragment();
      Array.from(node.childNodes).forEach((child) => {
        const cleaned = clean(child);
        if (cleaned) fragment.appendChild(cleaned);
      });
      return fragment;
    }

    // 创建新的安全节点
    const newNode = document.createElement(tagName);

    // 只复制允许的属性
    if (allowedAttrs[tagName]) {
      allowedAttrs[tagName].forEach((attr) => {
        if (node.hasAttribute(attr)) {
          let value = node.getAttribute(attr);

          // 特殊处理 href 属性，防止 javascript: 协议
          if (attr === "href") {
            value = sanitizeUrl(value);
          }

          // 防止 onclick 等事件属性
          if (!attr.startsWith("on")) {
            newNode.setAttribute(attr, value);
          }
        }
      });
    }

    // 递归清理子节点
    Array.from(node.childNodes).forEach((child) => {
      const cleaned = clean(child);
      if (cleaned) newNode.appendChild(cleaned);
    });

    return newNode;
  };

  const result = document.createElement("div");
  Array.from(tmp.childNodes).forEach((child) => {
    const cleaned = clean(child);
    if (cleaned) result.appendChild(cleaned);
  });

  return result.innerHTML;
}

/**
 * URL 清理
 * 防止 javascript:、data: 等危险协议
 */
export function sanitizeUrl(url) {
  if (!url) return "";

  const trimmed = url.trim().toLowerCase();

  // 危险协议黑名单
  const dangerousProtocols = ["javascript:", "data:", "vbscript:", "file:"];

  for (const protocol of dangerousProtocols) {
    if (trimmed.startsWith(protocol)) {
      return "#";
    }
  }

  return url;
}

/**
 * 验证输入
 * 检查输入是否包含潜在的 XSS 攻击代码
 */
export function validateInput(input, options = {}) {
  if (!input) return { valid: true, sanitized: "" };

  const {
    maxLength = 10000,
    allowHtml = false,
    allowScripts = false,
  } = options;

  let sanitized = String(input);

  // 长度限制
  if (sanitized.length > maxLength) {
    return {
      valid: false,
      error: `输入长度超过限制（最大 ${maxLength} 字符）`,
      sanitized: sanitized.substring(0, maxLength),
    };
  }

  // 检测脚本标签
  const scriptPattern = /<script[\s\S]*?>[\s\S]*?<\/script>/gi;
  if (!allowScripts && scriptPattern.test(sanitized)) {
    return {
      valid: false,
      error: "检测到不允许的脚本内容",
      sanitized: sanitized.replace(scriptPattern, ""),
    };
  }

  // 检测事件处理器
  const eventPattern = /on\w+\s*=\s*["'][^"']*["']/gi;
  if (eventPattern.test(sanitized)) {
    return {
      valid: false,
      error: "检测到不允许的事件处理器",
      sanitized: sanitized.replace(eventPattern, ""),
    };
  }

  // HTML 处理
  if (!allowHtml) {
    sanitized = escapeHtml(sanitized);
  } else {
    sanitized = sanitizeHtml(sanitized);
  }

  return {
    valid: true,
    sanitized,
  };
}

/**
 * 清理对象中的所有字符串值
 * 用于批量处理表单数据
 */
export function sanitizeObject(obj, options = {}) {
  if (!obj || typeof obj !== "object") return obj;

  const result = Array.isArray(obj) ? [] : {};

  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      const value = obj[key];

      if (typeof value === "string") {
        const validation = validateInput(value, options);
        result[key] = validation.sanitized;
      } else if (typeof value === "object") {
        result[key] = sanitizeObject(value, options);
      } else {
        result[key] = value;
      }
    }
  }

  return result;
}

/**
 * 使用 DOMPurify 库进行清理（如果已安装）
 * 这是最推荐的方式
 */
export function sanitizeWithDOMPurify(dirty, config = {}) {
  // 检查是否安装了 DOMPurify
  if (typeof window !== "undefined" && window.DOMPurify) {
    return window.DOMPurify.sanitize(dirty, {
      ALLOWED_TAGS: [
        "p",
        "br",
        "strong",
        "em",
        "u",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "ul",
        "ol",
        "li",
        "a",
        "span",
        "div",
      ],
      ALLOWED_ATTR: ["href", "title", "target", "class"],
      ALLOW_DATA_ATTR: false,
      ...config,
    });
  }

  // 如果没有安装 DOMPurify，使用备用方案
  console.warn(
    "DOMPurify 未安装，使用内置清理方法。建议安装 DOMPurify: npm install dompurify"
  );
  return sanitizeHtml(dirty);
}

export default {
  escapeHtml,
  stripHtml,
  sanitizeHtml,
  sanitizeUrl,
  validateInput,
  sanitizeObject,
  sanitizeWithDOMPurify,
};
