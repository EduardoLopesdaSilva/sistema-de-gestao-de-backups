# Gestão de informações de backup

O sistema cadastra informações sobre backups; **não recebe, armazena, navega ou entrega os arquivos de backup**. Excluir um cadastro remove somente a informação no banco e não altera discos ou arquivos físicos.

## Modelo e planilha

Frontend Next.js e API Spring Boot 3 / Java 21, MySQL 8 e schema SQL manual para MySQL Workbench. Foram consultados `data/Controle-Dos-Backups-Copia-b86353.xlsx` e `public/data/legacy-workbook.json` antes de definir os campos.

- `DADOS` contém rótulo de usuário/setor (`usuario_setor`), data registrada, teste/status e tamanho/unidade vinculados ao volume. A data é armazenada como DATE; status e unidade usam os valores padronizados.
- `RESUMO` alimenta os indicadores por volume; os valores não são medições físicas.
- `Planilha1` acrescenta 191 identificadores informativos vinculados aos volumes indicados pelas seções, preservando tamanhos, células originais e metadados da seção. Identificadores `Id-*` não são tratados como nomes de pessoas.
- `CONFERIR` acrescenta 7 cadastros relacionados aos volumes de destino, preservando referência antiga, referência nova, data ou observação e células originais.
- `backend/sql/mysql/02_seed_workbook_data.sql` contém 26 volumes e 379 registros informativos: 181 de `DADOS`, 191 de `Planilha1` e 7 de `CONFERIR`. As linhas `BADBLOCK NÃO FUNCIONA` são associadas ao BKP IMAGEM 12 e seu rótulo original fica preservado nos metadados.
- `backend/sql/mysql/01_schema.sql` é o schema MySQL executável no Workbench. O backend não roda migrações automáticas; o Hibernate apenas valida o schema configurado (`ddl-auto: validate`). O schema inclui armazenamento de hashes CHAR(64), datas, JSON e relacionamentos em tipos MySQL.
- As versões Flyway/PostgreSQL foram removidas do classpath e do código-fonte para não serem executadas contra MySQL. O script só cria tabelas/índices ausentes e não contém `DROP TABLE`; não apaga dados existentes.

## Executar

1. No MySQL Workbench, conecte-se ao servidor MySQL 8 e execute `backend/sql/mysql/01_schema.sql`, seguido por `backend/sql/mysql/02_seed_workbook_data.sql`.
2. Confirme que o servidor está acessível antes de iniciar a API. Neste ambiente local, o MySQL está configurado na porta `3307`; verifique com `Test-NetConnection localhost -Port 3307` e confirme `TcpTestSucceeded: True`. O Workbench sozinho não inicia o servidor. Se aparecer `Connection refused`, inicie o serviço MySQL no Windows ou corrija host/porta até o teste passar.
3. Na mesma janela do PowerShell que iniciará a API, configure o endereço e credenciais (substitua usuário e senha pelos usados no Workbench):

   ```powershell
   $env:DATABASE_URL = "jdbc:mysql://localhost:3307/ease_backup_control"
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = "SUA_SENHA_DO_MYSQL"
   ```

   Para um banco novo, configure também `AUTH_INITIAL_PASSWORD` com pelo menos 16 caracteres para criar a conta inicial. Não grave senhas no repositório.
4. Inicie a API na pasta `backend` com `mvn spring-boot:run`. O backend exige Java 21 e Maven, valida o schema e não executa migrações automáticas. O checksum inserido pelo script SQL impede que o importador repita a carga da planilha.
5. Em outro PowerShell, na raiz do projeto, execute `pnpm install` e `pnpm dev`. Acesse `http://localhost:3000`. O endereço da API padrão é `http://localhost:8080`; se mudar portas/origens, ajuste `NEXT_PUBLIC_API_URL` e `CORS_ORIGIN`.

O desenvolvimento e o build usam Webpack para evitar a falha de processo auxiliar observada com Turbopack. O build de produção usa dois workers para limitar concorrência; a heap máxima é 4 GB, como teto e não como memória reservada. Execute `pnpm build` para compilar e `pnpm start` para servir o build.

O MySQL Workbench é o cliente usado para executar SQL; ele não hospeda o banco. Para Vercel, configure um servidor MySQL acessível pela API e forneça a URL/credenciais no ambiente do backend. Os dois scripts criam o schema e inserem os dados da planilha sem sobrescrever registros existentes; eles não convertem dados adicionais de uma instância antiga. Exporte esses dados em CSV e importe-os no MySQL antes de apontar a API para esse destino. Não há `DROP TABLE` nem exclusão de dados nos scripts.

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

Se a tabela `auth_user` estiver vazia, o backend cria a conta inicial `ti3` apenas se `AUTH_INITIAL_PASSWORD` estiver definida com pelo menos 16 caracteres; a senha é armazenada com BCrypt. Se já houver qualquer conta, o backend inicia sem exigir essa variável e não altera usuários existentes. Em produção, defina `SESSION_COOKIE_SECURE=true` e forneça `EMAIL_ACCESS_ENCRYPTION_KEY` por um gerenciador de segredos.

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
