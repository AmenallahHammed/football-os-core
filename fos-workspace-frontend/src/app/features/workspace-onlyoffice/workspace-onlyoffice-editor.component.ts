import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { BackendDocumentResponse, DocumentsApiService } from '../documents/documents-api.service';
import { OnlyOfficeConfigResponse, OnlyOfficeEditorMode } from '../../shared/onlyoffice/onlyoffice-config.model';
import { OnlyofficeConfigApiService } from '../../shared/onlyoffice/onlyoffice-config-api.service';
import { OnlyofficeScriptLoaderService } from '../../shared/onlyoffice/onlyoffice-script-loader.service';

declare global {
  interface Window {
    DocsAPI?: {
      DocEditor: new (placeholderId: string, config: unknown) => {
        destroyEditor?: () => void;
      };
    };
  }
}

@Component({
  selector: 'app-workspace-onlyoffice-editor',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './workspace-onlyoffice-editor.component.html',
  styleUrl: './workspace-onlyoffice-editor.component.scss'
})
export class WorkspaceOnlyofficeEditorComponent implements OnInit, OnDestroy {
  private static readonly EDITOR_CONTAINER_ID = 'onlyoffice-editor-host';
  private static readonly SUPPORTED_EXTENSIONS = new Set(['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'odt', 'ods', 'odp', 'pdf']);
  private static readonly UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  private readonly route = inject(ActivatedRoute);
  private readonly onlyofficeApi = inject(OnlyofficeConfigApiService);
  private readonly onlyofficeScriptLoader = inject(OnlyofficeScriptLoaderService);
  private readonly documentsApi = inject(DocumentsApiService);
  private readonly subscriptions = new Subscription();

  private editorInstance: { destroyEditor?: () => void } | null = null;

  protected readonly isProduction = environment.production;
  protected documentId = '';
  protected documentName = '';
  protected fileTypeHint = '';
  protected mode: OnlyOfficeEditorMode = 'edit';
  protected showDebug = false;
  protected loading = true;
  protected errorMessage = '';
  protected errorDetail = '';
  protected networkWarning = '';
  protected onlyOfficeScriptUrl = '';
  protected configResponse: OnlyOfficeConfigResponse | null = null;
  protected docsApiAvailable = false;

  ngOnInit(): void {
    this.showDebug = this.route.snapshot.data['onlyofficeDebug'] === true;

    this.subscriptions.add(
      this.route.paramMap.subscribe((params) => {
        this.documentId = params.get('documentId') ?? '';
        this.documentName = this.route.snapshot.queryParamMap.get('name') ?? '';
        this.fileTypeHint = (this.route.snapshot.queryParamMap.get('fileType') ?? '').trim();
        this.mode = this.resolveMode();
        void this.initializeEditor();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.destroyEditor();
  }

  protected get configPreview(): string {
    return this.configResponse ? JSON.stringify(this.sanitizedConfigResponse(this.configResponse), null, 2) : '';
  }

  protected get isSupportedForOnlyOffice(): boolean {
    const extension = this.documentExtension;
    return !extension || WorkspaceOnlyofficeEditorComponent.SUPPORTED_EXTENSIONS.has(extension);
  }

  protected get isPdfMode(): boolean {
    return this.mode === 'view' && this.documentExtension === 'pdf';
  }

  private get documentExtension(): string {
    const explicitType = this.fileTypeHint.toLowerCase().replace(/^\./, '');
    if (explicitType) {
      return explicitType;
    }

    const name = this.documentName.trim();
    const extensionFromName = name.includes('.') ? name.split('.').pop()?.toLowerCase() : '';
    return extensionFromName?.replace(/^\./, '') ?? '';
  }

  private resolveMode(): OnlyOfficeEditorMode {
    const queryMode = this.route.snapshot.queryParamMap.get('mode');
    if (queryMode === 'view' || queryMode === 'edit') {
      return queryMode;
    }

    return this.documentExtension === 'pdf' ? 'view' : 'edit';
  }

  private async initializeEditor(): Promise<void> {
    this.destroyEditor();
    this.loading = true;
    this.errorMessage = '';
    this.errorDetail = '';
    this.networkWarning = '';
    this.configResponse = null;
    this.onlyOfficeScriptUrl = '';
    this.docsApiAvailable = false;

    if (!this.documentId.trim()) {
      this.loading = false;
      this.setEditorError('Could not open this document in OnlyOffice.', 'Missing documentId route parameter.');
      return;
    }

    if (!WorkspaceOnlyofficeEditorComponent.UUID_PATTERN.test(this.documentId.trim())) {
      this.loading = false;
      this.setEditorError('Could not open this document in OnlyOffice.', 'Invalid documentId format. Expected a UUID from backend documents.');
      return;
    }

    if (!this.isSupportedForOnlyOffice) {
      this.loading = false;
      this.setEditorError('Could not open this document in OnlyOffice.', 'Unsupported file type for OnlyOffice.');
      return;
    }

    this.debugLog('OnlyOffice config endpoint', `${environment.gatewayBaseUrl}/api/v1/onlyoffice/config`);
    this.debugLog('OnlyOffice requested documentId', this.documentId);
    this.debugLog('OnlyOffice requested mode', this.mode);

    const preflightDocument = await this.loadDocumentPreflight();
    if (!preflightDocument) {
      this.loading = false;
      return;
    }

    this.subscriptions.add(
      this.onlyofficeApi.getEditorConfig(this.documentId, this.mode).subscribe({
        next: async (response) => {
          try {
            this.configResponse = response;
            this.debugLog('OnlyOffice config response (sanitized)', this.sanitizedConfigResponse(response));
            this.debugLog('OnlyOffice document.url', response.config?.document?.url ?? '');
            this.debugLog('OnlyOffice document.fileType', response.config?.document?.fileType ?? '');
            this.debugLog('OnlyOffice document.key', response.config?.document?.key ?? '');
            this.debugLog('OnlyOffice document.title', response.config?.document?.title ?? '');
            this.debugLog('OnlyOffice callbackUrl', response.config?.editorConfig?.callbackUrl ?? '');
            this.debugLog('OnlyOffice mode', response.config?.editorConfig?.mode ?? this.mode);

            this.networkWarning = this.buildNetworkWarning(response);
            if (this.networkWarning) {
              this.debugLog('OnlyOffice Docker reachability warning', this.networkWarning);
            }

            if (this.isKnownUnreachableDocumentUrl(response.config?.document?.url ?? '')) {
              this.loading = false;
              this.setEditorError(
                'OnlyOffice could not download this document.',
                'The generated document URL is not reachable from Dockerized OnlyOffice in this environment.'
              );
              return;
            }

            const scriptBaseUrl = this.resolveDocumentServerBaseUrl(response.documentServerUrl);
            this.onlyOfficeScriptUrl = `${scriptBaseUrl}/web-apps/apps/api/documents/api.js`;
            this.debugLog('OnlyOffice script URL', this.onlyOfficeScriptUrl);

            await this.onlyofficeScriptLoader.load(scriptBaseUrl);
            this.docsApiAvailable = !!window.DocsAPI;
            this.debugLog('OnlyOffice DocsAPI present', !!window.DocsAPI);

            if (!window.DocsAPI) {
              this.loading = false;
              this.setEditorError('Could not open this document in OnlyOffice.', 'OnlyOffice DocsAPI was not found after script load.');
              return;
            }

            this.editorInstance = new window.DocsAPI.DocEditor(WorkspaceOnlyofficeEditorComponent.EDITOR_CONTAINER_ID, response.config);
            this.debugLog('OnlyOffice editor initialization', 'success');
            this.loading = false;
          } catch (error) {
            this.loading = false;
            const message = this.formatUnknownError(error);
            this.setEditorError('Could not open this document in OnlyOffice.', message);
            this.debugLog('OnlyOffice editor initialization', message);
          }
        },
        error: (error: HttpErrorResponse) => {
          this.loading = false;
          this.setEditorError('Could not open this document in OnlyOffice.', this.mapHttpError(error));
          this.debugLog('OnlyOffice config request failed', {
            status: error.status,
            message: error.message,
            response: error.error
          });
        }
      })
    );
  }

  private mapHttpError(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Cannot reach gateway at http://localhost:8080.';
    }

    if (error.status === 400) {
      return 'Config request rejected. Check documentId format (UUID) and mode.';
    }

    if (error.status === 401 || error.status === 403) {
      return 'Unauthorized to open this document. Check authentication/permissions.';
    }

    if (error.status === 404) {
      return 'OnlyOffice endpoint not found through gateway. Check route mapping.';
    }

    if (error.status >= 500) {
      const backendMessage = this.extractBackendMessage(error);
      if (backendMessage) {
        return `Backend failed while preparing OnlyOffice config: ${backendMessage}`;
      }
      return 'Backend failed while preparing OnlyOffice config.';
    }

    return `OnlyOffice config request failed with status ${error.status}.`;
  }

  private formatUnknownError(error: unknown): string {
    if (error instanceof Error && error.message) {
      return error.message;
    }

    return 'Unexpected OnlyOffice initialization error.';
  }

  private async loadDocumentPreflight(): Promise<BackendDocumentResponse | null> {
    try {
      const documentItem = await firstValueFrom(this.documentsApi.getDocument(this.documentId));
      const extension = this.resolveMetadataExtension(documentItem);
      const contentType = documentItem.currentVersion?.contentType ?? '';

      this.debugLog('OnlyOffice preflight document', {
        id: documentItem.documentId,
        name: documentItem.name,
        extension,
        contentType,
        hasCurrentVersion: !!documentItem.currentVersion,
        versionCount: documentItem.versionCount,
        latestVersionId: documentItem.currentVersion?.versionId ?? null,
        latestUploadedAt: documentItem.currentVersion?.uploadedAt ?? null
      });

      if (!documentItem.currentVersion) {
        this.setEditorError(
          'Could not open this document in OnlyOffice.',
          'The selected document does not have a confirmed uploaded version yet.'
        );
        return null;
      }

      if (extension && !WorkspaceOnlyofficeEditorComponent.SUPPORTED_EXTENSIONS.has(extension)) {
        this.setEditorError('Could not open this document in OnlyOffice.', `Unsupported file type "${extension}" for OnlyOffice.`);
        return null;
      }

      if (extension === 'pdf' && this.mode === 'edit') {
        this.mode = 'view';
        this.debugLog('OnlyOffice mode override', 'PDF detected; forcing view mode.');
      }

      return documentItem;
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        const detail =
          error.status === 404
            ? 'The requested document was not found.'
            : `Document preflight check failed with status ${error.status}.`;
        this.setEditorError('Could not open this document in OnlyOffice.', detail);
        this.debugLog('OnlyOffice preflight failed', {
          status: error.status,
          message: error.message,
          response: error.error
        });
        return null;
      }

      this.setEditorError('Could not open this document in OnlyOffice.', 'Document preflight check failed unexpectedly.');
      return null;
    }
  }

  private resolveMetadataExtension(documentItem: BackendDocumentResponse): string {
    const hinted = this.fileTypeHint.toLowerCase().replace(/^\./, '');
    if (hinted) {
      return hinted;
    }

    const fileName = documentItem.currentVersion?.originalFilename ?? documentItem.name;
    if (fileName.includes('.')) {
      return fileName.split('.').pop()?.toLowerCase()?.replace(/^\./, '') ?? '';
    }

    const contentType = documentItem.currentVersion?.contentType ?? '';
    if (contentType.includes('pdf')) {
      return 'pdf';
    }
    if (contentType.includes('word')) {
      return 'docx';
    }
    if (contentType.includes('sheet') || contentType.includes('excel')) {
      return 'xlsx';
    }
    if (contentType.includes('presentation') || contentType.includes('powerpoint')) {
      return 'pptx';
    }
    if (contentType.includes('text')) {
      return 'txt';
    }

    return '';
  }

  private extractBackendMessage(error: HttpErrorResponse): string {
    const body = error.error;
    if (!body) {
      return '';
    }

    if (typeof body === 'string') {
      return body;
    }

    if (typeof body === 'object') {
      const message = (body as Record<string, unknown>)['message'];
      return typeof message === 'string' ? message : '';
    }

    return '';
  }

  private setEditorError(title: string, detail: string): void {
    this.errorMessage = title;
    this.errorDetail = detail;
  }

  private sanitizedConfigResponse(response: OnlyOfficeConfigResponse): Record<string, unknown> {
    return {
      documentServerUrl: response.documentServerUrl,
      config: {
        document: {
          fileType: response.config?.document?.fileType ?? null,
          key: response.config?.document?.key ?? null,
          title: response.config?.document?.title ?? null,
          url: response.config?.document?.url ?? null
        },
        editorConfig: {
          callbackUrl: response.config?.editorConfig?.callbackUrl ?? null,
          mode: response.config?.editorConfig?.mode ?? null,
          lang: response.config?.editorConfig?.lang ?? null
        },
        documentType: response.config?.documentType ?? null,
        token: '[redacted]'
      },
      token: '[redacted]'
    };
  }

  private buildNetworkWarning(response: OnlyOfficeConfigResponse): string {
    const documentUrl = response.config?.document?.url ?? '';
    const callbackUrl = response.config?.editorConfig?.callbackUrl ?? '';
    const warnings: string[] = [];

    if (this.isLikelyContainerUnreachableUrl(documentUrl)) {
      warnings.push(
        'OnlyOffice document.url appears container-unreachable. Dockerized OnlyOffice may fail to download the file.'
      );
    }

    if (this.isLikelyContainerUnreachableUrl(callbackUrl)) {
      warnings.push(
        'OnlyOffice callbackUrl appears container-unreachable. Saving edits back to backend may fail.'
      );
    }

    return warnings.join(' ');
  }

  private isLikelyContainerUnreachableUrl(rawUrl: string): boolean {
    if (!rawUrl.trim()) {
      return false;
    }

    try {
      const parsed = new URL(rawUrl);
      const host = parsed.hostname.toLowerCase();
      return host === 'localhost' || host === '127.0.0.1' || host === 'noop.fos.local';
    } catch {
      return false;
    }
  }

  private isKnownUnreachableDocumentUrl(rawUrl: string): boolean {
    if (!rawUrl.trim()) {
      return false;
    }

    try {
      const parsed = new URL(rawUrl);
      const host = parsed.hostname.toLowerCase();
      return host === 'noop.fos.local';
    } catch {
      return false;
    }
  }

  private destroyEditor(): void {
    try {
      this.editorInstance?.destroyEditor?.();
    } catch {
      // no-op
    }
    this.editorInstance = null;
  }

  private resolveDocumentServerBaseUrl(responseUrl: string | null | undefined): string {
    const fallback = environment.onlyOfficeDocumentServerUrl.replace(/\/+$/, '');
    const raw = (responseUrl ?? '').trim();
    if (!raw) {
      return fallback;
    }

    try {
      const parsed = new URL(raw);
      const hostname = parsed.hostname.toLowerCase();
      if (hostname === 'onlyoffice' || hostname === 'fos-onlyoffice') {
        this.debugLog('OnlyOffice response URL is container-internal; using frontend fallback URL', fallback);
        return fallback;
      }

      return raw.replace(/\/+$/, '');
    } catch {
      return fallback;
    }
  }

  private debugLog(label: string, payload: unknown): void {
    if (environment.production) {
      return;
    }

    console.log(`[OnlyOffice Debug] ${label}:`, payload);
  }
}
