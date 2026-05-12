export type OnlyOfficeEditorMode = 'view' | 'edit';

export interface OnlyOfficeConfigRequest {
  documentId: string;
  mode: OnlyOfficeEditorMode;
}

export interface OnlyOfficeConfigResponse {
  documentServerUrl: string;
  config: {
    document: {
      fileType: string;
      key: string;
      title: string;
      url: string;
    };
    editorConfig: {
      callbackUrl: string;
      lang: string;
      user: {
        id: string;
        name: string;
      };
      mode: OnlyOfficeEditorMode;
      customization: {
        autosave: boolean;
        forcesave: boolean;
      };
    };
    documentType: string;
    token: string;
  };
  token: string;
}
