import { HttpErrorResponse, HttpEventType, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { concat, defer, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { DocumentsComponent } from './documents.component';
import {
  BackendDocumentResponse,
  BackendInitiateUploadRequest,
  DocumentsApiService
} from './documents-api.service';

describe('DocumentsComponent upload flow', () => {
  let fixture: ComponentFixture<DocumentsComponent>;
  let component: DocumentsComponent;
  let authService: jasmine.SpyObj<Pick<AuthService, 'roles'>>;
  let documentsApi: jasmine.SpyObj<
    Pick<DocumentsApiService, 'initiateUpload' | 'uploadBinary' | 'confirmUpload' | 'listDocumentsByCategory'>
  >;

  beforeEach(async () => {
    const workspaceDataService = jasmine.createSpyObj('WorkspaceDataService', ['getFolders', 'getParticipants', 'createFolder']);
    workspaceDataService.getFolders.and.returnValue([]);
    workspaceDataService.getParticipants.and.returnValue([]);

    documentsApi = jasmine.createSpyObj('DocumentsApiService', [
      'initiateUpload',
      'uploadBinary',
      'confirmUpload',
      'listDocumentsByCategory'
    ]);
    documentsApi.listDocumentsByCategory.and.returnValue(of({ content: [] }));
    authService = jasmine.createSpyObj('AuthService', ['roles']);
    authService.roles.and.returnValue(['HEAD_COACH']);

    await TestBed.configureTestingModule({
      imports: [DocumentsComponent],
      providers: [
        {
          provide: WorkspaceDataService,
          useValue: workspaceDataService
        },
        {
          provide: Router,
          useValue: jasmine.createSpyObj('Router', ['navigate'])
        },
        {
          provide: AuthService,
          useValue: authService
        },
        {
          provide: DocumentsApiService,
          useValue: documentsApi
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentsComponent);
    component = fixture.componentInstance;
    spyOn(console, 'error');
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('calls initiate, waits for the final PUT response, then confirms upload', async () => {
    const file = new File(['resume'], 'attestation.pdf', { type: 'application/pdf' });
    const callOrder: string[] = [];

    documentsApi.initiateUpload.and.callFake((payload: BackendInitiateUploadRequest) => {
      callOrder.push(`initiate:${payload.originalFilename}`);
      return of({
        documentId: '11111111-1111-4111-8111-111111111111',
        uploadUrl: 'http://localhost:9000/fos-workspace/documents/test.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256',
        objectKey: 'documents/test.pdf'
      });
    });

    documentsApi.uploadBinary.and.callFake(() => {
      callOrder.push('put:subscribed');
      const progressEvent = { type: HttpEventType.UploadProgress as const, loaded: 6, total: 6 };
      return concat(
        defer(() => {
          callOrder.push('put:progress');
          return of(progressEvent);
        }),
        defer(() => {
          callOrder.push('put:response');
          return of(new HttpResponse({ status: 204, body: '' }));
        })
      );
    });

    documentsApi.confirmUpload.and.callFake(() => {
      callOrder.push('confirm');
      return of(mockDocumentResponse());
    });

    await component['uploadFilesThroughBackend']([file]);

    expect(callOrder).toEqual([
      'initiate:attestation.pdf',
      'put:subscribed',
      'put:progress',
      'put:response',
      'confirm'
    ]);
    expect(documentsApi.confirmUpload).toHaveBeenCalledWith(
      jasmine.objectContaining({
        documentId: '11111111-1111-4111-8111-111111111111',
        storageObjectKey: 'documents/test.pdf',
        storageBucket: 'fos-workspace'
      })
    );
  });

  it('does not confirm upload when the presigned PUT fails', async () => {
    const file = new File(['resume'], 'attestation.pdf', { type: 'application/pdf' });

    documentsApi.initiateUpload.and.returnValue(
      of({
        documentId: '11111111-1111-4111-8111-111111111111',
        uploadUrl: 'http://localhost:9000/fos-workspace/documents/test.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256',
        objectKey: 'documents/test.pdf'
      })
    );
    documentsApi.uploadBinary.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' }))
    );

    await component['uploadFilesThroughBackend']([file]);

    expect(documentsApi.confirmUpload).not.toHaveBeenCalled();
    expect(component['uploadError']).toBe('File upload failed before confirmation. Please try again.');
  });

  it('shows a registration error when storage upload succeeds but confirm fails', async () => {
    const file = new File(['resume'], 'attestation.pdf', { type: 'application/pdf' });

    documentsApi.initiateUpload.and.returnValue(
      of({
        documentId: '11111111-1111-4111-8111-111111111111',
        uploadUrl: 'http://localhost:9000/fos-workspace/documents/test.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256',
        objectKey: 'documents/test.pdf'
      })
    );
    documentsApi.uploadBinary.and.returnValue(of(new HttpResponse({ status: 204, body: '' })));
    documentsApi.confirmUpload.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' }))
    );

    await component['uploadFilesThroughBackend']([file]);

    expect(component['uploadError']).toBe('File uploaded to storage, but document registration failed. Please retry.');
  });

  it('deduplicates the same file within one selection before initiating upload', async () => {
    const file = new File(['resume'], 'attestation.pdf', { type: 'application/pdf', lastModified: 1717581600000 });

    documentsApi.initiateUpload.and.returnValue(
      of({
        documentId: '11111111-1111-4111-8111-111111111111',
        uploadUrl: 'http://localhost:9000/fos-workspace/documents/test.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256',
        objectKey: 'documents/test.pdf'
      })
    );
    documentsApi.uploadBinary.and.returnValue(of(new HttpResponse({ status: 204, body: '' })));
    documentsApi.confirmUpload.and.returnValue(of(mockDocumentResponse()));

    await component['uploadFilesThroughBackend']([file, file]);

    expect(documentsApi.initiateUpload).toHaveBeenCalledTimes(1);
    expect(documentsApi.confirmUpload).toHaveBeenCalledTimes(1);
  });

  it('treats a duplicate confirm 409 as an already-completed upload and refreshes documents', async () => {
    const file = new File(['resume'], 'attestation.pdf', { type: 'application/pdf' });

    documentsApi.initiateUpload.and.returnValue(
      of({
        documentId: '11111111-1111-4111-8111-111111111111',
        uploadUrl: 'http://localhost:9000/fos-workspace/documents/test.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256',
        objectKey: 'documents/test.pdf'
      })
    );
    documentsApi.uploadBinary.and.returnValue(of(new HttpResponse({ status: 204, body: '' })));
    documentsApi.confirmUpload.and.returnValue(
      throwError(() =>
        new HttpErrorResponse({
          status: 409,
          statusText: 'Conflict',
          error: { code: 'CONFLICT', message: 'Upload was already confirmed for this document.' }
        })
      )
    );

    await component['uploadFilesThroughBackend']([file]);

    expect(component['uploadError']).toBe('');
    expect(component['uploadInfo']).toBe('This upload was already confirmed once. The document list has been refreshed.');
  });

  it('shows a clear conflict message when backend rejects a duplicate upload', async () => {
    const file = new File(['resume'], 'attestation.pdf', { type: 'application/pdf' });

    documentsApi.initiateUpload.and.returnValue(
      throwError(() =>
        new HttpErrorResponse({
          status: 409,
          statusText: 'Conflict',
          error: { code: 'CONFLICT', message: 'A document with this name already exists.' }
        })
      )
    );

    await component['uploadFilesThroughBackend']([file]);

    expect(component['uploadError']).toBe('A document with this name or upload session already exists.');
  });

  it('ignores a new file selection while an upload is already running', () => {
    component['uploadBusy'] = true;

    component['onFilesSelected']([new File(['resume'], 'attestation.pdf', { type: 'application/pdf' })]);

    expect(documentsApi.initiateUpload).not.toHaveBeenCalled();
  });

  it('accepts alternate backend page payload keys when loading documents', () => {
    documentsApi.listDocumentsByCategory.and.returnValue(of({ documents: [mockDocumentResponse()] }));

    fixture.detectChanges();

    expect(component['visibleDocuments'].length).toBe(1);
    expect(component['visibleDocuments'][0].category).toBe('General');
  });

  function mockDocumentResponse(): BackendDocumentResponse {
    return {
      documentId: '11111111-1111-4111-8111-111111111111',
      name: 'attestation',
      description: null,
      category: 'GENERAL',
      visibility: 'CLUB_WIDE',
      state: 'ACTIVE',
      ownerRefId: null,
      linkedPlayerRefId: null,
      linkedTeamRefId: null,
      tags: [],
      versionCount: 1,
      currentVersion: {
        versionId: '22222222-2222-4222-8222-222222222222',
        originalFilename: 'attestation.pdf',
        contentType: 'application/pdf',
        fileSizeBytes: 6,
        versionNumber: 1,
        uploadedByActorId: null,
        uploadedAt: '2026-06-04T10:00:00Z',
        versionNote: 'Uploaded from workspace documents UI'
      },
      createdAt: '2026-06-04T10:00:00Z',
      updatedAt: '2026-06-04T10:00:00Z',
      downloadUrl: null
    };
  }
});
