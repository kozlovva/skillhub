import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { createAppTheme, type AppThemeMode } from '../theme';

interface ThemeModeApi {
  mode: AppThemeMode;
  toggleMode: () => void;
}

const ThemeModeContext = createContext<ThemeModeApi>({
  mode: 'light',
  toggleMode: () => {},
});

export const useThemeMode = () => useContext(ThemeModeContext);

const STORAGE_KEY = 'skillhub-theme-mode';

function readInitialMode(): AppThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'dark' || stored === 'light') return stored;
  } catch {
    // localStorage unavailable — fall through to default
  }
  return 'light';
}

export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<AppThemeMode>(readInitialMode);

  const toggleMode = useCallback(() => {
    setMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // ignore persistence errors
      }
      return next;
    });
  }, []);

  const value = useMemo(() => ({ mode, toggleMode }), [mode, toggleMode]);
  const theme = useMemo(() => createAppTheme(mode), [mode]);

  return (
    <ThemeModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
}
