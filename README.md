# Gestão de informações de backup

O sistema cadastra informações sobre backups; **não recebe, armazena, navega ou entrega os arquivos de backup**. Excluir um cadastro remove somente a informação no banco e não altera discos ou arquivos físicos.

## Modelo e planilha

Frontend Next.js e API Spring Boot 3 / Java 21, PostgreSQL e migrações Flyway. Foram consultados `data/Controle-Dos-Backups-Copia-b86353.xlsx` e `public/data/legacy-workbook.json` antes de definir os campos.

- `DADOS` contém rótulo de usuário/setor (`usuario_setor`), data textual, teste/status e tamanho/unidade. Esses metadados permanecem textuais, como informados, vinculados ao volume correspondente.
- `RESUMO` alimenta os indicadores por volume; os valores não são medições físicas.
- `Planilha1` e `CONFERIR` permanecem preservadas no snapshot; referências sem vínculo inequívoco não foram atribuídas a pessoas.
- V6 adiciona cadastros informativos sem alterar registros anteriores. A importação legada preserva origem e linha da planilha.
- V1 é uma migração Flyway histórica e inclui a antiga tabela `app_user`. V7 remove essa tabela do banco. As migrações anteriores não são reescritas para evitar incompatibilidade de checksum.

## Executar

1. Configure PostgreSQL por `DATABASE_URL` ou variáveis equivalentes suportadas pelo projeto.
2. Inicie a API em `backend` com `mvn clean spring-boot:run`; Flyway aplica migrações pendentes, inclusive V7. O `clean` evita reutilizar classes antigas de conta em `target`.
3. Configure `CORS_ORIGIN` para a origem do frontend (padrão `http://localhost:3000`) e execute `pnpm install` e `pnpm dev`. Se necessário, defina `NEXT_PUBLIC_API_URL`.

Não há tela de login, usuário padrão, senha, autenticação HTTP Basic ou papéis. A API permite as operações diretamente. Qualquer pessoa ou serviço que consiga alcançar a API pode consultar, incluir, editar e excluir cadastros e consultar o histórico; CORS restringe navegadores pela origem, mas não é autenticação.

## Cadastro e manutenção

Na Visão Geral, selecione um volume para abrir seus detalhes ou use a tela **Backups**. **Adicionar Backup** solicita usuário/setor e volume; data textual, teste/status, tamanho e unidade são opcionais. Os cadastros podem ser consultados, buscados, editados e excluídos. A exclusão pede confirmação e remove apenas o cadastro informativo.

O histórico registra data, ação, resultado e o ator `acesso-direto` para operações feitas sem autenticação. Os registros legados importados identificam a origem da planilha. A busca e os relatórios trabalham com cadastros informativos, não com arquivos físicos.

## API

- `GET /api/backups?volume={id}` — lista cadastros.
- `POST /api/backups` — cria cadastro.
- `PUT /api/backups/{id}` — edita cadastro.
- `DELETE /api/backups/{id}` — exclui cadastro informativo.
- `GET /api/history` — lista auditoria.
- `GET /api/volumes`, `GET /api/search` e `GET /api/reports` — dados e busca de cadastros/volumes.

As rotas antigas de gestão de usuários, upload/download, pastas e análise de binários estão removidas ou bloqueadas. Nenhuma operação de cadastro apaga conteúdo de discos ou arquivos.
