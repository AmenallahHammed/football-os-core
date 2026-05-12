import { Injectable } from '@angular/core';

declare global {
  interface Window {
    DocsAPI?: {
      DocEditor: new (placeholderId: string, config: unknown) => {
        destroyEditor?: () => void;
      };
    };
  }
}

@Injectable({
  providedIn: 'root'
})
export class OnlyofficeScriptLoaderService {
  private readonly loadByUrl = new Map<string, Promise<void>>();

  load(documentServerUrl: string): Promise<string> {
    const normalizedBase = documentServerUrl.replace(/\/+$/, '');
    const scriptUrl = `${normalizedBase}/web-apps/apps/api/documents/api.js`;

    if (window.DocsAPI) {
      return Promise.resolve(scriptUrl);
    }

    const existingPromise = this.loadByUrl.get(scriptUrl);
    if (existingPromise) {
      return existingPromise.then(() => scriptUrl);
    }

    const loadPromise = new Promise<void>((resolve, reject) => {
      const existingScript = document.querySelector<HTMLScriptElement>(`script[src="${scriptUrl}"]`);
      if (existingScript?.dataset['loaded'] === 'true') {
        resolve();
        return;
      }

      if (existingScript) {
        existingScript.addEventListener('load', () => resolve(), { once: true });
        existingScript.addEventListener('error', () => reject(new Error(`Failed to load OnlyOffice script: ${scriptUrl}`)), {
          once: true
        });
        return;
      }

      const script = document.createElement('script');
      script.type = 'text/javascript';
      script.src = scriptUrl;
      script.async = true;
      script.onload = () => {
        script.dataset['loaded'] = 'true';
        resolve();
      };
      script.onerror = () => reject(new Error(`Failed to load OnlyOffice script: ${scriptUrl}`));
      document.head.appendChild(script);
    });

    this.loadByUrl.set(scriptUrl, loadPromise);
    return loadPromise.then(() => scriptUrl);
  }
}
