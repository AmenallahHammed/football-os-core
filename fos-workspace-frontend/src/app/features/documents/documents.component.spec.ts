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
  let documentsApi: jasmine.SpyObj<
    Pick<DocumentsApiService, 'initiateUpload' | 'uploadBinary' | 'confirmUpload' | 'listDocumentsByCategory'>
  >;

  beforeEach(async () => {
    documentsApi = jasmine.createSpyObj('DocumentsApiService', [
      'initiateUpload',
      'uploadBinary',
      'confirmUpload',
      'listDocumentsByCategory'
    ]);
    documentsApi.listDocumentsByCategory.and.returnValue(of({ content: [] }));

    await TestBed.configureTestingModule({
      imports: [DocumentsComponent],
      providers: [
        {
          provide: WorkspaceDataService,
          useValue: jasmine.createSpyObj('WorkspaceDataService', ['getFolders', 'getParticipants', 'createFolder'])
        },
        {
          provide: Router,
          useValue: jasmine.createSpyObj('Router', ['navigate'])
        },
        {
          provide: AuthService,
          useValue: jasmine.createSpyObj('AuthService', ['roles'])
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

  it('ignores a new file selection while an upload is already running', () => {
    component['uploadBusy'] = true;

    component['onFilesSelected']([new File(['resume'], 'attestation.pdf', { type: 'application/pdf' })]);

    expect(documentsApi.initiateUpload).not.toHaveBeenCalled();
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
