import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OnlyOfficeConfigRequest, OnlyOfficeConfigResponse, OnlyOfficeEditorMode } from './onlyoffice-config.model';

@Injectable({
  providedIn: 'root'
})
export class OnlyofficeConfigApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.gatewayBaseUrl.replace(/\/+$/, '');

  getEditorConfig(documentId: string, mode: OnlyOfficeEditorMode): Observable<OnlyOfficeConfigResponse> {
    const payload: OnlyOfficeConfigRequest = { documentId, mode };
    return this.http.post<OnlyOfficeConfigResponse>(`${this.baseUrl}/api/v1/onlyoffice/config`, payload);
  }
}
