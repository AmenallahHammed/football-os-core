export type DocumentStatus = 'Draft' | 'Active' | 'Archived';

export interface FolderNode {
  id: string;
  name: string;
  parentId: string | null;
}

export interface WorkspaceDocument {
  id: string;
  name: string;
  fileType: string;
  uploadedAt: string;
  status: DocumentStatus;
  folderId: string | null;
  icon: string;
}
