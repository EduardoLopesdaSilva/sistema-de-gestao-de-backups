'use client'

import { useState, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import { Eye, EyeOff, LockKeyhole, LogIn } from 'lucide-react'

const api = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

export default function LoginPage() {
  const router = useRouter()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await fetch(`${api}/api/login`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      if (!response.ok) {
        setError(response.status === 401 ? 'Usuário ou senha incorretos.' : response.status === 429 ? 'Muitas tentativas. Aguarde e tente novamente.' : 'Não foi possível autenticar. Verifique a conexão com o servidor.')
        return
      }
      router.replace('/')
    } catch {
      setError('Não foi possível conectar ao servidor de autenticação.')
    } finally { setLoading(false) }
  }

  return <main className="login-screen">
    <div className="login-shade" aria-hidden="true"/>
    <section className="login-card" aria-labelledby="login-title">
      <div className="login-emblem"><LockKeyhole size={18}/><span>ACESSO RESTRITO</span></div>
      <header className="login-heading"><p>CONTROLE DE TECNOLOGIA</p><h1 id="login-title">Controle TI <span>— EASE</span></h1><div className="login-divider"/><small>Entre com suas credenciais para continuar.</small></header>
      <form className="login-form" onSubmit={submit}>
        <label htmlFor="username">Login</label>
        <input id="username" name="username" autoComplete="username" autoFocus required value={username} onChange={event => setUsername(event.target.value)} placeholder="Digite seu login"/>
        <label htmlFor="password">Senha</label>
        <div className="login-password"><input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required value={password} onChange={event => setPassword(event.target.value)} placeholder="Digite sua senha"/><button type="button" aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'} onClick={() => setShowPassword(value => !value)}>{showPassword ? <EyeOff size={17}/> : <Eye size={17}/>}</button></div>
        {error && <p className="login-error" role="alert">{error}</p>}
        <button className="login-submit" type="submit" disabled={loading}>{loading ? 'Autenticando…' : <><LogIn size={16}/> Login</>}</button>
      </form>
      <footer className="login-footer"><span/>Sessão protegida · expira após inatividade</footer>
    </section>
  </main>
}
