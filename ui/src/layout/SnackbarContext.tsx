import { createContext, useContext, useState, type ReactNode } from 'react';
import { Snackbar, Alert } from '@mui/material';

interface SnackbarApi {
  showError: (msg: string) => void;
  showSuccess: (msg: string) => void;
}

const SnackbarContext = createContext<SnackbarApi>({
  showError: () => {}, showSuccess: () => {},
});

export const useSnackbar = () => useContext(SnackbarContext);

export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<{ text: string; severity: 'error' | 'success' } | null>(null);

  return (
    <SnackbarContext.Provider
      value={{
        showError: (text) => setMsg({ text, severity: 'error' }),
        showSuccess: (text) => setMsg({ text, severity: 'success' }),
      }}
    >
      {children}
      <Snackbar
        open={msg !== null}
        autoHideDuration={5000}
        onClose={() => setMsg(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {msg ? (
          <Alert severity={msg.severity} onClose={() => setMsg(null)}>
            {msg.text}
          </Alert>
        ) : undefined}
      </Snackbar>
    </SnackbarContext.Provider>
  );
}
