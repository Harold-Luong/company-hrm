import { leaveErrors as copy } from '../content/leave.js'
import { ApiError, serviceError } from '../api/errors.js'

export function leaveError(error) {
  if (!(error instanceof ApiError)) return copy.unexpected
  const messages = {
    get 'No working time in the requested leave period'() {
      return copy.noWorkingDays
    },
    get 'Working calendar changed; withdraw and resubmit leave before approval'() {
      return copy.scheduleChanged
    },
    get 'Legacy request has no working calendar; withdraw and resubmit before approval'() {
      return copy.missingSnapshot
    },
    get 'Working calendar is unavailable or incomplete'() {
      return copy.sourcesUnavailable
    },
    get 'Use full cancellation when no working leave remains'() {
      return copy.noRemainingDays
    },
    get 'Invalid leave dates or period'() {
      return copy.invalidPeriod
    },

    get 'Adjustment must reduce approved leave'() {
      return copy.remainingMustDecrease
    },
    get 'Remaining leave must be within the approved dates and period'() {
      return copy.remainingOutsideApproved
    },
    get 'An adjustment is already pending'() {
      return copy.adjustmentPending
    },
    get 'Leave balance requires reconciliation before refund'() {
      return copy.balanceNeedsReview
    },
    get 'A pending or approved leave request overlaps these dates'() {
      return copy.overlappingRequest
    },
    get 'You cannot review your own leave request'() {
      return copy.selfReview
    },
    get 'A rejection reason is required'() {
      return copy.rejectionRequired
    },
    get 'Only pending requests can be processed'() {
      return copy.alreadyReviewed
    },
    get 'Dates must start today or later, in order, and span at most 366 calendar days'() {
      return copy.invalidDateRange
    },
  }
  if (messages[error.message]) return messages[error.message]
  if ([412, 428].includes(error.status)) return copy.staleVersion
  if (error.status === 0 || error.status >= 500) return copy.uncertainResult
  return serviceError(error)
}
