import { Outlet, Link as RouterLink, NavLink, useNavigate } from 'react-router-dom';
import { AppBar, Toolbar, Typography, Button, Box, IconButton, ButtonBase } from '@mui/material';
import StorageIcon from '@mui/icons-material/Storage';
import LoginIcon from '@mui/icons-material/Login';
import LogoutIcon from '@mui/icons-material/Logout';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import { useAuth } from '../auth/KeycloakProvider';
import { useThemeMode } from '../theme/ThemeModeProvider';

const navItems = [
  { to: '/', label: 'Каталог' },
  { to: '/teams', label: 'Команды' },
  { to: '/tokens', label: 'API-токены' },
];

const adminNavItems = [
  { to: '/admin/categories', label: 'Категории' },
];

export default function AppLayout() {
  const { authenticated, displayName, isAdmin, login, logout } = useAuth();
  const { mode, toggleMode } = useThemeMode();
  const navigate = useNavigate();

  return (
    <Box sx={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <AppBar position="sticky">
        <Toolbar sx={{ gap: 1 }}>
          <IconButton
            component={RouterLink}
            to="/"
            aria-label="SkillHub"
            sx={{
              backgroundColor: 'primary.main',
              color: '#fff',
              borderRadius: 1,
              p: 1,
              mr: 1,
              '&:hover': { backgroundColor: 'primary.dark' },
            }}
          >
            <StorageIcon />
          </IconButton>
          <Typography
            variant="h6"
            sx={{ mr: 3, fontFamily: '"Literata", Georgia, serif', fontWeight: 500, letterSpacing: '-0.01em', color: '#ece9e2' }}
          >
            SkillHub
          </Typography>
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
            {(isAdmin ? [...navItems, ...adminNavItems] : navItems).map((item) => (
              <Button
                key={item.to}
                component={NavLink}
                to={item.to}
                end={item.to === '/'}
                sx={{
                  color: '#9b968c',
                  px: 2,
                  borderRadius: 1,
                  whiteSpace: 'nowrap',
                  '&.active': {
                    color: '#ece9e2',
                    backgroundColor: '#23272d',
                    fontWeight: 600,
                  },
                  '&:hover': { backgroundColor: '#23272d', color: '#ece9e2' },
                }}
              >
                {item.label}
              </Button>
            ))}
          </Box>
          <Box sx={{ flexGrow: 1 }} />
          <IconButton
            onClick={toggleMode}
            aria-label={mode === 'light' ? 'Включить тёмную тему' : 'Включить светлую тему'}
            sx={{
              mr: 1,
              color: '#9b968c',
              '&:hover': { backgroundColor: '#23272d', color: '#ece9e2' },
            }}
          >
            {mode === 'light' ? <DarkModeIcon /> : <LightModeIcon />}
          </IconButton>
          {authenticated ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <ButtonBase
                sx={{
                  borderRadius: 1,
                  px: 1.5,
                  py: 0.75,
                  backgroundColor: '#23272d',
                  color: '#ece9e2',
                  fontWeight: 600,
                  fontSize: 14,
                }}
              >
                {displayName}
              </ButtonBase>
              <Button
                color="inherit"
                startIcon={<LogoutIcon />}
                onClick={() => { logout(); navigate('/'); }}
              >
                Выйти
              </Button>
            </Box>
          ) : (
            <Button variant="contained" startIcon={<LoginIcon />} onClick={login}>
              Войти
            </Button>
          )}
        </Toolbar>
      </AppBar>
      <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 3 }, maxWidth: 1200, width: '100%', mx: 'auto' }}>
        <Outlet />
      </Box>
    </Box>
  );
}
