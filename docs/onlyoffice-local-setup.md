# OnlyOffice Local URL Setup

Use three separate URL roles for local OnlyOffice development:

- `ONLYOFFICE_DOCUMENT_SERVER_URL`
  The URL the browser uses to load OnlyOffice `api.js`.
  Example: `http://football-os.local:8084`

- `ONLYOFFICE_BACKEND_PUBLIC_URL`
  The backend/gateway base used inside generated `document.url`.
  This must be reachable from both the host browser and the `fos-onlyoffice` container.
  Stable local example: `http://football-os.local:8080`

- `ONLYOFFICE_CALLBACK_BASE_URL`
  The backend/gateway base used inside generated `editorConfig.callbackUrl`.
  In stable local debugging, keep this aligned with `ONLYOFFICE_BACKEND_PUBLIC_URL`.

Recommended stable local setup:

```env
LOCAL_PUBLIC_HOST=football-os.local
ONLYOFFICE_DOCUMENT_SERVER_URL=http://${LOCAL_PUBLIC_HOST}:8084
ONLYOFFICE_PUBLIC_URL=${ONLYOFFICE_DOCUMENT_SERVER_URL}
ONLYOFFICE_BACKEND_PUBLIC_URL=http://${LOCAL_PUBLIC_HOST}:8080
ONLYOFFICE_CALLBACK_BASE_URL=http://${LOCAL_PUBLIC_HOST}:8080
MINIO_PUBLIC_ENDPOINT=http://${LOCAL_PUBLIC_HOST}:9000
```

Notes:

- Add `127.0.0.1 football-os.local` to the Windows hosts file.
- Normal Angular API calls still go through `http://localhost:8080/api/v1/...`.
- Do not use the OnlyOffice Document Server URL as `document.url`.
- Do not use changing LAN IPs like `192.168.x.x` for stable local development.
- Do not use `http://football-os.local:8084/downloadfile/...` for backend download.
- Keep `MINIO_ENDPOINT=http://minio:9000` for backend container to MinIO communication in full Docker mode.
- Use `MINIO_PUBLIC_ENDPOINT` only for presigned URLs that must be reachable from both the browser and containers.
