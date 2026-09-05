/** Copy full text, including on HTTP deployments without the async Clipboard API. */
export async function copyText(text) {
  if (text == null || String(text).length === 0) throw new Error('没有可复制的内容');
  const value = String(text);
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
      return;
    }
  } catch {
    // Permission denied / insecure context: fall back to a selected, temporary textarea.
  }
  const focused = document.activeElement;
  const input = document.createElement('textarea');
  input.value = value;
  input.readOnly = true;
  input.style.cssText = 'position:fixed;left:0;top:0;opacity:0;pointer-events:none;';
  // Stay inside the active dialog's focus trap, if any.
  (focused?.closest('[role="dialog"]') || document.body).appendChild(input);
  try {
    input.select();
    input.setSelectionRange(0, value.length);
    if (!document.execCommand('copy')) throw new Error('复制失败，请手动选择完整内容复制');
  } finally {
    input.remove();
    focused?.focus?.({ preventScroll: true });
  }
}
