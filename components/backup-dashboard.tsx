'use client'

import { useMemo, useState } from 'react'
import legacy from '@/public/data/legacy-workbook.json'
import { Activity, AlertTriangle, ArrowDownToLine, ArrowLeftRight, Bell, CheckCircle2, ChevronRight, CircleHelp, Database, File, FileSearch, Folder, HardDrive, LayoutDashboard, ListFilter, Menu, MoreHorizontal, Search, ShieldCheck, SlidersHorizontal, Upload, X } from 'lucide-react'

type Volume = (typeof legacy.volumes)[number]
type LegacyEntry = (typeof legacy.legacyItems)[number]
type Tab = 'Visão geral' | 'Volumes' | 'Arquivos' | 'Histórico' | 'Relatórios' | 'Análise inteligente'

const nav: { label: Tab; icon: typeof LayoutDashboard }[] = [
  { label: 'Visão geral', icon: LayoutDashboard }, { label: 'Volumes', icon: HardDrive }, { label: 'Arquivos', icon: FileSearch }, { label: 'Histórico', icon: Activity }, { label: 'Relatórios', icon: ListFilter }, { label: 'Análise inteligente', icon: ShieldCheck },
]
const format = (n: number) => new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(n)
const percent = (v: Volume) => v.capacityGb > 0 ? Math.max(0, v.occupiedGb / v.capacityGb * 100) : 0
const level = (v: Volume) => v.freeGb < 20 ? 'critical' : v.freeGb <= 100 ? 'warning' : 'healthy'
const exportCsv = (volumes: Volume[]) => {
  const quote = (value: string | number) => `"${String(value).replaceAll('"', '""')}"`
  const rows = [['Volume', 'Ocupado (GB)', 'Capacidade informada (GB)', 'Saldo informado (GB)', 'Observação', 'Origem'], ...volumes.map(v => [v.name, v.occupiedGb, v.capacityGb, v.freeGb, v.note, 'RESUMO'])]
  const csv = `\uFEFF${rows.map(row => row.map(quote).join(';')).join('\r\n')}`
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = 'relatorio-volumes-importados.csv'; link.click(); URL.revokeObjectURL(url)
}

function VolumeCard({ volume, onClick }: { volume: Volume; onClick: () => void }) {
  const state = level(volume)
  return <button className={`volume-card ${state}`} onClick={onClick} aria-label={`Abrir ${volume.name}: ${format(volume.freeGb)} GB livres`}>
    <div className="volume-top"><span className="drive-icon"><HardDrive size={17}/></span><span className={`status-pill ${state}`}><i />{state === 'critical' ? 'Crítico' : state === 'warning' ? 'Atenção' : 'Saudável'}</span><MoreHorizontal className="more" size={18}/></div>
    <strong className="volume-name">{volume.name}</strong>
    <div className="capacity"><span>{format(volume.occupiedGb)} GB <small>ocupados</small></span><span>de {format(volume.capacityGb)} GB</span></div>
    <div className="meter"><span style={{ width: `${percent(volume)}%` }} /></div>
    <div className="volume-bottom"><span><b>{format(volume.freeGb)} GB</b> livres (saldo importado)</span><span>{format(percent(volume))}%</span></div>
    <div className="volume-foot"><span>Origem: planilha · RESUMO</span><span>10 nov 2026</span></div>
  </button>
}

export function BackupDashboard() {
  const [active, setActive] = useState<Tab>('Visão geral')
  const [selected, setSelected] = useState<Volume | null>(null)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('Todos os volumes')
  const [criticalOnly, setCriticalOnly] = useState(false)
  const [mobileNav, setMobileNav] = useState(false)
  const volumes = legacy.volumes as Volume[]
  const entries = legacy.legacyItems as LegacyEntry[]
  const critical = volumes.filter(v => level(v) === 'critical').length
  const shownVolumes = criticalOnly ? volumes.filter(v => level(v) === 'critical') : volumes
  const sorted = [...volumes].sort((a,b) => b.occupiedGb - a.occupiedGb)
  const matched = useMemo(() => entries.filter(e => (!search || `${e.owner} ${e.volume} ${e.status}`.toLocaleLowerCase('pt-BR').includes(search.toLocaleLowerCase('pt-BR'))) && (filter === 'Todos os volumes' || e.volume === filter)), [entries, search, filter])
  const latestEvents = [
    { icon: CheckCircle2, title: 'Importação de dados legados', detail: '26 volumes e 181 registros agregados · origem: DADOS / RESUMO', time: '10 nov 2026', type: 'success' },
    { icon: AlertTriangle, title: 'BKP IMAGEM 12 requer atenção', detail: 'Saldo informado de −20 GB e observação BADBLOCK NÃO FUNCIONA', time: '10 nov 2026', type: 'warning' },
    { icon: Database, title: 'Capacidades importadas', detail: 'Capacidades preservadas conforme informadas na planilha', time: '10 nov 2026', type: 'neutral' },
  ]
  const showTab = (tab: Tab) => { setActive(tab); setSelected(null); setMobileNav(false) }
  return <div className="app-shell">
    <aside className={`sidebar ${mobileNav ? 'open' : ''}`}>
      <div className="brand"><span className="brand-mark"><Database size={19}/></span><span><b>Vaultline</b><small>BACKUP CONTROL</small></span><button className="mobile-close" onClick={() => setMobileNav(false)} aria-label="Fechar menu"><X/></button></div>
      <div className="workspace-label">ESPAÇO DE TRABALHO</div>
      <div className="workspace-switch"><span className="workspace-avatar"><Database size={14}/></span><span><b>Volumes importados</b><small>Origem: planilha</small></span></div>
      <div className="nav-label">MENU PRINCIPAL</div>
      <nav aria-label="Navegação principal">{nav.map(({label,icon:Icon}) => <button key={label} onClick={() => showTab(label)} className={`nav-item ${active === label ? 'active' : ''}`}><Icon size={17}/><span>{label}</span>{label === 'Análise inteligente' && <span className="nav-new">2</span>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="storage-summary"><div className="storage-title"><span>Armazenamento</span><span>{volumes.length} volumes</span></div><div className="stacked-bar"><i style={{width:'66%'}}/><i style={{width:'20%'}}/><i style={{width:'14%'}}/></div><p>Indicadores importados da planilha</p></div><div className="profile"><span><b>Dados de referência</b><small>Sem usuário autenticado nesta prévia</small></span></div></div>
    </aside>
    {mobileNav && <button className="mobile-scrim" onClick={() => setMobileNav(false)} aria-label="Fechar menu"/>}
    <main className="main-area">
      <header className="topbar"><button className="mobile-menu" onClick={() => setMobileNav(true)} aria-label="Abrir menu"><Menu/></button><div className="breadcrumbs"><span>Workspace</span><ChevronRight size={14}/><b>{selected?.name || active}</b></div><div className="top-actions"><span className="api-indicator"><i/>Snapshot importado</span><button className="icon-button" aria-label="Abrir histórico" onClick={() => showTab('Histórico')}><Bell size={18}/><i/></button><div className="top-avatar" aria-hidden="true"><Database size={14}/></div></div></header>
      <div className="content">
        {selected ? <><div className="page-heading"><div><button className="back-link" onClick={() => setSelected(null)}>← Voltar aos volumes</button><h1>{selected.name}</h1><p>Detalhes e registros agregados importados para este volume.</p></div><span className={`status-pill ${level(selected)}`}><i/>{level(selected) === 'critical' ? 'Crítico' : level(selected) === 'warning' ? 'Atenção' : 'Saudável'}</span></div><div className="detail-stats"><Stat label="Total ocupado" value={`${format(selected.occupiedGb)} GB`} icon={Database}/><Stat label="Capacidade informada" value={`${format(selected.capacityGb)} GB`} icon={HardDrive}/><Stat label="Saldo informado" value={`${format(selected.freeGb)} GB`} icon={Activity}/></div><section className="panel"><div className="panel-heading"><div><h2>Registros legados</h2><p>Totais agregados; não representam arquivos físicos navegáveis.</p></div></div><LegacyTable entries={entries.filter(e => e.volume === selected.name)} /></section></>
        : <>
        <div className="page-heading"><div><div className="eyebrow"><span className="eyebrow-dot"/>CENTRAL DE OPERAÇÕES</div><h1>{active === 'Visão geral' ? 'Visão geral' : active}</h1><p>Acompanhe a saúde dos backups e a distribuição do armazenamento.</p><span id="upload-hint" className="upload-hint">Uploads reais aguardam a API de armazenamento.</span></div><div className="heading-actions"><button className="button secondary" onClick={() => exportCsv(volumes)}><ArrowDownToLine size={16}/> Exportar relatório</button><button className="button primary upload-button" disabled aria-describedby="upload-hint" title="Upload aguarda a implementação do endpoint seguro de armazenamento"><Upload size={16}/> Novo upload</button></div></div>
        {active === 'Visão geral' || active === 'Volumes' ? <>
          <div className="stats-grid"><Stat label="Volumes monitorados" value={String(volumes.length)} note="Dados importados · RESUMO" icon={HardDrive}/><Stat label="Volumes críticos" value={String(critical)} note="Saldo abaixo de 20 GB" icon={AlertTriangle} tone="red"/><Stat label="Dados ocupados" value={`${format(volumes.reduce((sum,v)=>sum+v.occupiedGb,0))} GB`} note="Total informado na planilha" icon={Database}/><Stat label="Registros legados" value={String(entries.length)} note="Sem conteúdo binário associado" icon={File}/></div>
          <div className="section-heading"><div><h2>{active === 'Volumes' ? 'Todos os volumes' : 'Saúde dos volumes'}</h2><p>Capacidade e saldo informados/importados — não são medição física do disco.</p></div><button className="filter-button" aria-pressed={criticalOnly} onClick={() => setCriticalOnly(value => !value)}><SlidersHorizontal size={15}/> {criticalOnly ? 'Mostrar todos' : 'Somente críticos'}</button></div>
          <div className="volume-grid">{shownVolumes.map(v => <VolumeCard key={v.id} volume={v} onClick={() => setSelected(v)}/>)}</div>
        </> : null}
        {active === 'Arquivos' && <section className="panel"><div className="panel-heading"><div><h2>Busca em registros importados</h2><p>Os registros da planilha não comprovam disponibilidade de arquivos reais.</p></div></div><div className="search-row"><div className="search-box"><Search size={17}/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Buscar nome, pasta ou volume..." aria-label="Buscar registros"/></div><select value={filter} onChange={e=>setFilter(e.target.value)} aria-label="Filtrar por volume"><option>Todos os volumes</option>{volumes.map(v=><option key={v.id}>{v.name}</option>)}</select></div><LegacyTable entries={matched.slice(0,60)}/><p className="table-note">Exibindo {Math.min(matched.length,60)} de {matched.length} registros agregados. Arquivos reais só aparecem após armazenamento pela aplicação.</p></section>}
        {active === 'Histórico' && <section className="panel"><div className="panel-heading"><div><h2>Observações importadas</h2><p>Esta tela ainda não é um histórico operacional. A auditoria persistida está pendente.</p></div></div><div className="activity-list">{latestEvents.map((e,i)=><div className="activity-item" key={i}><span className={`activity-icon ${e.type}`}><e.icon size={16}/></span><div><b>{e.title}</b><p>{e.detail}</p></div><time>{e.time}</time></div>)}</div></section>}
        {active === 'Relatórios' && <><div className="stats-grid"><Stat label="Maior volume por ocupado" value={`${sorted[0]?.name}`} note={`${format(sorted[0]?.occupiedGb || 0)} GB · origem: RESUMO`} icon={ArrowDownToLine}/><Stat label="Menor volume por ocupado" value={`${sorted.at(-1)?.name}`} note={`${format(sorted.at(-1)?.occupiedGb || 0)} GB · origem: RESUMO`} icon={ArrowDownToLine}/><Stat label="Capacidade total informada" value={`${format(volumes.reduce((s,v)=>s+v.capacityGb,0))} GB`} note="Soma das capacidades declaradas" icon={HardDrive}/><Stat label="Saldo negativo" value={String(volumes.filter(v=>v.freeGb<0).length)} note="Não representa espaço físico medido" icon={AlertTriangle} tone="red"/></div><section className="panel"><div className="panel-heading"><div><h2>Relatório por volume</h2><p>Métrica consistente: dados ocupados importados. Capacidades distintas preservadas.</p></div></div><VolumeReport volumes={sorted}/></section></>}
        {active === 'Análise inteligente' && <><div className="analysis-banner"><span><CircleHelp size={19}/></span><div><b>Análises são apenas recomendações</b><p>Nenhum arquivo será apagado ou movido automaticamente. Os registros legados não têm binário para hash.</p></div></div><div className="analysis-grid"><article className="analysis-card"><div className="analysis-icon danger"><AlertTriangle size={18}/></div><span className="analysis-label">ESPAÇO CRÍTICO</span><h3>BKP IMAGEM 12</h3><p>Saldo informado de −20 GB sobre capacidade de 465 GB. A planilha registra “BADBLOCK NÃO FUNCIONA”. Exige conferência do dispositivo.</p><div className="analysis-meta"><span>Origem: RESUMO</span><b>Não migrar automaticamente</b></div></article><article className="analysis-card"><div className="analysis-icon caution"><ArrowLeftRight size={18}/></div><span className="analysis-label">POSSÍVEL AVALIAÇÃO</span><h3>Volume de destino não validado</h3><p>Capacidade disponível é apenas saldo importado. Confirme o espaço físico e a conectividade antes de qualquer migração.</p><div className="analysis-meta"><span>Sem ação automática</span><b>Revisão necessária</b></div></article><article className="analysis-card"><div className="analysis-icon safe"><ShieldCheck size={18}/></div><span className="analysis-label">RESTRIÇÃO IDENTIFICADA</span><h3>Não apagar</h3><p>Os nomes BKP GRAVACOES e BKP GERAL contêm “NAO APAGAR”. Essa nota é alerta de revisão e não substitui autorização formal.</p><div className="analysis-meta"><span>Referência da planilha</span><b>Protegido para análise</b></div></article></div></>}
        {active === 'Visão geral' && <div className="lower-grid"><section className="panel activity-panel"><div className="panel-heading"><div><h2>Observações da planilha</h2><p>Referências importadas; não é trilha de auditoria operacional</p></div><button className="link-button" onClick={()=>showTab('Histórico')}>Ver histórico <ChevronRight size={15}/></button></div><div className="activity-list">{latestEvents.slice(0,2).map((e,i)=><div className="activity-item" key={i}><span className={`activity-icon ${e.type}`}><e.icon size={16}/></span><div><b>{e.title}</b><p>{e.detail}</p></div><time>{e.time}</time></div>)}</div></section><section className="panel distribution-panel"><div className="panel-heading"><div><h2>Resumo de capacidade</h2><p>Indicadores declarados · planilha</p></div></div><div className="distribution-number"><b>{format(volumes.reduce((s,v)=>s+v.occupiedGb,0))}</b><span>GB ocupados</span></div><div className="meter large"><span style={{width:`${volumes.reduce((s,v)=>s+v.capacityGb? s+v.occupiedGb: s,0)/volumes.reduce((s,v)=>s+v.capacityGb,0)*100}%`}}/></div><div className="distribution-foot"><span><i/>Importado e informado</span><span>{format(volumes.reduce((s,v)=>s+v.capacityGb,0))} GB capacidade total</span></div></section></div>}
        <footer className="data-footer"><ShieldCheck size={15}/><span>Visualização baseada nos dados fornecidos na planilha. Nenhum arquivo físico foi descoberto ou inferido.</span><span className="footer-date">Importado em 10 nov 2026</span></footer>
        </>}
      </div>
    </main>
  </div>
}

function Stat({label,value,note,icon:Icon,tone}:{label:string;value:string;note?:string;icon:typeof Database;tone?:string}){return <article className={`stat-card ${tone||''}`}><div className="stat-top"><span>{label}</span><span className="stat-icon"><Icon size={17}/></span></div><strong>{value}</strong>{note&&<small>{note}</small>}</article>}
function LegacyTable({entries}:{entries:LegacyEntry[]}){return <div className="table-wrap"><table><thead><tr><th>Registro / pasta</th><th>Volume</th><th>Data de criação</th><th>Teste / status</th><th>Tamanho</th><th>Tipo</th></tr></thead><tbody>{entries.map(e=><tr key={e.id}><td><span className="file-cell"><Folder size={16}/><b>{e.owner||'Sem nome'}</b></span></td><td>{e.volume}</td><td>{e.created||'—'}</td><td>{e.status||'Sem status'}</td><td>{e.amount?`${e.amount} ${e.unit}`:'Não informado'}</td><td><span className="legacy-tag">Legado agregado</span></td></tr>)}{entries.length===0&&<tr><td colSpan={6} className="empty-cell">Nenhum registro encontrado para os filtros aplicados.</td></tr>}</tbody></table></div>}
function VolumeReport({volumes}:{volumes:Volume[]}){return <div className="table-wrap"><table><thead><tr><th>Volume</th><th>Ocupado</th><th>Capacidade</th><th>Saldo importado</th><th>Status</th><th>Observação</th></tr></thead><tbody>{volumes.map(v=><tr key={v.id}><td><b>{v.name}</b></td><td>{format(v.occupiedGb)} GB</td><td>{format(v.capacityGb)} GB</td><td className={v.freeGb<20?'negative':''}>{format(v.freeGb)} GB</td><td><span className={`status-pill ${level(v)}`}><i/>{level(v)==='critical'?'Crítico':level(v)==='warning'?'Atenção':'Saudável'}</span></td><td>{v.note||'—'}</td></tr>)}</tbody></table></div>}
