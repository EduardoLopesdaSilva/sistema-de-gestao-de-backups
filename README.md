# Gestão de informações de backup

O sistema mantém cadastros sobre backups. **A aplicação não recebe, armazena, navega ou entrega arquivos de backup.** Excluir um cadastro remove somente a linha informativa correspondente; não altera discos nem arquivos físicos.

## Modelo e planilha de origem

Frontend Next.js e API Spring Boot 3 / Java 21 com PostgreSQL e migrações Flyway. A planilha preservada em `data/Controle-Dos-Backups-Copia-b86353.xlsx` e seu snapshot versionado em `public/data/legacy-workbook.json` foram consultados antes da definição do formulário.

- `DADOS` contém agregados por rótulo de usuário/setor (`usuario_setor`), data textual, teste/status e tamanho/unidade. O formulário mantém esses dados como informados e os associa ao volume correspondente.
- `RESUMO` fornece os metadados de volume usados na Visão Geral. Indicadores não são medição física.
- `Planilha1` e `CONFERIR` permanecem preservadas no snapshot; referências sem vínculo inequívoco não foram atribuídas a usuários.
- A migração V6 cria `backup_record` sem modificar tabelas ou dados anteriores. Os cadastros novos guardam usuário/setor, volume, data textual, status, tamanho/unidade e autoria/data técnica. Registros legados e itens gerenciados existentes continuam preservados; os itens legados não são exibidos como arquivos.

## Executar

1. Configure PostgreSQL via `DATABASE_URL` (ou variáveis equivalentes suportadas pelo projeto) e `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`. A senha inicial do administrador precisa ter ao menos 16 caracteres. Não versione credenciais.
2. Inicie a API em `backend` com `mvn spring-boot:run`. Flyway aplica V6 ao iniciar. O seed inicial cria a conta ADMIN quando habilitado.
3. Configure `CORS_ORIGIN` para a origem do frontend (padrão `http://localhost:3000`) e inicie a interface com `pnpm install` e `pnpm dev`. Se a API não estiver em `http://localhost:8080`, defina `NEXT_PUBLIC_API_URL`.
4. Clique em **Entrar na API** e autentique com uma conta cadastrada. ADMIN e OPERATOR podem cadastrar, editar e excluir; READER pode consultar. As permissões são conferidas também pela API.

## Cadastrar e manter

Na Visão Geral, selecione um volume e use **Adicionar Backup**, ou abra **Backups** para cadastrar sem selecionar volume previamente. Preencha usuário/setor e volume; data textual, teste/status, tamanho e unidade são metadados opcionais. Em Backups, use os controles de edição ou exclusão. A exclusão pede confirmação e remove apenas o cadastro informativo. Inclusões, edições e exclusões registram ator autenticado, horário, ação e resultado no histórico.

O histórico mostra eventos operacionais guardados pela API. Busca e relatórios trabalham com os cadastros informativos e dados de volume. Não existe fluxo de upload ou download.

## API

Rotas de cadastro protegidas por HTTP Basic e papéis existentes:

- `GET /api/backups?volume={id}` — lista cadastros.
- `POST /api/backups` — cria cadastro.
- `PUT /api/backups/{id}` — edita cadastro.
- `DELETE /api/backups/{id}` — exclui cadastro informativo.
- `GET /api/history` — lista auditoria.
- `GET /api/volumes` e `GET /api/reports` — dados de volume.

Rotas de upload/download, gerenciamento de pastas e análises dependentes de binários foram removidas. V1–V5 não são reescritas; V6 é aditiva. Nenhum arquivo, linha legada ou dado de volume é apagado por esta mudança.

## Limitações

O frontend usa o snapshot versionado para a listagem de volumes; os cadastros e o histórico são lidos/escritos pela API. A interface precisa de uma API e um banco configurados e da autenticação válida para persistir alterações. Não há atribuição automática de cada agregado legado a um usuário além da informação textual originalmente registrada em `DADOS`.
