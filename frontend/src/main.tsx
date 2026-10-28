import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { ThemeProvider, useTheme } from '@/lib/theme'
import { AuthProvider } from '@/lib/auth'
import { ApiError } from '@/lib/api'
import App from './App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
})

function ThemedToaster() {
  const { resolved } = useTheme()
  return (
    <Toaster
      theme={resolved}
      position="bottom-right"
      closeButton
      toastOptions={{ style: { fontFamily: 'inherit', fontSize: 13 } }}
    />
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <App />
            <ThemedToaster />
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
