import { today } from './helpers.js'

// datetime-local values always represent Vietnam time, regardless of browser timezone.
export function localCorrectionTime(value) {
  return value ? new Date(new Date(value).getTime() + 7 * 3600000).toISOString().slice(0, 19) : ''
}
export function correctionBody(form, plan, row, now = new Date()) {
  if (!form.workDate || form.workDate > today())
    throw new Error('Chọn ngày công không ở tương lai.')
  if (!plan?.definition || plan.date !== form.workDate)
    throw new Error('Chưa có ca cho ngày đã chọn. Hãy tải lại dữ liệu ngày công.')
  if (row && row.workDate !== form.workDate) throw new Error('Hãy tải lại dữ liệu ngày công.')
  const parse = (value) => {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value))
      throw new Error('Nhập đủ ngày và giờ vào/ra đề nghị.')
    const time = new Date(`${value}+07:00`)
    if (!Number.isFinite(time.getTime())) throw new Error('Giờ đề nghị không hợp lệ.')
    return time
  }
  const start = parse(form.proposedCheckIn),
    end = parse(form.proposedCheckOut)
  if (end <= start || end > now) throw new Error('Giờ ra phải sau giờ vào và không ở tương lai.')
  if (!form.reason.trim() || form.reason.trim().length > 1000)
    throw new Error('Nhập lý do điều chỉnh, tối đa 1.000 ký tự.')
  return {
    workDate: form.workDate,
    shiftId: plan.shiftId,
    shiftVersion: plan.shiftVersion,
    recordVersion: row?.recordVersion ?? null,
    proposedCheckIn: start.toISOString().replace('.000Z', 'Z'),
    proposedCheckOut: end.toISOString().replace('.000Z', 'Z'),
    reason: form.reason.trim(),
  }
}
