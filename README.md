# Gestão de informações de backup

O sistema cadastra informações sobre backups; **não recebe, armazena, navega ou entrega os arquivos de backup**. Excluir um cadastro remove somente a informação no banco e não altera discos ou arquivos físicos.

## Modelo e planilha

Frontend Next.js e API Spring Boot 3 / Java 21, PostgreSQL e migrações Flyway. Foram consultados `data/Controle-Dos-Backups-Copia-b86353.xlsx` e `public/data/legacy-workbook.json` antes de definir os campos.

- `DADOS` contém rótulo de usuário/setor (`usuario_setor`), data textual, teste/status e tamanho/unidade. Esses metadados permanecem textuais, como informados, vinculados ao volume correspondente.
- `RESUMO` alimenta os indicadores por volume; os valores não são medições físicas.
- `Planilha1` acrescenta 191 identificadores informativos vinculados aos volumes indicados pelas seções, preservando tamanhos, células originais e metadados da seção. Identificadores `Id-*` não são tratados como nomes de pessoas.
- `CONFERIR` acrescenta 7 cadastros relacionados aos volumes de destino, preservando referência antiga, referência nova, data ou observação e células originais.
- A importação mantém os 181 registros de `DADOS`, corrige a associação das linhas marcadas `BADBLOCK NÃO FUNCIONA` ao volume BKP IMAGEM 12 e preserva esse rótulo como dado original. No total, são 379 registros informativos de backup além dos 26 volumes de `RESUMO`.
- V6 cria os cadastros informativos e V8 adiciona metadados de origem em JSONB. O importador atualizado é idempotente, reconhece registros já existentes por aba e linha e preenche a associação/origem sem substituir os valores editáveis que já tenham sido alterados.
- V1 é uma migração Flyway histórica e inclui a antiga tabela `app_user`. V7 remove essa tabela do banco. As migrações anteriores não são reescritas para evitar incompatibilidade de checksum.

## Executar

1. Configure PostgreSQL por `DATABASE_URL` ou variáveis equivalentes suportadas pelo projeto.
2. Inicie a API em `backend` com `mvn clean spring-boot:run`; Flyway aplica migrações pendentes, inclusive V7. O `clean` evita reutilizar classes antigas de conta em `target`.
3. Configure `CORS_ORIGIN` para a origem do frontend (padrão `http://localhost:3000`) e execute `pnpm install` e `pnpm dev`. Se necessário, defina `NEXT_PUBLIC_API_URL`.

O módulo de Backups não tem tela de login nem autenticação; qualquer pessoa ou serviço que alcance essa API pode consultar e alterar seus cadastros. O módulo **E-mails e Acessos** usa uma chave compartilhada própria e criptografa seus dados no banco.

## E-mails e Acessos

O módulo tem oito categorias de CRUD: E-mails Principais (abas `E-mail` e `Inativos`), Office 365, Skype, Redirecionamentos, Gerentes, Contas Gmail/Outlook, Celulares/Tablets e Lavanderia. As duas planilhas de encaminhamentos são normalizadas para DE → PARA e pares duplicados são consolidados. Os dados em células coloridas da planilha Cpanel viram tags especiais. Linhas vazias são ignoradas.

As oito planilhas não são copiadas para o repositório. Na tela **E-mails e Acessos**, use **Importar planilhas** e selecione os oito arquivos `.xlsx` de uma só vez. O importador valida nomes e abas, registra um hash por arquivo e não repete um arquivo já importado. Os conteúdos ficam criptografados com AES-256-GCM antes de serem salvos; a chave de criptografia não é armazenada no banco.

Configure duas variáveis secretas no ambiente do backend antes de iniciar a API. Para desenvolvimento local, gere os valores uma única vez e mantenha-os no perfil Windows do usuário (ou em um gerenciador de segredos):

```powershell
$token = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$encryptionKey = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
[Environment]::SetEnvironmentVariable('EMAIL_ACCESS_TOKEN', $token, 'User')
[Environment]::SetEnvironmentVariable('EMAIL_ACCESS_ENCRYPTION_KEY', $encryptionKey, 'User')
$env:EMAIL_ACCESS_TOKEN = $token
$env:EMAIL_ACCESS_ENCRYPTION_KEY = $encryptionKey
```

Em novos terminais, carregue os mesmos valores com `$env:EMAIL_ACCESS_TOKEN = [Environment]::GetEnvironmentVariable('EMAIL_ACCESS_TOKEN', 'User')` e `$env:EMAIL_ACCESS_ENCRYPTION_KEY = [Environment]::GetEnvironmentVariable('EMAIL_ACCESS_ENCRYPTION_KEY', 'User')`. Mantenha a chave de criptografia em um gerenciador seguro e não gere outra depois de importar dados: perdê-la impede a descriptografia dos registros. Inicie a API no mesmo terminal e cole o valor de `$env:EMAIL_ACCESS_TOKEN` no painel de desbloqueio. O token é mantido apenas na memória da página. A API rejeita chamadas ao módulo sem o token e permanece fechada se o token ou a chave de criptografia não estiverem configurados corretamente.

As tabelas mascaram senhas e oferecem ações para revelar ou copiar. O filtro de busca exclui campos de senha. As operações de CRUD e importação não registram valores de credenciais no histórico.

## Cadastro e manutenção

Na Visão Geral, selecione um volume para abrir seus detalhes ou use a tela **Backups**. **Adicionar Backup** solicita identificação/usuário/setor e volume; data textual, teste/status, tamanho e unidade são opcionais. Os metadados originais importados podem ser consultados em “Ver metadados originais”. Os cadastros podem ser consultados, buscados, editados e excluídos. A exclusão pede confirmação e remove apenas o cadastro informativo.

O histórico registra data, ação, resultado e o ator `acesso-direto` para operações feitas sem autenticação. Os registros legados importados identificam a origem da planilha. A busca e os relatórios trabalham com cadastros informativos, não com arquivos físicos.

## API

- `GET /api/backups?volume={id}` — lista cadastros.
- `POST /api/backups` — cria cadastro.
- `PUT /api/backups/{id}` — edita cadastro.
- `DELETE /api/backups/{id}` — exclui cadastro informativo.
- `GET /api/history` — lista auditoria.
- `GET /api/volumes`, `GET /api/search` e `GET /api/reports` — dados e busca de cadastros/volumes.

As rotas antigas de gestão de usuários, upload/download, pastas e análise de binários estão removidas ou bloqueadas. Nenhuma operação de cadastro apaga conteúdo de discos ou arquivos.
