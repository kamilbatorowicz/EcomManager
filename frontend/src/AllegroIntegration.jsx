import { useEffect, useState } from 'react'

const messages = {
  connected: 'Konto Allegro Sandbox zostało połączone.',
  invalid_state: 'Sesja łączenia wygasła lub jest nieprawidłowa. Rozpocznij ponownie.',
  denied: 'Allegro zwróciło access_denied. Spróbuj ponownie i zaakceptuj dostęp na ekranie zgody Allegro.',
  invalid_scope: 'Allegro odrzuciło zakres uprawnień (invalid_scope). Sprawdź uprawnienie „Odczyt danych konta” w aplikacji na Sandboxie.',
  invalid_request: 'Allegro odrzuciło parametry autoryzacji (invalid_request). Sprawdź adres przekierowania i typ aplikacji.',
  unauthorized_client: 'Allegro nie zezwala aplikacji na ten sposób logowania (unauthorized_client). Sprawdź typ aplikacji i Client ID na Sandboxie.',
  unsupported_response_type: 'Allegro nie obsługuje wybranego sposobu autoryzacji (unsupported_response_type). Sprawdź rejestrację aplikacji.',
  server_error: 'Allegro zgłosiło błąd serwera. Spróbuj połączyć konto później.',
  temporarily_unavailable: 'Autoryzacja Allegro jest chwilowo niedostępna. Spróbuj ponownie później.',
  missing_code: 'Allegro nie przekazało kodu autoryzacyjnego. Rozpocznij łączenie ponownie z panelu.',
  authorization_error: 'Allegro zwróciło nierozpoznany błąd autoryzacji. Sprawdź konfigurację aplikacji na Sandboxie.',
  error: 'Nie udało się połączyć konta. Sprawdź konfigurację aplikacji Allegro i spróbuj ponownie.',
}

const states = {
  NOT_CONFIGURED: 'Uzupełnij konfigurację Allegro w backendzie i uruchom go ponownie.',
  DISCONNECTED: 'Połącz konto, aby rozpocząć integrację z Allegro Sandbox.',
  REAUTH_REQUIRED: 'Dostęp do konta wygasł. Połącz konto ponownie.',
  EXPIRED: 'Token wygasł. System spróbuje go odświeżyć. Możesz też połączyć konto ponownie.',
  REFRESH_ERROR: 'Konto jest połączone, ale ostatnia próba odświeżenia dostępu nie powiodła się.',
}

export default function AllegroIntegration() {
  const [connection, setConnection] = useState(null)
  const [loading, setLoading] = useState(true)
  const [connecting, setConnecting] = useState(false)
  const [error, setError] = useState('')
  const [notice] = useState(() => {
    const url = new URL(window.location.href)
    return messages[url.searchParams.get('allegro')] || ''
  })

  useEffect(() => {
    const url = new URL(window.location.href)
    if (url.searchParams.has('allegro')) {
      url.searchParams.delete('allegro')
      window.history.replaceState(null, '', url.pathname + url.search + url.hash)
    }

    const controller = new AbortController()
    let active = true

    async function loadStatus() {
      try {
        const response = await fetch('/api/integrations/allegro/status', {
          signal: controller.signal,
          cache: 'no-store',
        })
        if (!response.ok) throw new Error()
        const data = await response.json()
        if (active) {
          setConnection(data)
          setError('')
        }
      } catch {
        if (active) setError('Nie można pobrać statusu Allegro. Sprawdź, czy backend jest uruchomiony.')
      } finally {
        if (active) setLoading(false)
      }
    }

    loadStatus()
    const interval = setInterval(loadStatus, 60000)
    return () => {
      active = false
      controller.abort()
      clearInterval(interval)
    }
  }, [])

  async function connect() {
    setConnecting(true)
    setError('')
    try {
      const response = await fetch('/api/integrations/allegro/authorize', { method: 'POST' })
      if (!response.ok) throw new Error()
      const data = await response.json()
      window.location.assign(data.url)
    } catch {
      setError('Nie udało się rozpocząć łączenia. Sprawdź konfigurację i dostępność backendu.')
      setConnecting(false)
    }
  }

  const connected = connection?.state === 'CONNECTED' || connection?.state === 'REFRESH_ERROR'

  return (
    <section className="integration-section" aria-labelledby="allegro-title">
      <h2 id="allegro-title">Allegro Sandbox</h2>
      <div className="integration-card">
        <div className="integration-details" aria-live="polite">
          {loading && <p>Sprawdzanie połączenia z Allegro…</p>}
          {connected && <p className="integration-account">Połączono z kontem <strong>{connection.login}</strong></p>}
          {connection && states[connection.state] && <p>{states[connection.state]}</p>}
          {notice && <p role="status">{notice}</p>}
          {error && <p className="message-error" role="alert">{error}</p>}
        </div>
        <button className="button" onClick={connect}
          disabled={loading || connecting || !connection || connection.state === 'NOT_CONFIGURED'}>
          {connecting ? 'Przekierowywanie…' : connected ? 'Połącz ponownie' : 'Połącz z Allegro Sandbox'}
        </button>
      </div>
    </section>
  )
}
