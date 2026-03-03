# Generated API Client

Dieser Ordner enthält den generierten TypeScript-Client für die Querchecker-API.

## Generieren

```bash
# Backend muss laufen (http://localhost:14070)
npm run generate-api
```

Der Generator liest die OpenAPI-Spec von `http://localhost:14070/v3/api-docs` und erzeugt
TypeScript-Services + Modelle in diesem Ordner.

Nach der Generierung die generierten Dateien committen (Option B).
