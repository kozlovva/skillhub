import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Box, Paper, Typography, Stack } from '@mui/material';
import { teams as teamsApi } from '../api/teams';
import { useAuth } from '../auth/KeycloakProvider';
import PageHeader from '../components/PageHeader';
import TeamListPanel from '../components/TeamListPanel';
import TeamMembersPanel, { ROLE_LABELS } from '../components/TeamMembersPanel';

export default function TeamsPage() {
  const { authenticated, isAdmin, myTeamRoles, teamRoleOf } = useAuth();
  const [selected, setSelected] = useState<string | null>(null);

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });
  const teamList = teams ?? [];
  const visibleTeams = isAdmin
    ? teamList
    : teamList.filter((t) => myTeamRoles[t.slug] != null);

  useEffect(() => {
    if (!selected && visibleTeams.length > 0) {
      setSelected(visibleTeams[0].slug);
    }
  }, [selected, visibleTeams]);

  const selectedTeam = teamList.find((t) => t.slug === selected);
  const ownRole = selected ? teamRoleOf(selected) : null;
  const ownRoleLabel = ownRole ? ROLE_LABELS[ownRole] : (isAdmin ? 'Админ' : null);

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Команды"
        subtitle="Управление командами и доступом к элементам"
      />
      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' }, alignItems: 'flex-start' }}>
        <TeamListPanel
          teams={visibleTeams}
          selected={selected}
          onSelect={setSelected}
          isAdmin={isAdmin}
          authenticated={authenticated}
        />
        {selectedTeam ? (
          <TeamMembersPanel
            key={selectedTeam.slug}
            team={selectedTeam}
            isMember={isAdmin || myTeamRoles[selectedTeam.slug] != null}
            canManage={isAdmin || myTeamRoles[selectedTeam.slug] === 'OWNER'}
            ownRoleLabel={ownRoleLabel}
          />
        ) : (
          <Paper sx={{ p: 3, flex: 1, width: '100%' }} data-testid="team-detail">
            <Typography color="text.secondary">Выберите команду</Typography>
          </Paper>
        )}
      </Box>
    </Stack>
  );
}
