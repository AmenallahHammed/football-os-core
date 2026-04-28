import { Component, EventEmitter, Input, Output } from '@angular/core';

export type DocumentsSectionId =
  | 'all-files'
  | 'recents'
  | 'favorite'
  | 'photos'
  | 'shared'
  | 'file-requests'
  | 'deleted-files';

export interface DocumentsSidebarFolder {
  id: string;
  name: string;
  depth: number;
}

interface SidebarSection {
  id: DocumentsSectionId;
  label: string;
}

@Component({
  selector: 'app-documents-sidebar',
  standalone: true,
  imports: [],
  templateUrl: './documents-sidebar.component.html',
  styleUrl: './documents-sidebar.component.scss'
})
export class DocumentsSidebarComponent {
  @Input({ required: true }) activeSection: DocumentsSectionId = 'all-files';
  @Input() folderTree: DocumentsSidebarFolder[] = [];
  @Input() currentFolderId: string | null = null;
  @Input() allFilesExpanded = true;

  @Output() sectionChanged = new EventEmitter<DocumentsSectionId>();
  @Output() folderSelected = new EventEmitter<string | null>();
  @Output() allFilesExpandedChange = new EventEmitter<boolean>();

  protected readonly primarySections: SidebarSection[] = [
    { id: 'recents', label: 'Recents' },
    { id: 'favorite', label: 'Favorite' },
    { id: 'photos', label: 'Photos' },
    { id: 'shared', label: 'Shared' },
    { id: 'file-requests', label: 'File Requests' }
  ];

  protected readonly deletedSection: SidebarSection = {
    id: 'deleted-files',
    label: 'Deleted Files'
  };

  protected changeSection(sectionId: DocumentsSectionId): void {
    this.sectionChanged.emit(sectionId);
  }

  protected toggleTree(event: MouseEvent): void {
    event.stopPropagation();
    this.allFilesExpandedChange.emit(!this.allFilesExpanded);
  }

  protected selectRoot(): void {
    this.sectionChanged.emit('all-files');
    this.folderSelected.emit(null);
  }

  protected selectFolder(folderId: string): void {
    this.sectionChanged.emit('all-files');
    this.folderSelected.emit(folderId);
  }
}
