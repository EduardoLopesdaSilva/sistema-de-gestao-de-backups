# Gestão de informações de backup

O sistema cadastra informações sobre backups; **não recebe, armazena, navega ou entrega os arquivos de backup**. Excluir um cadastro remove somente a informação no banco e não altera discos ou arquivos físicos.

## Modelo e planilha

Frontend Next.js e API Spring Boot 3 / Java 21, PostgreSQL e migrações Flyway. Foram consultados `data/Controle-Dos-Backups-Copia-b86353.xlsx` e `public/data/legacy-workbook.json` antes de definir os campos.

- `DADOS` contém rótulo de usuário/setor (`usuario_setor`), data registrada, teste/status e tamanho/unidade vinculados ao volume. A data é armazenada como DATE; status e unidade usam os valores padronizados.
- `RESUMO` alimenta os indicadores por volume; os valores não são medições físicas.
- `Planilha1` acrescenta 191 identificadores informativos vinculados aos volumes indicados pelas seções, preservando tamanhos, células originais e metadados da seção. Identificadores `Id-*` não são tratados como nomes de pessoas.
- `CONFERIR` acrescenta 7 cadastros relacionados aos volumes de destino, preservando referência antiga, referência nova, data ou observação e células originais.
- A importação mantém os 181 registros de `DADOS`, corrige a associação das linhas marcadas `BADBLOCK NÃO FUNCIONA` ao volume BKP IMAGEM 12 e preserva esse rótulo como dado original. No total, são 379 registros informativos de backup além dos 26 volumes de `RESUMO`.
- V6 cria os cadastros informativos e V8 adiciona metadados de origem em JSONB. O importador atualizado é idempotente, reconhece registros já existentes por aba e linha e preenche a associação/origem sem substituir os valores editáveis que já tenham sido alterados.
- V1 é uma migração Flyway histórica e inclui a antiga tabela `app_user`. V7 remove essa tabela do banco. As migrações anteriores não são reescritas para evitar incompatibilidade de checksum.

## Executar

1. Configure PostgreSQL por `DATABASE_URL` ou variáveis equivalentes suportadas pelo projeto.
2. Inicie a API em `backend` com `mvn clean spring-boot:run`; Flyway aplica as migrações pendentes. O seed cria o usuário inicial `ti3` caso ainda não exista.
3. Configure `CORS_ORIGIN` para a origem do frontend (padrão `http://localhost:3000`) e execute `pnpm install` e `pnpm dev`. Se necessário, defina `NEXT_PUBLIC_API_URL`.

Todas as telas e rotas `/api/**` exigem uma sessão autenticada, exceto `POST /api/login` e `POST /api/logout`. O backend valida a origem de requisições que alteram dados e limita tentativas de login. O cookie de sessão é `HttpOnly`, `SameSite=Strict`, expira após 30 minutos de inatividade e deve usar `SESSION_COOKIE_SECURE=true` em HTTPS de produção.

## E-mails e Acessos

O módulo tem oito categorias de CRUD: E-mails Principais (abas `E-mail` e `Inativos`), Office 365, Skype, Redirecionamentos, Gerentes, Contas Gmail/Outlook, Celulares/Tablets e Lavanderia. As duas planilhas de encaminhamentos são normalizadas para DE → PARA e pares duplicados são consolidados. Os dados em células coloridas da planilha Cpanel viram tags especiais. Linhas vazias são ignoradas.

As oito planilhas não são copiadas para o repositório. Na tela **E-mails e Acessos**, use **Importar planilhas** e selecione os oito arquivos `.xlsx` de uma só vez. O importador valida nomes e abas, registra um hash por arquivo e não repete um arquivo já importado. Os conteúdos ficam criptografados com AES-256-GCM antes de serem salvos; a chave de criptografia não é armazenada no banco. Todas as rotas da API, inclusive as deste módulo, exigem a sessão autenticada global.

Os dados das credenciais são criptografados com AES-256-GCM antes de serem salvos. Configure uma chave de 32 bytes em Base64 no ambiente do backend e preserve-a entre reinicializações; perder essa chave impede descriptografar os registros:

```powershell
$encryptionKey = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
[Environment]::SetEnvironmentVariable('EMAIL_ACCESS_ENCRYPTION_KEY', $encryptionKey, 'User')
$env:EMAIL_ACCESS_ENCRYPTION_KEY = $encryptionKey
```

O seed cria `ti3` com a senha inicial `ease.123#`, armazenada com BCrypt. Troque-a em **Alterar Senha** após entrar. `AUTH_INITIAL_PASSWORD` pode substituir a senha inicial somente antes do primeiro seed; o seed não redefine contas existentes. Em produção, defina `SESSION_COOKIE_SECURE=true` e forneça `EMAIL_ACCESS_ENCRYPTION_KEY` por um gerenciador de segredos.

As tabelas mascaram senhas e oferecem ações para revelar ou copiar. O filtro de busca exclui campos de senha. As operações de CRUD e importação não registram valores de credenciais no histórico.

## Cadastro e manutenção

Na Visão Geral, selecione um volume para abrir seus detalhes ou use a tela **Backups**. **Adicionar Backup** solicita identificação/usuário/setor e volume; data registrada (DATE), teste/status, tamanho e unidade são opcionais; status aceita CONFERIDO, PENDENTE ou COM ERRO e unidade aceita KB, MB, GB ou TB. Os metadados originais importados podem ser consultados em “Ver metadados originais”. Os cadastros podem ser consultados, buscados, editados e excluídos. A exclusão pede confirmação e remove apenas o cadastro informativo.

O histórico registra data, ação e resultado; operações de backup usam o usuário autenticado como ator. Os registros legados importados identificam a origem da planilha. A busca e os relatórios trabalham com cadastros informativos, não com arquivos físicos.

## API

- `GET /api/backups?volume={id}` — lista cadastros.
- `POST /api/backups` — cria cadastro.
- `PUT /api/backups/{id}` — edita cadastro.
- `DELETE /api/backups/{id}` — exclui cadastro informativo.
- `GET /api/history` — lista auditoria.
- `GET /api/volumes`, `GET /api/search` e `GET /api/reports` — dados e busca de cadastros/volumes.

As rotas antigas de upload/download, pastas e análise de binários estão removidas ou bloqueadas. Nenhuma operação de cadastro apaga conteúdo de discos ou arquivos.
