import { HttpInterceptorFn } from '@angular/common/http';

const OVERRIDABLE = new Set(['PUT', 'PATCH', 'DELETE']);

/**
 * Sends PUT/PATCH/DELETE as a POST carrying X-App-Verb instead —
 * a network appliance in front of production drops those verbs outright
 * (bare 403, never reaching the backend at all — confirmed by comparing an
 * identical GET, which does reach it), while GET/POST/OPTIONS go through
 * fine. The backend's MethodOverrideFilter reads this header and treats the
 * request exactly as the real verb, so no controller/service code changes.
 */
export const methodOverrideInterceptor: HttpInterceptorFn = (req, next) => {
  if (!OVERRIDABLE.has(req.method)) return next(req);
  return next(
    req.clone({ method: 'POST', setHeaders: { 'X-App-Verb': req.method } }),
  );
};
