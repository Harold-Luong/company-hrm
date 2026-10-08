import com.company.attendance.service.worktime.*;
import com.company.attendance.service.worktime.WorkTimeInput.*;
import java.time.*;
import java.util.List;

public class PaidLeaveExample {
    public static void main(String[] args) {
        LocalDate date = LocalDate.of(2030, 1, 7);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var input = new WorkTimeInput(
                date, zone,
                List.of(
                        new WorkInterval("MORNING", date.atTime(8, 0).atZone(zone).toInstant(),
                                date.atTime(12, 0).atZone(zone).toInstant()),
                        new WorkInterval("AFTERNOON", date.atTime(13, 30).atZone(zone).toInstant(),
                                date.atTime(17, 30).atZone(zone).toInstant())),
                CalendarDay.WORKDAY,
                List.of(new Leave("leave-001", WorkTimeInput.FULL_DAY, LeaveType.ANNUAL_PAID, Approval.APPROVED)),
                Punch.none(), List.of(),
                date.atTime(22, 0).atZone(zone).toInstant(),
                date.atTime(23, 0).atZone(zone).toInstant(),
                WorkTimePolicy.standard());
        var result = WorkTimeSupport.calculate(input);
        System.out.println("status=" + result.status());
        System.out.println("workedMinutes=" + result.workedCountedMinutes());
        System.out.println("annualLeaveMinutes=" + result.annualLeaveMinutes());
        System.out.println("payableMinutes=" + result.payableMinutes());
        System.out.println("payableDays=" + result.payableDays());
    }
}
