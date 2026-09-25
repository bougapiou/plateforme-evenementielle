import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Observable, Subscription, catchError, interval, startWith, switchMap } from 'rxjs';
import { AttendanceView } from '../features/checkin/checkin.service';

/**
 * Refreshes an attendance view every `everyMs`. A display left on all day must survive a network blip:
 * a failed refresh is simply skipped, and only "not found / forbidden" — the event is gone or the access
 * was withdrawn — is reported through `unavailable`.
 */
export function pollAttendance(
  fetch: () => Observable<AttendanceView>,
  everyMs: number,
  next: (view: AttendanceView) => void,
  unavailable: () => void,
): Subscription {
  return interval(everyMs)
    .pipe(
      startWith(0),
      switchMap(() =>
        fetch().pipe(
          catchError((err: HttpErrorResponse) => {
            if (err.status === 404 || err.status === 403) unavailable();
            return EMPTY;
          }),
        ),
      ),
    )
    .subscribe(next);
}
