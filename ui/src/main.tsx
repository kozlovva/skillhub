import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import KeycloakProvider from './auth/KeycloakProvider';
import { SnackbarProvider } from './layout/SnackbarContext';
import { ThemeModeProvider } from './theme/ThemeModeProvider';
import App from './App';

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SnackbarProvider>
          <ThemeModeProvider>
            <KeycloakProvider>
              <App />
            </KeycloakProvider>
          </ThemeModeProvider>
        </SnackbarProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);
