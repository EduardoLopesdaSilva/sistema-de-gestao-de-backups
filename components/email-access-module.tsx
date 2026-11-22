'use client'

import { useEffect, useMemo, useRef, useState, type FormEvent, type ChangeEvent } from 'react'
import { Check, Copy, Eye, EyeOff, FileSpreadsheet, LockKeyhole, Pencil, Plus, Search, ShieldCheck, Trash2, X } from 'lucide-react'

type Category = 'EMAIL_MAIN' | 'OFFICE365' | 'SKYPE' | 'REDIRECTS' | 'MANAGERS' | 'EXTERNAL_ACCOUNTS' | 'DEVICES' | 'LAUNDRY'
type Field = { key: string; label: string; secret?: boolean; required?: boolean; kind?: 'active' | 'tag' }
type Entry = { id: string; category: Category; fields: Record<string, string>; createdAt?: string }

const categories: { key: Category; label: string; description: string; fields: Field[] }[] = [
  { key: 'EMAIL_MAIN', label: 'E-mails Principais', description: 'Cpanel e contas gerais, incluindo os blocos ativos e inativos.', fields: [
    { key: 'email', label: 'E-mail', required: true }, { key: 'password', label: 'Senha', secret: true },
    { key: 'systems', label: 'Integração sistemas' }, { key: 'active', label: 'Ativo', kind: 'active', required: true },
    { key: 'specialTag', label: 'Status especial', kind: 'tag' },
  ] },
  { key: 'OFFICE365', label: 'Office 365 / Setores', description: 'Contas Microsoft vinculadas aos setores e usuários anteriores.', fields: [
    { key: 'email', label: 'E-mail', required: true }, { key: 'password', label: 'Senha', secret: true },
    { key: 'sector', label: 'Setor' }, { key: 'previousUser', label: 'Usuário Anterior' },
  ] },
  { key: 'SKYPE', label: 'Skype', description: 'Usuários, contas Skype e e-mails de recuperação.', fields: [
    { key: 'user', label: 'Usuário', required: true }, { key: 'skypeEmail', label: 'E-mail Skype' },
    { key: 'password', label: 'Senha', secret: true }, { key: 'recoveryEmail', label: 'E-mail de Recuperação' },
  ] },
  { key: 'REDIRECTS', label: 'Redirecionamentos', description: 'Relações DE → PARA e encaminhamentos consolidados sem pares duplicados.', fields: [
    { key: 'sourceEmail', label: 'E-mail Origem (DE)', required: true }, { key: 'targetEmail', label: 'E-mail Destino (PARA)', required: true },
  ] },
  { key: 'MANAGERS', label: 'Gerentes de Setor', description: 'Responsáveis e e-mails por setor.', fields: [
    { key: 'fullName', label: 'Nome Completo' }, { key: 'managerSector', label: 'Gerente do Setor' }, { key: 'email', label: 'E-mail', required: true },
  ] },
  { key: 'EXTERNAL_ACCOUNTS', label: 'Contas Gmail / Outlook', description: 'Contas de serviços externos e senha Samsung associada.', fields: [
    { key: 'description', label: 'Descrição' }, { key: 'email', label: 'E-mail', required: true },
    { key: 'password', label: 'Senha', secret: true }, { key: 'samsungPassword', label: 'Senha Samsung', secret: true },
  ] },
  { key: 'DEVICES', label: 'Celulares / Tablets', description: 'Contas e credenciais dos dispositivos por setor.', fields: [
    { key: 'deviceSector', label: 'Celular/Setor', required: true }, { key: 'emailUsed', label: 'E-mail Usado' },
    { key: 'gmailPassword', label: 'Senha Gmail', secret: true }, { key: 'samsungPassword', label: 'Senha Samsung', secret: true },
    { key: 'pin', label: 'PIN', secret: true }, { key: 'user', label: 'Usuário' },
  ] },
  { key: 'LAUNDRY', label: 'Acessos Lavanderia', description: 'Contas Cpanel e Office 365 da lavanderia.', fields: [
    { key: 'user', label: 'Usuário', required: true }, { key: 'cpanel', label: 'Cpanel' }, { key: 'office365', label: 'Office 365' },
    { key: 'cpanelPassword', label: 'Senha Cpanel', secret: true }, { key: 'office365Password', label: 'Senha 365', secret: true }, { key: 'sector', label: 'Setor' },
  ] },
]
const api = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
const TAGS = ['Inativar', 'Incluir', 'Não Mexer', 'Filial']
const blank = (fields: Field[]) => Object.fromEntries(fields.map(field => [field.key, field.key === 'active' ? 'Sim' : ''])) as Record<string, string>

export function EmailAccessModule() {
  const [tokenInput, setTokenInput] = useState('')
  const [token, setToken] = useState('')
  const [authorized, setAuthorized] = useState(false)
  const [category, setCategory] = useState<Category>('EMAIL_MAIN')
  const [records, setRecords] = useState<Entry[]>([])
  const [query, setQuery] = useState('')
  const [dialog, setDialog] = useState(false)
  const [editing, setEditing] = useState<Entry | null>(null)
  const [form, setForm] = useState<Record<string, string>>({})
  const [visible, setVisible] = useState<Record<string, boolean>>({})
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const current = categories.find(item => item.key === category)!

  async function request(path: string, init: RequestInit = {}, accessToken = token) {
    const isForm = typeof FormData !== 'undefined' && init.body instanceof FormData
    const headers = new Headers(init.headers)
    headers.set('X-Email-Access-Token', accessToken)
    if (!isForm && init.body) headers.set('Content-Type', 'application/json')
    const response = await fetch(`${api}${path}`, { ...init, headers })
    if (!response.ok) {
      if (response.status === 401) setAuthorized(false)
      if (response.status === 503) throw new Error('O backend precisa de EMAIL_ACCESS_TOKEN e EMAIL_ACCESS_ENCRYPTION_KEY configurados.')
      throw new Error(response.status === 401 ? 'Chave de acesso incorreta.' : `Falha na API (${response.status}).`)
    }
    return response.status === 204 ? null : response.json()
  }

  async function refresh(nextCategory = category, accessToken = token) {
    if (!accessToken) return
    try {
      const result = await request(`/api/email-access?category=${encodeURIComponent(nextCategory)}`, {}, accessToken)
      setRecords(result)
      setAuthorized(true)
      setError('')
    } catch (cause) {
      setRecords([])
      setError(cause instanceof Error ? cause.message : 'Não foi possível abrir o módulo.')
    }
  }

  useEffect(() => { if (authorized) void refresh(category) }, [authorized, category])

  const filtered = useMemo(() => {
    const text = query.trim().toLocaleLowerCase('pt-BR')
    if (!text) return records
    const searchable = current.fields.filter(field => !field.secret)
    return records.filter(record => searchable.some(field => (record.fields[field.key] || '').toLocaleLowerCase('pt-BR').includes(text)))
  }, [records, query, current])
  const activeCount = records.filter(record => record.fields.active === 'Sim').length
  const inactiveCount = records.filter(record => record.fields.active === 'Não').length
  const taggedCount = records.filter(record => record.fields.specialTag).length
  const secretFields = current.fields.filter(field => field.secret)
  const credentialCount = records.filter(record => secretFields.some(field => record.fields[field.key])).length
  const contactCount = records.filter(record => Object.entries(record.fields).some(([key, value]) => /email|cpanel|office365/i.test(key) && Boolean(value))).length

  function unlock(event: FormEvent) {
    event.preventDefault()
    setError('')
    setToken(tokenInput)
    void refresh(category, tokenInput)
  }
  function lockModule() { setAuthorized(false); setToken(''); setTokenInput(''); setRecords([]); setVisible({}); setQuery('') }
  function startCreate() { setEditing(null); setForm(blank(current.fields)); setDialog(true); setError('') }
  function startEdit(record: Entry) { setEditing(record); setForm({ ...blank(current.fields), ...record.fields }); setDialog(true); setError('') }

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      const path = editing ? `/api/email-access/${editing.id}` : `/api/email-access/${category}`
      await request(path, { method: editing ? 'PUT' : 'POST', body: JSON.stringify(form) })
      setDialog(false)
      setNotice(editing ? 'Registro atualizado.' : 'Registro adicionado.')
      await refresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível salvar o registro.') }
    finally { setBusy(false) }
  }

  async function remove(record: Entry) {
    if (!window.confirm(`Remover o registro “${record.fields.email || record.fields.user || record.fields.deviceSector || record.fields.sourceEmail || current.label}”?`)) return
    try { await request(`/api/email-access/${record.id}`, { method: 'DELETE' }); setNotice('Registro removido.'); await refresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível remover o registro.') }
  }

  async function importFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files || [])
    event.target.value = ''
    if (!files.length) return
    const payload = new FormData()
    files.forEach(file => payload.append('files', file))
    setBusy(true)
    setError('')
    try {
      const result = await request('/api/email-access/import', { method: 'POST', body: payload })
      setNotice(`${result.imported} registros importados. Planilhas já processadas foram ignoradas.`)
      await refresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível importar as planilhas.') }
    finally { setBusy(false) }
  }

  function toggleSecret(id: string, key: string) { const identity = `${id}:${key}`; setVisible(state => ({ ...state, [identity]: !state[identity] })) }
  async function copySecret(value: string) {
    try { await navigator.clipboard.writeText(value); setNotice('Senha copiada para a área de transferência.') }
    catch { setError('O navegador não permitiu copiar para a área de transferência.') }
  }

  if (!authorized) return <section className="panel access-lock-panel">
    <div className="module-title"><span className="module-icon"><LockKeyhole size={20}/></span><div><h2>E-mails e Acessos</h2><p>Informe a chave de acesso configurada no backend para abrir as credenciais.</p></div></div>
    <form className="backup-form access-unlock" onSubmit={unlock}>
      <label>Chave de acesso<input type="password" autoComplete="current-password" value={tokenInput} onChange={event => setTokenInput(event.target.value)} required/></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="button primary" disabled={busy || !tokenInput}>Abrir módulo</button>
      <p className="security-note"><ShieldCheck size={15}/> Os dados são criptografados no banco. Configure <code>EMAIL_ACCESS_TOKEN</code> e <code>EMAIL_ACCESS_ENCRYPTION_KEY</code> no backend.</p>
    </form>
  </section>

  return <>
    <div className="page-heading email-page-heading"><div><div className="eyebrow"><span className="eyebrow-dot"/>GERENCIAMENTO DE CONTAS</div><h1>E-mails e Acessos</h1><p>Credenciais protegidas, com visualização mascarada e controle de acesso.</p></div><div className="heading-actions"><input ref={fileRef} className="visually-hidden" type="file" accept=".xlsx" multiple onChange={importFiles}/><button className="button secondary" onClick={() => fileRef.current?.click()} disabled={busy}><FileSpreadsheet size={15}/> Importar planilhas</button><button className="button secondary" onClick={lockModule}><LockKeyhole size={14}/> Bloquear</button><button className="button primary" onClick={startCreate}><Plus size={16}/> Adicionar</button></div></div>
    {notice && <div className="notice" role="status"><span>{notice}</span><button aria-label="Fechar aviso" onClick={() => setNotice('')}><X size={16}/></button></div>}
    {error && <div className="notice module-error" role="alert"><span>{error}</span><button aria-label="Fechar aviso" onClick={() => setError('')}><X size={16}/></button></div>}
    <div className="email-summary stats-grid">
      <Summary label="Registros nesta categoria" value={String(records.length)} note={current.label}/>
      <Summary label={category === 'EMAIL_MAIN' ? 'Ativos' : 'Com credenciais'} value={String(category === 'EMAIL_MAIN' ? activeCount : credentialCount)} note={category === 'EMAIL_MAIN' ? 'Contas marcadas como Sim' : 'Registros com senha cadastrada'}/>
      <Summary label={category === 'EMAIL_MAIN' ? 'Inativos' : 'Com e-mail ou conta'} value={String(category === 'EMAIL_MAIN' ? inactiveCount : contactCount)} note={category === 'EMAIL_MAIN' ? 'Contas marcadas como Não' : 'Registros com contato preenchido'}/>
      <Summary label={category === 'EMAIL_MAIN' ? 'Com status especial' : 'Correspondem à busca'} value={String(category === 'EMAIL_MAIN' ? taggedCount : filtered.length)} note={category === 'EMAIL_MAIN' ? 'Tags preservadas da planilha' : 'Resultado do filtro atual'}/>
    </div>
    <section className="panel email-panel">
      <div className="email-tabs" role="tablist" aria-label="Categorias de e-mails e acessos">
        {categories.map(item => <button key={item.key} role="tab" aria-selected={category === item.key} className={`email-tab ${category === item.key ? 'active' : ''}`} onClick={() => { setCategory(item.key); setQuery(''); setNotice('') }}><span>{item.label}</span><small>{item.key === category ? records.length : ''}</small></button>)}
      </div>
      <div className="panel-heading email-list-heading"><div><h2>{current.label}</h2><p>{current.description}</p></div><span className="secure-pill"><LockKeyhole size={13}/> Protegido</span></div>
      <div className="search-row"><div className="search-box"><Search size={16}/><input aria-label="Buscar registros" placeholder="Buscar por e-mail, usuário, setor ou descrição..." value={query} onChange={event => setQuery(event.target.value)}/></div></div>
      <div className="table-wrap"><table className="email-table"><thead><tr>{current.fields.map(field => <th key={field.key}>{field.label}</th>)}<th>Ações</th></tr></thead><tbody>
        {filtered.map(record => <tr key={record.id}>{current.fields.map(field => <td key={field.key}>
          {field.secret ? <div className="secret-cell"><span className="secret-value">{visible[`${record.id}:${field.key}`] ? (record.fields[field.key] || '—') : record.fields[field.key] ? '••••••••' : '—'}</span>{Boolean(record.fields[field.key]) && <><button className="secret-action" title={visible[`${record.id}:${field.key}`] ? 'Ocultar senha' : 'Mostrar senha'} aria-label={visible[`${record.id}:${field.key}`] ? 'Ocultar senha' : 'Mostrar senha'} onClick={() => toggleSecret(record.id, field.key)}>{visible[`${record.id}:${field.key}`] ? <EyeOff size={14}/> : <Eye size={14}/>}</button><button className="secret-action" title="Copiar senha" aria-label="Copiar senha" onClick={() => void copySecret(record.fields[field.key])}><Copy size={13}/></button></>}</div>
            : field.kind === 'active' ? <span className={`status-pill ${record.fields.active === 'Sim' ? 'healthy' : 'warning'}`}><i/>{record.fields.active || '—'}</span>
            : field.kind === 'tag' ? record.fields.specialTag ? <span className="email-tag">{record.fields.specialTag}</span> : '—'
            : record.fields[field.key] || '—'}</td>)}<td><div className="row-actions"><button aria-label={`Editar registro ${record.id}`} title="Editar" onClick={() => startEdit(record)}><Pencil size={14}/></button><button aria-label={`Remover registro ${record.id}`} title="Remover" onClick={() => void remove(record)}><Trash2 size={14}/></button></div></td></tr>)}
        {!filtered.length && <tr><td colSpan={current.fields.length + 1} className="empty-cell">{records.length ? 'Nenhum registro corresponde à busca.' : 'Ainda não há registros nesta categoria. Importe as planilhas ou adicione um registro.'}</td></tr>}
      </tbody></table></div>
      <p className="table-note">Exibindo {filtered.length} de {records.length} registros. As credenciais ficam mascaradas até serem reveladas.</p>
    </section>

    {dialog && <div className="dialog-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setDialog(false) }}><section className="dialog email-dialog" role="dialog" aria-modal="true" aria-labelledby="email-dialog-title"><div className="dialog-heading"><div><h2 id="email-dialog-title">{editing ? 'Editar registro' : 'Adicionar registro'}</h2><p>{current.label}</p></div><button className="icon-button" aria-label="Fechar formulário" onClick={() => setDialog(false)}><X size={18}/></button></div><form className="backup-form" onSubmit={save}><div className="email-form-grid">
      {current.fields.map(field => <label key={field.key}>{field.label}{field.kind === 'active' ? <select name={field.key} required value={form[field.key] || ''} onChange={event => setForm(state => ({ ...state, [field.key]: event.target.value }))}><option value="">Selecione</option><option value="Sim">Sim</option><option value="Não">Não</option></select>
        : field.kind === 'tag' ? <select name={field.key} value={form[field.key] || ''} onChange={event => setForm(state => ({ ...state, [field.key]: event.target.value }))}><option value="">Sem status especial</option>{TAGS.map(tag => <option key={tag}>{tag}</option>)}</select>
        : <input name={field.key} type={field.secret ? 'password' : 'text'} autoComplete={field.secret ? 'new-password' : 'off'} required={Boolean(field.required)} value={form[field.key] || ''} onChange={event => setForm(state => ({ ...state, [field.key]: event.target.value }))}/>}</label>)}
      </div>{error && <p className="form-error" role="alert">{error}</p>}<div className="form-actions"><button type="button" className="button secondary" onClick={() => setDialog(false)}>Cancelar</button><button className="button primary" disabled={busy}>{busy ? 'Salvando…' : editing ? 'Salvar alterações' : 'Adicionar'}</button></div></form></section></div>}
  </>
}

function Summary({ label, value, note }: { label: string; value: string; note: string }) {
  return <article className="stat-card"><div className="stat-top"><span>{label}</span><span className="stat-icon"><Check size={16}/></span></div><strong>{value}</strong><small>{note}</small></article>
}
