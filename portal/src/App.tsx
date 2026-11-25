import { Link } from "react-aria-components"
import { ApiProvider } from "./api/ApiContext.tsx"
import { SettingsDialog } from "./components/SettingsDialog.tsx"
import { routeHref } from "./routing/routes.ts"
import { useRoute } from "./routing/useRoute.ts"
import { HomeScreen } from "./screens/HomeScreen.tsx"
import { RunScreen } from "./screens/RunScreen.tsx"
import { SearchScreen } from "./screens/SearchScreen.tsx"

export function App() {
  return (
    <ApiProvider>
      <header className="app-header">
        <Link href={routeHref({ name: "home" })} className="react-aria-Link brand">
          Trains debug portal
        </Link>
        <span className="spacer" />
        <SettingsDialog />
      </header>
      <main>
        <Screen />
      </main>
    </ApiProvider>
  )
}

function Screen() {
  const route = useRoute()
  switch (route.name) {
    case "home":
      return <HomeScreen />
    case "search":
      return <SearchScreen query={route.query} />
    case "run":
      return <RunScreen key={route.runId} runId={route.runId} />
    case "notFound":
      return (
        <p>
          Nothing at <span className="mono">#/{route.path}</span>. <Link href={routeHref({ name: "home" })}>Home</Link>
        </p>
      )
  }
}
