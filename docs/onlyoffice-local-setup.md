# OnlyOffice Local URL Setup

Use three separate URL roles for local OnlyOffice development:

- `ONLYOFFICE_DOCUMENT_SERVER_URL`
  The URL the browser uses to load OnlyOffice `api.js`.
  Example: `http://192.168.0.126:8084`

- `ONLYOFFICE_BACKEND_PUBLIC_URL`
  The backend/gateway base used inside generated `document.url`.
  This must be reachable from both the host browser and the `fos-onlyoffice` container.
  Local LAN example: `http://192.168.0.126:8080`

- `ONLYOFFICE_CALLBACK_BASE_URL`
  The backend/gateway base used inside generated `editorConfig.callbackUrl`.
  In local LAN debugging, keep this aligned with `ONLYOFFICE_BACKEND_PUBLIC_URL`.

Recommended local browser-openable setup:

```env
HOST_LAN_IP=192.168.0.126
ONLYOFFICE_DOCUMENT_SERVER_URL=http://${HOST_LAN_IP}:8084
ONLYOFFICE_PUBLIC_URL=${ONLYOFFICE_DOCUMENT_SERVER_URL}
ONLYOFFICE_BACKEND_PUBLIC_URL=http://${HOST_LAN_IP}:8080
ONLYOFFICE_CALLBACK_BASE_URL=http://${HOST_LAN_IP}:8080
```

Notes:

- Normal Angular API calls still go through `http://localhost:8080/api/v1/...`.
- Do not use the OnlyOffice Document Server URL as `document.url`.
- Do not use `http://192.168.0.126:8084/downloadfile/...` for backend download.
- Use `host.docker.internal` only when the URL is intended for container consumption only and does not need to be opened from the host browser.
