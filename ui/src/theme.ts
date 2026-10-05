import { createTheme } from '@mui/material/styles';
import type { Shadows, Theme } from '@mui/material/styles';

export type AppThemeMode = 'light' | 'dark';

// Токены дизайн-системы LLM Dashboard (design-tokens.css)
const sansFont = '"Onest", "Golos Text", system-ui, -apple-system, "Segoe UI", sans-serif';
const displayFont = '"Literata", "PT Serif", Georgia, serif';

const tokens = {
  light: {
    canvas: '#f5f3ee',
    surface: '#ffffff',
    surfaceSunken: '#efece5',
    line: '#e3dfd6',
    controlBorder: '#8a8377',
    ink: '#1b1e23',
    inkMuted: '#5d5850',
    inkSubtle: '#6b665d',
    primary: '#1d5e59',
    primaryHover: '#164a46',
    primarySoft: '#e3efec',
    onPrimary: '#ffffff',
    accent: '#b9431c',
    accentSoft: '#fbe8df',
    ok: '#1b6a4f',
    info: '#2d4e8e',
    warn: '#855400',
    danger: '#b3261e',
    nav: '#16191d',
    navRaised: '#23272d',
    navInk: '#ece9e2',
    navMuted: '#9b968c',
    focusRing: '0 0 0 2px #ffffff, 0 0 0 4px #1d5e59',
  },
  dark: {
    canvas: '#111316',
    surface: '#1a1d21',
    surfaceSunken: '#15171a',
    line: '#2b2f35',
    controlBorder: '#77726a',
    ink: '#ece9e2',
    inkMuted: '#a9a499',
    inkSubtle: '#8f8a80',
    primary: '#6cc0b4',
    primaryHover: '#8ad2c7',
    primarySoft: '#16302d',
    onPrimary: '#0b1917',
    accent: '#f08a5f',
    accentSoft: '#3a2016',
    ok: '#74cfa2',
    info: '#9fb7ef',
    warn: '#e6b252',
    danger: '#f59189',
    nav: '#0c0e10',
    navRaised: '#1b1f24',
    navInk: '#ece9e2',
    navMuted: '#8f8a80',
    focusRing: '0 0 0 2px #1a1d21, 0 0 0 4px #6cc0b4',
  },
} as const;

function makeShadows(mode: AppThemeMode): Shadows {
  const sm = mode === 'dark' ? '#00000066' : '#1b1e230a';
  const lg1 = mode === 'dark' ? '#000000b3' : '#1b1e232e';
  const lg2 = mode === 'dark' ? '#00000000' : '#1b1e2314';
  const darkLg = '0 12px 28px -4px #000000b3';
  return [
    'none',
    `0 1px 2px ${sm}`,
    `0 8px 24px -6px ${lg1}, 0 2px 6px ${lg2}`,
    `0 8px 24px -6px ${lg1}, 0 2px 6px ${lg2}`,
    mode === 'dark' ? darkLg : `0 8px 24px -6px ${lg1}, 0 2px 6px ${lg2}`,
    ...Array(20).fill(mode === 'dark' ? darkLg : `0 8px 24px -6px ${lg1}, 0 2px 6px ${lg2}`),
  ] as Shadows;
}

export function createAppTheme(mode: AppThemeMode): Theme {
  const t = tokens[mode];
  const dark = mode === 'dark';

  return createTheme({
    palette: {
      mode,
      primary: { main: t.primary, light: t.primaryHover, dark: t.primaryHover, contrastText: t.onPrimary },
      secondary: { main: t.accent, contrastText: dark ? '#1c0d06' : '#ffffff' },
      success: { main: t.ok, contrastText: dark ? '#0b1917' : '#ffffff' },
      warning: { main: t.warn, contrastText: dark ? '#2e2410' : '#ffffff' },
      error: { main: t.danger, contrastText: dark ? '#3a1a17' : '#ffffff' },
      info: { main: t.info, contrastText: dark ? '#1a2438' : '#ffffff' },
      background: { default: t.canvas, paper: t.surface },
      text: { primary: t.ink, secondary: t.inkMuted, disabled: t.inkSubtle },
      divider: t.line,
    },
    typography: {
      fontFamily: sansFont,
      h1: { fontFamily: displayFont, fontWeight: 500, fontSize: '2.75rem', lineHeight: 1.1, letterSpacing: '-0.015em' },
      h2: { fontFamily: displayFont, fontWeight: 500, fontSize: '1.875rem', lineHeight: 1.2, letterSpacing: '-0.01em' },
      h3: { fontFamily: sansFont, fontWeight: 600, fontSize: '1.0625rem', lineHeight: 1.4 },
      h4: { fontFamily: sansFont, fontWeight: 600, fontSize: '1rem', lineHeight: 1.4 },
      h5: { fontFamily: sansFont, fontWeight: 600, fontSize: '0.9375rem', lineHeight: 1.4 },
      h6: { fontFamily: sansFont, fontWeight: 600, fontSize: '0.875rem', lineHeight: 1.4 },
      subtitle1: { fontSize: '0.9375rem', lineHeight: 1.45 },
      subtitle2: { fontWeight: 600, fontSize: '0.875rem' },
      body1: { fontSize: '0.875rem', lineHeight: 1.45 },
      body2: { fontSize: '0.8125rem', lineHeight: 1.4 },
      button: { fontFamily: sansFont, fontWeight: 600, textTransform: 'none' },
      caption: { fontSize: '0.75rem', lineHeight: 1.35, letterSpacing: '0.01em' },
      overline: { fontSize: '0.6875rem', fontWeight: 600, letterSpacing: '0.08em' },
    },
    shape: { borderRadius: 10 },
    shadows: makeShadows(mode),
    components: {
      MuiCssBaseline: {
        styleOverrides: (theme) => ({
          body: {
            backgroundColor: theme.palette.background.default,
            color: theme.palette.text.primary,
            fontFeatureSettings: '"tnum" 1',
          },
          html: { colorScheme: mode, scrollbarGutter: 'stable' },
          '@media (prefers-reduced-motion: reduce)': {
            '*, *::before, *::after': {
              animationDuration: '0.01ms !important',
              transitionDuration: '0.01ms !important',
            },
          },
        }),
      },
      MuiPaper: {
        defaultProps: { elevation: 1 },
        styleOverrides: {
          root: { backgroundImage: 'none', borderRadius: 10 },
          outlined: { borderColor: t.line },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            border: `1px solid ${t.line}`,
            boxShadow: makeShadows(mode)[1],
            backgroundImage: 'none',
          },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: {
            borderRadius: 6,
            paddingTop: 7,
            paddingBottom: 7,
            transition: 'background .12s, border-color .12s, color .12s',
          },
          containedPrimary: {
            '&:hover': { backgroundColor: dark ? t.primaryHover : '#164a46' },
          },
          outlined: { borderColor: t.controlBorder },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontFamily: sansFont,
            fontWeight: 600,
            borderRadius: 9999,
            fontSize: '0.75rem',
          },
          outlinedPrimary: { borderColor: t.primary },
        },
      },
      MuiAppBar: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            backgroundColor: t.nav,
            backdropFilter: 'blur(8px)',
            borderBottom: `1px solid ${t.navRaised}`,
            color: t.navInk,
            borderRadius: 0,
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          head: {
            fontWeight: 500,
            color: t.inkMuted,
            backgroundColor: t.surfaceSunken,
            borderBottom: `1px solid ${t.line}`,
          },
          root: { borderBottom: `1px solid ${t.line}` },
        },
      },
      MuiTextField: { defaultProps: { size: 'small' } },
      MuiTooltip: {
        defaultProps: { arrow: true },
        styleOverrides: { tooltip: { borderRadius: 6 } },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 6,
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
              borderColor: t.primary,
            },
          },
        },
      },
      MuiFilledInput: { styleOverrides: { root: { borderRadius: 6 } } },
      MuiAvatar: { styleOverrides: { root: { borderRadius: 6 } } },
      MuiDialog: { styleOverrides: { paper: { borderRadius: 14 } } },
      MuiMenu: { styleOverrides: { paper: { borderRadius: 10 } } },
      MuiAlert: { styleOverrides: { root: { borderRadius: 10 } } },
      MuiSkeleton: { styleOverrides: { root: { borderRadius: 6 } } },
      MuiAutocomplete: { styleOverrides: { paper: { borderRadius: 10 } } },
      MuiLinearProgress: { styleOverrides: { root: { borderRadius: 9999 } } },
    },
  });
}

export const lightTheme = createAppTheme('light');
