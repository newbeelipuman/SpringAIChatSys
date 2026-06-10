# SpringAIChatSys Public Showcase

This directory is the public showcase version of the project. It includes only publishable source code, UI screenshots, and PNG diagram exports.

## Included

- `src/backend`: Spring Boot backend source code, `pom.xml`, and the public configuration template `.env.example`.
- `src/frontend`: Vue/Vite frontend source code, public build configuration, and dependency manifests.
- `src/scripts`: local helper scripts.
- `screenshots`: UI screenshots.
- `diagrams_png`: PNG exports of workflow diagrams, architecture diagrams, use-case diagrams, and ER diagrams.

## Not Included

This public showcase intentionally excludes:

- Thesis files, defense slides, draft documents, and review records.
- `.env.local`, API keys, database passwords, admin credentials, tokens, and private runtime settings.
- `node_modules`, `target`, `dist`, runtime logs, local data, and Milvus volumes.
- Editable diagram sources such as `.drawio`, `.vsdx`, `.emf`, and non-public vector assets.
- Reference works, third-party papers, archives, and files with uncertain publication status.

## Run

For detailed setup, see [CONFIG_AND_RUN.md](CONFIG_AND_RUN.md).

Backend:

```powershell
cd src/backend
copy .env.example .env.local
mvn spring-boot:run
```

Frontend:

```powershell
cd src/frontend
npm install
npm run dev
```

Create your own `.env.local` for local runs. Do not commit private environment files.

## Copyright

This project is a graduation-design and portfolio showcase by the author. Without written permission from the author, it may not be used for papers, coursework, competitions, software copyright registration, patent applications, commercial projects, or secondary public redistribution.
