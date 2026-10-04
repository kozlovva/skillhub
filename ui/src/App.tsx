import { Routes, Route } from 'react-router-dom';
import AppLayout from './layout/AppLayout';
import CatalogPage from './pages/CatalogPage';
import ElementPage from './pages/ElementPage';
import PackPage from './pages/PackPage';
import TeamsPage from './pages/TeamsPage';
import AdminCategoriesPage from './pages/AdminCategoriesPage';
import TokensPage from './pages/TokensPage';
import UploadPage from './pages/UploadPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<CatalogPage />} />
        <Route path="/elements/:slug" element={<ElementPage />} />
        <Route path="/packs/:slug" element={<PackPage />} />
        <Route path="/teams" element={<TeamsPage />} />
        <Route path="/admin/categories" element={<AdminCategoriesPage />} />
        <Route path="/tokens" element={<TokensPage />} />
        <Route path="/upload" element={<UploadPage />} />
      </Route>
    </Routes>
  );
}
