import { HttpClient, HttpEvent, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export type BackendDocumentCategory = 'GENERAL' | 'MEDICAL' | 'ADMIN' | 'REPORT' | 'CONTRACT';
export type BackendDocumentVisibility = 'CLUB_WIDE' | 'TEAM_ONLY' | 'PRIVATE';

export interface BackendInitiateUploadRequest {
  name: string;
  description: string | null;
  category: BackendDocumentCategory;
  visibility: BackendDocumentVisibility;
  originalFilename: string;
  contentType: string;
  fileSizeBytes: number;
  linkedPlayerRefId: string | null;
  linkedTeamRefId: string | null;
  tags: string[];
  versionNote: string | null;
}

export interface BackendUploadInitiationResponse {
  documentId: string;
  uploadUrl: string;
  objectKey: string;
}

export interface BackendConfirmUploadRequest extends BackendInitiateUploadRequest {
  documentId: string;
  storageObjectKey: string;
  storageBucket: string;
}

export interface BackendDocumentResponse {
  documentId: string;
  name: string;
  description: string | null;
  category: BackendDocumentCategory;
  visibility: string;
  state: 'DRAFT' | 'ACTIVE' | 'ARCHIVED' | string;
  ownerRefId: string | null;
  linkedPlayerRefId: string | null;
  linkedTeamRefId: string | null;
  tags: string[];
  versionCount: number;
  currentVersion: {
    versionId: string;
    originalFilename: string;
    contentType: string;
    fileSizeBytes: number;
    versionNumber: number;
    uploadedByActorId: string | null;
    uploadedAt: string | null;
    versionNote: string | null;
  } | null;
  createdAt: string;
  updatedAt: string;
  downloadUrl: string | null;
}

export interface BackendPageResponse<T> {
  content: T[];
}

@Injectable({
  providedIn: 'root'
})
export class DocumentsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.gatewayBaseUrl.replace(/\/+$/, '');

  listDocumentsByCategory(category: BackendDocumentCategory): Observable<BackendPageResponse<BackendDocumentResponse>> {
    const params = new HttpParams().set('category', category).set('size', '100');
    return this.http.get<BackendPageResponse<BackendDocumentResponse>>(`${this.baseUrl}/api/v1/documents`, { params });
  }

  softDeleteDocument(documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/documents/${documentId}`);
  }

  getDocument(documentId: string): Observable<BackendDocumentResponse> {
    return this.http.get<BackendDocumentResponse>(`${this.baseUrl}/api/v1/documents/${documentId}`);
  }

  initiateUpload(payload: BackendInitiateUploadRequest): Observable<BackendUploadInitiationResponse> {
    return this.http.post<BackendUploadInitiationResponse>(`${this.baseUrl}/api/v1/documents/upload/initiate`, payload);
  }

  uploadBinary(uploadUrl: string, file: File): Observable<HttpEvent<unknown>> {
    const headers = new HttpHeaders({
      'Content-Type': file.type || 'application/octet-stream'
    });

    return this.http.put(uploadUrl, file, {
      headers,
      observe: 'events',
      reportProgress: true,
      responseType: 'text' as 'json'
    });
  }

  confirmUpload(payload: BackendConfirmUploadRequest): Observable<BackendDocumentResponse> {
    return this.http.post<BackendDocumentResponse>(`${this.baseUrl}/api/v1/documents/upload/confirm`, payload);
  }
}
