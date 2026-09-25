export function formatDate(value) {
  if (!value) return 'Chưa có thông tin'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Chưa có thông tin'
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(date)
}
