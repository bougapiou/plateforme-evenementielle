import { HttpClient, HttpParams } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Small helper base for feature API services. */
export abstract class ApiBase {
  protected http = inject(HttpClient);
  protected base = environment.apiBaseUrl;

  protected params(obj: Record<string, string | number | boolean | null | undefined>): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(obj)) {
      if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
    }
    return p;
  }

  protected get<T>(path: string, params?: Record<string, any>): Observable<T> {
    return this.http.get<T>(`${this.base}${path}`, params ? { params: this.params(params) } : {});
  }
}
