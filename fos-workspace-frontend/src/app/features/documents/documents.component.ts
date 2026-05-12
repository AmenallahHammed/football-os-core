import { DatePipe } from '@angular/common';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, firstValueFrom, forkJoin, tap } from 'rxjs';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { BreadcrumbComponent, BreadcrumbSegment } from '../../shared/breadcrumb/breadcrumb.component';
import { ContextMenuAction, ContextMenuComponent } from '../../shared/context-menu/context-menu.component';
import { DropzoneComponent } from '../../shared/dropzone/dropzone.component';
import { WorkspaceDocument } from '../../shared/models/document.model';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import {
  CreateFolderDialogComponent,
  CreateFolderDialogPerson,
  CreateFolderFormValue
} from './create-folder-dialog.component';
import {
  BackendConfirmUploadRequest,
  BackendDocumentCategory,
  BackendDocumentResponse,
  BackendDocumentVisibility,
  BackendInitiateUploadRequest,
  DocumentsApiService
} from './documents-api.service';

const SUPPORTED_ONLYOFFICE_EXTENSIONS = new Set(['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'odt', 'ods', 'odp', 'pdf']);
const SUPPORTED_UPLOAD_EXTENSIONS = new Set([
  'doc',
  'docx',
  'xls',
  'xlsx',
  'ppt',
  'pptx',
  'pdf',
  'txt',
  'odt',
  'ods',
  'odp',
  'png',
  'jpg',
  'jpeg'
]);
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_UPLOAD_SIZE_BYTES = 25 * 1024 * 1024;
const FALLBACK_STORAGE_BUCKET = 'fos-workspace';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    BreadcrumbComponent,
    DropzoneComponent,
    StatusBadgeComponent,
    ContextMenuComponent,
    CreateFolderDialogComponent
  ],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss'
})
export class DocumentsComponent implements OnInit, OnDestroy {
  private static readonly BACKEND_CATEGORIES: BackendDocumentCategory[] = ['GENERAL', 'MEDICAL', 'ADMIN', 'REPORT', 'CONTRACT'];

  private readonly subscriptions = new Subscription();
  private backendDocuments: WorkspaceDocument[] = [];

  protected searchTerm = '';
  protected selectedFileType = 'all';
  protected selectedStatus = 'all';
  protected selectedDate = '';
  protected selectedFolderId: string | null = null;
  protected showDropzone = false;
  protected uploadCategory: BackendDocumentCategory = 'GENERAL';
  protected uploadVisibility: BackendDocumentVisibility = 'CLUB_WIDE';
  protected uploadBusy = false;
  protected uploadProgress = 0;
  protected uploadStatusMessage = '';
  protected uploadError = '';
  protected uploadInfo = '';
  protected openDocumentError = '';
  protected backendLoadError = '';
  protected contextActionError = '';
  protected contextActionInfo = '';
  protected createFolderError = '';
  protected createFolderInfo = '';
  protected showCreateFolderDialog = false;

  protected contextMenuVisible = false;
  protected contextMenuX = 0;
  protected contextMenuY = 0;
  protected contextDocument: WorkspaceDocument | null = null;

  protected readonly contextActions: ContextMenuAction[] = [
    { id: 'rename', label: 'Rename' },
    { id: 'move', label: 'Move' },
    { id: 'archive', label: 'Archive' },
    { id: 'delete', label: 'Delete' }
  ];

  constructor(
    private readonly workspaceData: WorkspaceDataService,
    private readonly router: Router,
    private readonly documentsApi: DocumentsApiService
  ) {}

  ngOnInit(): void {
    this.loadBackendDocuments();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  protected get folders() {
    return this.workspaceData.getFolders();
  }

  protected get rootFolders() {
    return this.folders.filter((folder) => folder.parentId === null);
  }

  protected get fileTypes(): string[] {
    return [...new Set(this.documentSource.map((doc) => doc.fileType))].sort();
  }

  protected get breadcrumbSegments(): BreadcrumbSegment[] {
    if (!this.selectedFolderId) {
      return [{ id: null, label: 'Documents' }];
    }

    const segmentTrail: BreadcrumbSegment[] = [];
    let currentFolderId: string | null = this.selectedFolderId;

    while (currentFolderId) {
      const folder = this.folders.find((item) => item.id === currentFolderId);
      if (!folder) {
        break;
      }
      segmentTrail.unshift({ id: folder.id, label: folder.name });
      currentFolderId = folder.parentId;
    }

    return [{ id: null, label: 'Documents' }, ...segmentTrail];
  }

  protected get visibleDocuments(): WorkspaceDocument[] {
    const query = this.searchTerm.trim().toLowerCase();

    return this.documentSource
      .filter((doc) => !this.selectedFolderId || doc.folderId === this.selectedFolderId)
      .filter((doc) => this.selectedFileType === 'all' || doc.fileType === this.selectedFileType)
      .filter((doc) => this.selectedStatus === 'all' || doc.status === this.selectedStatus)
      .filter((doc) => !this.selectedDate || doc.uploadedAt === this.selectedDate)
      .filter((doc) => !query || doc.name.toLowerCase().includes(query));
  }

  protected get uploadCategoryOptions(): BackendDocumentCategory[] {
    return DocumentsComponent.BACKEND_CATEGORIES;
  }

  protected get uploadVisibilityOptions(): BackendDocumentVisibility[] {
    return ['CLUB_WIDE', 'TEAM_ONLY', 'PRIVATE'];
  }

  protected selectFolder(folderId: string | null): void {
    this.selectedFolderId = folderId;
  }

  protected childFolders(parentId: string | null) {
    return this.folders.filter((folder) => folder.parentId === parentId);
  }

  protected onBreadcrumbSelected(segment: BreadcrumbSegment): void {
    this.selectedFolderId = segment.id;
  }

  protected onFilesSelected(files: File[]): void {
    void this.uploadFilesThroughBackend(files);
  }

  protected createFolder(): void {
    this.createFolderError = '';
    this.showCreateFolderDialog = true;
  }

  protected closeCreateFolderDialog(): void {
    this.showCreateFolderDialog = false;
  }

  protected get createFolderPeople(): CreateFolderDialogPerson[] {
    return this.workspaceData.getParticipants().map((person) => ({
      id: person.id,
      name: person.name,
      secondaryInfo: `${person.role} - ${person.group}`
    }));
  }

  protected handleCreateFolder(formValue: CreateFolderFormValue): void {
    this.createFolderError = '';
    this.createFolderInfo = '';

    if (!formValue.name.trim()) {
      this.createFolderError = 'Folder name is required.';
      return;
    }

    this.workspaceData.createFolder(formValue.name, this.selectedFolderId);

    // TODO(Folder sharing): include accessType + allowedUserIds in backend folder payload
    // once the documents/folders API supports folder-level access control fields.
    if (formValue.accessType === 'specific') {
      this.createFolderInfo = `Folder created and shared locally with ${formValue.allowedUserIds.length} selected people.`;
    } else {
      this.createFolderInfo = 'Folder created successfully.';
    }

    this.showCreateFolderDialog = false;
  }

  protected openDocument(document: WorkspaceDocument): void {
    this.openDocumentError = '';
    const documentId = document.id.trim();

    if (!UUID_PATTERN.test(documentId)) {
      this.openDocumentError =
        'This document is not linked to a backend UUID yet, so OnlyOffice config cannot be generated.';
      return;
    }

    const extension = this.extensionFor(document);

    if (!extension || !SUPPORTED_ONLYOFFICE_EXTENSIONS.has(extension)) {
      this.openDocumentError = `Unsupported file type "${document.fileType}" for OnlyOffice.`;
      return;
    }

    const mode = extension === 'pdf' ? 'view' : 'edit';

    void this.router.navigate(['/workspace/documents', documentId, 'editor'], {
      queryParams: {
        name: document.name,
        fileType: extension,
        mode
      }
    });
  }

  protected openContextMenu(event: MouseEvent, document: WorkspaceDocument): void {
    event.preventDefault();
    this.contextDocument = document;
    this.contextMenuX = event.clientX;
    this.contextMenuY = event.clientY;
    this.contextMenuVisible = true;
  }

  protected closeContextMenu(): void {
    this.contextMenuVisible = false;
  }

  protected handleContextAction(action: ContextMenuAction): void {
    if (!this.contextDocument) {
      return;
    }

    const documentId = this.contextDocument.id.trim();
    this.contextActionError = '';
    this.contextActionInfo = '';

    if (!UUID_PATTERN.test(documentId)) {
      this.contextActionError = 'This action requires a backend document UUID.';
      this.closeContextMenu();
      return;
    }

    switch (action.id) {
      case 'rename':
        this.contextActionInfo = 'Rename is not available yet because backend API does not expose a rename endpoint.';
        break;
      case 'move':
        this.contextActionInfo = 'Move is not available yet because backend API does not expose a move endpoint.';
        break;
      case 'archive':
      case 'delete':
        this.subscriptions.add(
          this.documentsApi.softDeleteDocument(documentId).subscribe({
            next: () => {
              this.backendDocuments = this.backendDocuments.filter((document) => document.id !== documentId);
              this.contextActionInfo =
                action.id === 'archive' ? 'Document archived successfully.' : 'Document deleted successfully.';
            },
            error: () => {
              this.contextActionError = 'Unable to complete this action through backend API.';
            }
          })
        );
        break;
      default:
        break;
    }

    this.closeContextMenu();
  }

  private extensionFor(document: WorkspaceDocument): string {
    const fromType = document.fileType.trim().toLowerCase().replace(/^\./, '');
    if (fromType) {
      return fromType;
    }

    const fromName = document.name.includes('.') ? document.name.split('.').pop()?.toLowerCase() : '';
    return (fromName ?? '').replace(/^\./, '');
  }

  private get documentSource(): WorkspaceDocument[] {
    return this.backendDocuments.length > 0 ? this.backendDocuments : this.workspaceData.getDocuments();
  }

  private async uploadFilesThroughBackend(files: File[]): Promise<void> {
    if (this.uploadBusy) {
      return;
    }

    this.uploadError = '';
    this.uploadInfo = '';
    this.uploadStatusMessage = '';
    this.uploadProgress = 0;

    if (!files.length) {
      this.uploadError = 'No file selected.';
      return;
    }

    const unsupportedFile = files.find((file) => !this.isSupportedUploadFile(file));
    if (unsupportedFile) {
      this.uploadError = `Unsupported file type for ${unsupportedFile.name}.`;
      return;
    }

    const oversizedFile = files.find((file) => file.size > MAX_UPLOAD_SIZE_BYTES);
    if (oversizedFile) {
      this.uploadError = `${oversizedFile.name} exceeds the 25MB limit.`;
      return;
    }

    this.uploadBusy = true;
    let uploadedCount = 0;

    try {
      for (let index = 0; index < files.length; index += 1) {
        const file = files[index];
        const uploadMetadata = this.buildUploadMetadata(file);

        this.uploadStatusMessage = `Initiating upload ${index + 1}/${files.length}: ${file.name}`;
        this.uploadProgress = 5;
        const initiated = await firstValueFrom(this.documentsApi.initiateUpload(uploadMetadata));

        if (!this.shouldSkipBinaryUpload(initiated.uploadUrl)) {
          this.uploadStatusMessage = `Uploading ${file.name}...`;
          await firstValueFrom(
            this.documentsApi.uploadBinary(initiated.uploadUrl, file).pipe(
              tap((event) => {
                if (event.type === HttpEventType.UploadProgress && event.total) {
                  const fileProgress = Math.round((event.loaded / event.total) * 100);
                  const overallBase = (index / files.length) * 100;
                  const scaledProgress = Math.round((fileProgress / files.length) + overallBase);
                  this.uploadProgress = Math.min(95, scaledProgress);
                }
              })
            )
          );
        } else {
          // TODO(storage-noop): noop storage returns a non-routable upload URL by design.
          // Skip binary transfer and continue confirm so local dev can still exercise workflow.
          this.uploadStatusMessage = `Storage is noop for ${file.name}, confirming metadata only...`;
          this.uploadProgress = 70;
        }

        this.uploadStatusMessage = `Confirming upload ${index + 1}/${files.length}: ${file.name}`;
        this.uploadProgress = 90;
        const confirmPayload: BackendConfirmUploadRequest = {
          ...uploadMetadata,
          documentId: initiated.documentId,
          storageObjectKey: initiated.objectKey,
          storageBucket: this.resolveStorageBucket(initiated.uploadUrl)
        };

        await firstValueFrom(this.documentsApi.confirmUpload(confirmPayload));
        uploadedCount += 1;
        this.uploadProgress = Math.round((uploadedCount / files.length) * 100);
      }

      this.uploadStatusMessage = '';
      this.uploadInfo = uploadedCount === 1 ? 'Document uploaded successfully.' : `${uploadedCount} documents uploaded successfully.`;
      this.showDropzone = false;
      this.loadBackendDocuments();
    } catch (error) {
      this.uploadStatusMessage = '';
      this.uploadError = this.mapUploadError(error);
    } finally {
      this.uploadBusy = false;
      this.uploadProgress = 0;
    }
  }

  private loadBackendDocuments(): void {
    this.backendLoadError = '';

    const requests = DocumentsComponent.BACKEND_CATEGORIES.map((category) => this.documentsApi.listDocumentsByCategory(category));
    this.subscriptions.add(
      forkJoin(requests).subscribe({
        next: (responses) => {
          const mapped = responses
            .flatMap((page) => page.content ?? [])
            .map((documentItem) => this.mapBackendDocument(documentItem));

          this.backendDocuments = this.uniqueById(mapped);
          this.contextActionError = '';
        },
        error: () => {
          this.backendDocuments = [];
          this.backendLoadError = 'Using local documents fallback because backend documents could not be loaded.';
        }
      })
    );
  }

  private isSupportedUploadFile(file: File): boolean {
    const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() ?? '' : '';
    return SUPPORTED_UPLOAD_EXTENSIONS.has(extension);
  }

  private buildUploadMetadata(file: File): BackendInitiateUploadRequest {
    const trimmedName = file.name.trim();
    const dotIndex = trimmedName.lastIndexOf('.');
    const displayName = dotIndex > 0 ? trimmedName.slice(0, dotIndex) : trimmedName;

    // TODO(Folder support): backend upload contracts currently do not accept folderId.
    // When folder-aware upload fields are added server-side, pass selectedFolderId here.
    return {
      name: displayName || trimmedName || 'Untitled document',
      description: null,
      category: this.uploadCategory,
      visibility: this.uploadVisibility,
      originalFilename: trimmedName,
      contentType: file.type || 'application/octet-stream',
      fileSizeBytes: file.size,
      linkedPlayerRefId: null,
      linkedTeamRefId: null,
      tags: [],
      versionNote: 'Uploaded from workspace documents UI'
    };
  }

  private shouldSkipBinaryUpload(uploadUrl: string): boolean {
    try {
      const parsed = new URL(uploadUrl);
      return parsed.hostname.toLowerCase() === 'noop.fos.local';
    } catch {
      return false;
    }
  }

  private resolveStorageBucket(uploadUrl: string): string {
    try {
      const parsed = new URL(uploadUrl);
      const firstSegment = parsed.pathname.split('/').filter(Boolean)[0] ?? '';
      const hasAwsSignatureQuery = parsed.searchParams.has('X-Amz-Algorithm');
      if (hasAwsSignatureQuery && firstSegment) {
        return firstSegment;
      }
    } catch {
      // no-op
    }

    return FALLBACK_STORAGE_BUCKET;
  }

  private mapUploadError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'Cannot reach gateway or upload URL.';
      }
      if (error.status === 400) {
        return 'Upload request rejected by backend.';
      }
      if (error.status === 401 || error.status === 403) {
        return 'You are not authorized to upload this document.';
      }
      if (error.status === 404) {
        return 'Upload endpoint was not found through gateway.';
      }
      if (error.status >= 500) {
        return 'Backend failed while processing the upload.';
      }
      return `Upload failed with status ${error.status}.`;
    }

    if (error instanceof Error && error.message) {
      return error.message;
    }

    return 'Upload failed due to an unexpected error.';
  }

  private uniqueById(documents: WorkspaceDocument[]): WorkspaceDocument[] {
    const byId = new Map<string, WorkspaceDocument>();
    for (const documentItem of documents) {
      byId.set(documentItem.id, documentItem);
    }
    return [...byId.values()];
  }

  private mapBackendDocument(documentItem: BackendDocumentResponse): WorkspaceDocument {
    const extension = this.extensionFromBackend(documentItem).toUpperCase();

    return {
      id: documentItem.documentId,
      name: documentItem.name,
      fileType: extension || 'FILE',
      uploadedAt: this.dateOnlyValue(documentItem.currentVersion?.uploadedAt ?? documentItem.createdAt),
      status: this.mapStatus(documentItem.state),
      folderId: null,
      icon: this.iconForExtension(extension)
    };
  }

  private extensionFromBackend(documentItem: BackendDocumentResponse): string {
    const originalFilename = documentItem.currentVersion?.originalFilename ?? '';
    const fromFilename = originalFilename.includes('.') ? originalFilename.split('.').pop()?.toLowerCase() ?? '' : '';
    if (fromFilename) {
      return fromFilename;
    }

    const fromName = documentItem.name.includes('.') ? documentItem.name.split('.').pop()?.toLowerCase() ?? '' : '';
    if (fromName) {
      return fromName;
    }

    const contentType = documentItem.currentVersion?.contentType ?? '';
    if (contentType.includes('word')) {
      return 'docx';
    }
    if (contentType.includes('sheet') || contentType.includes('excel')) {
      return 'xlsx';
    }
    if (contentType.includes('presentation') || contentType.includes('powerpoint')) {
      return 'pptx';
    }
    if (contentType.includes('pdf')) {
      return 'pdf';
    }
    if (contentType.includes('text')) {
      return 'txt';
    }

    return '';
  }

  private mapStatus(state: string): WorkspaceDocument['status'] {
    const normalized = state.trim().toUpperCase();
    if (normalized === 'ARCHIVED') {
      return 'Archived';
    }
    if (normalized === 'DRAFT') {
      return 'Draft';
    }
    return 'Active';
  }

  private iconForExtension(extension: string): string {
    const normalized = extension.toLowerCase();
    if (normalized === 'pdf') {
      return '[PDF]';
    }
    if (normalized === 'doc' || normalized === 'docx' || normalized === 'odt' || normalized === 'txt') {
      return '[DOC]';
    }
    if (normalized === 'xls' || normalized === 'xlsx' || normalized === 'ods') {
      return '[XLS]';
    }
    if (normalized === 'ppt' || normalized === 'pptx' || normalized === 'odp') {
      return '[PPT]';
    }
    return '[FILE]';
  }

  private dateOnlyValue(value: string): string {
    if (!value) {
      return '';
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return '';
    }

    return `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(parsed.getDate()).padStart(2, '0')}`;
  }
}
