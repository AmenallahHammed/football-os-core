import { DatePipe } from '@angular/common';
import { animate, style, transition, trigger } from '@angular/animations';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { BreadcrumbComponent, BreadcrumbSegment } from '../../shared/breadcrumb/breadcrumb.component';
import { ContextMenuAction, ContextMenuComponent } from '../../shared/context-menu/context-menu.component';
import { DropzoneComponent } from '../../shared/dropzone/dropzone.component';
import { WorkspaceDocument } from '../../shared/models/document.model';
import { OnlyofficeEditorComponent } from '../../shared/onlyoffice-editor/onlyoffice-editor.component';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';

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
    OnlyofficeEditorComponent
  ],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss',
  animations: [
    trigger('panelSlide', [
      transition(':enter', [
        style({ transform: 'translateX(24px)', opacity: 0 }),
        animate('220ms ease-out', style({ transform: 'translateX(0)', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('160ms ease-in', style({ transform: 'translateX(24px)', opacity: 0 }))
      ])
    ])
  ]
})
export class DocumentsComponent {
  protected searchTerm = '';
  protected selectedFileType = 'all';
  protected selectedStatus = 'all';
  protected selectedDate = '';
  protected selectedFolderId: string | null = null;
  protected showDropzone = false;
  protected selectedDocument: WorkspaceDocument | null = null;

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

  constructor(private readonly workspaceData: WorkspaceDataService) {}

  protected get folders() {
    return this.workspaceData.getFolders();
  }

  protected get rootFolders() {
    return this.folders.filter((folder) => folder.parentId === null);
  }

  protected get fileTypes(): string[] {
    return [...new Set(this.workspaceData.getDocuments().map((doc) => doc.fileType))].sort();
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

    return this.workspaceData
      .getDocuments()
      .filter((doc) => !this.selectedFolderId || doc.folderId === this.selectedFolderId)
      .filter((doc) => this.selectedFileType === 'all' || doc.fileType === this.selectedFileType)
      .filter((doc) => this.selectedStatus === 'all' || doc.status === this.selectedStatus)
      .filter((doc) => !this.selectedDate || doc.uploadedAt === this.selectedDate)
      .filter((doc) => !query || doc.name.toLowerCase().includes(query));
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
    this.workspaceData.uploadDocuments(files, this.selectedFolderId);
    this.showDropzone = false;
  }

  protected createFolder(): void {
    const folderName = window.prompt('New folder name');
    if (!folderName) {
      return;
    }

    this.workspaceData.createFolder(folderName, this.selectedFolderId);
  }

  protected openDocument(document: WorkspaceDocument): void {
    this.selectedDocument = document;
  }

  protected closeEditor(): void {
    this.selectedDocument = null;
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

    const documentId = this.contextDocument.id;

    switch (action.id) {
      case 'rename': {
        const nextName = window.prompt('Rename document', this.contextDocument.name);
        if (nextName) {
          this.workspaceData.renameDocument(documentId, nextName);
        }
        break;
      }
      case 'move': {
        const firstFolder = this.folders[0];
        this.workspaceData.moveDocument(documentId, firstFolder?.id ?? null);
        break;
      }
      case 'archive':
        this.workspaceData.archiveDocument(documentId);
        break;
      case 'delete':
        this.workspaceData.deleteDocument(documentId);
        break;
      default:
        break;
    }

    this.closeContextMenu();
  }

}
