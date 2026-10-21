// These limits mirror backend contracts. Coordinate changes with the corresponding service.
export const activationPassword = { minLength: 12, maxBytes: 72 }

export const fieldLimits = {
  email: 255,
  employeeName: 100,
  phone: 30,
  address: 255,
  emergencyContact: 255,
  catalogCode: 50,
  catalogName: 150,
  shiftName: 100,
  attendanceReason: 1000,
  leaveReason: 2000,
  periodReason: 2000,
  eventTitle: 255,
  eventLocation: 255,
  eventDescription: 10000,
  eventReason: 1000,
}

export const dateLimits = {
  minYear: 2000,
  maxYear: 9999,
  calendarMaxYear: 2100,
  maxRangeDays: 366,
}
