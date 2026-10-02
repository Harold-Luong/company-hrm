package com.company.calendar_service.calendar;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class CalendarErrors extends ResponseEntityExceptionHandler {
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ProblemDetail staleVersion(Exception error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED,
                "Event has changed; reload before retrying");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Calendar data conflicts with stored constraints");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail unavailable(DataAccessException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Calendar data is temporarily unavailable");
    }
}
