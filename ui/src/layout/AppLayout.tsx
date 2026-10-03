import { Outlet, Link as RouterLink, useNavigate } from 'react-router-dom';
import { AppBar, Toolbar, Typography, Button, Box, IconButton } from '@mui/material';
import StorageIcon from '@mui/icons-material/Storage';
import { useAuth } from '../auth/KeycloakProvider';

export default function AppLayout() {
  const { authenticated, displayName, login, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <>
      <AppBar position="static">
        <Toolbar>
          <IconButton component={RouterLink} to="/" color="inherit" size="large">
            <StorageIcon />
          </IconButton>
          <Typography variant="h6" sx={{ mr: 3 }}>SkillHub</Typography>
          <Button color="inherit" component={RouterLink} to="/">Каталог</Button>
          <Button color="inherit" component={RouterLink} to="/teams">Команды</Button>
          <Button color="inherit" component={RouterLink} to="/tokens">API-токены</Button>
          <Button color="inherit" component={RouterLink} to="/admin/categories">Категории</Button>
          <Box sx={{ flexGrow: 1 }} />
          {authenticated ? (
            <>
              <Typography sx={{ mr: 2 }}>{displayName}</Typography>
              <Button color="inherit" onClick={() => { logout(); navigate('/'); }}>Выйти</Button>
            </>
          ) : (
            <Button color="inherit" onClick={login}>Войти</Button>
          )}
        </Toolbar>
      </AppBar>
      <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
        <Outlet />
      </Box>
    </>
  );
}
