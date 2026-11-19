import AllegroIntegration from './AllegroIntegration.jsx'

const modules = [
  ['01', 'Produkty i źródła', 'Powiązania produktów dostawców z Twoimi ofertami.'],
  ['02', 'Reguły cenowe', 'Marże, prowizje i bezpieczna cena minimalna.'],
  ['03', 'Synchronizacja', 'Cykliczne sprawdzanie zmian i aktualizacja ofert.'],
  ['04', 'Zapasy i zestawy', 'Dostępność składników i prognozowanie zapasów.'],
]

export default function App() {
  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="/" aria-label="EcomManager — strona główna">
          <span className="brand-mark" aria-hidden="true">E</span>
          EcomManager
        </a>
        <span className="badge">Środowisko deweloperskie</span>
      </header>
      <main>
        <section className="hero" aria-labelledby="page-title">
          <p className="eyebrow">Twoje centrum sprzedaży</p>
          <h1 id="page-title">Wiele kanałów.<br />Jedno miejsce zarządzania.</h1>
          <p className="intro">Połącz źródła produktów ze swoimi ofertami. Tutaj powstanie panel automatyzacji cen, dostępności i synchronizacji.</p>
        </section>
        <AllegroIntegration />
        <section className="modules" aria-labelledby="modules-title">
          <div className="section-heading">
            <h2 id="modules-title">Planowane moduły</h2>
            <p>Podstawa gotowa do dalszej rozbudowy</p>
          </div>
          <div className="module-grid">
            {modules.map(([number, title, description]) => (
              <article className="module-card" key={number}>
                <span className="module-number">{number}</span>
                <h3>{title}</h3>
                <p>{description}</p>
                <span className="module-label">W planach</span>
              </article>
            ))}
          </div>
        </section>
      </main>
      <footer> EcomManager · Projekt inżynierski</footer>
    </div>
  )
}
