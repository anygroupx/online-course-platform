export const TICKET_IMAGE_LIMIT = 2 * 1024 * 1024
export const ticketImageFileError = (file, header) => {
  if (!file || !['image/png', 'image/jpeg'].includes(file.type)) return '仅支持 PNG 或 JPEG 图片，不接受 SVG、GIF 或网络链接。'
  if (!file.size || file.size > TICKET_IMAGE_LIMIT) return '图片原文件须大于零且不超过 2MiB。'
  if (header) {
    const png = [137, 80, 78, 71, 13, 10, 26, 10]
    const valid = file.type === 'image/png' ? png.every((b, i) => header[i] === b) : header[0] === 255 && header[1] === 216 && header[2] === 255
    if (!valid) return '文件内容与图片格式不符，请选择真实 PNG/JPEG。'
  }
  return ''
}
