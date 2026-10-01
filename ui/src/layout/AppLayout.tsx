import { Outlet } from 'react-router-dom';
import { Typography } from '@mui/material';

export default function AppLayout() {
  return (
    <>
      <Typography variant="h4">SkillHub</Typography>
      <Outlet />
    </>
  );
}
