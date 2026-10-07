# Đi trễ / về sớm — UI preview

Route: `/attendance/requests`. Frontend only, no attendance requests are sent to any API.
The in-memory preview is isolated by signed-in user ID, retained when navigating within
the app, and reset on a page reload. Sample approval/rejection is labelled explicitly.

Read-only reference: branch `attendance-service`, commit `53e5ca2`.
Its README explicitly excludes late-arrival / early-departure requests. There is no
request DTO or endpoint to integrate yet. The referenced design document is absent
from that branch. No backend files or branch references were changed.

Existing field references:

- `AttendanceResponse.workDate`, `shiftId`, `shiftVersion`.
- `ScheduleResponse.Day.definition` (a `ShiftRequest`) for the schedule context.
- `ShiftRequest.Interval.period` (`MORNING`, `AFTERNOON`), `start`, `end`.
- Company timezone: `Asia/Ho_Chi_Minh`.

Proposed frontend-only fields (not a confirmed API contract): `requestType`
(`LATE_ARRIVAL`, `EARLY_DEPARTURE`), `period`, `expectedTime`, `reason`,
`requestedMinutes`, `status`, `reviewNote`. IDs and timestamps are preview metadata.
`requestedMinutes` is the unrounded requested duration inside the selected interval;
it is not `roundedLateMinutes`, `roundedEarlyMinutes`, or `workMinutesCounted`.

Preview validation: date from today, time strictly inside the chosen work interval,
reason of 1–1,000 trimmed characters, and no duplicate pending/approved requests
for the same date + period + request type. Full-session absence links to Leave.
These are UI assumptions to reconcile with the future backend contract.

When connecting the real feature, replace the sample shift and memory store with
the actual assigned schedule and request API; validate all constraints and permissions
on the server. Do not send this preview body to check-in/out or Leave endpoints.
