import { Routes, Route } from 'react-router-dom';
import AppLayout from './layout/AppLayout';
import CatalogPage from './pages/CatalogPage';
import ElementPage from './pages/ElementPage';
import PackPage from './pages/PackPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<CatalogPage />} />
        <Route path="/elements/:slug" element={<ElementPage />} />
        <Route path="/packs/:slug" element={<PackPage />} />
        <Route path="/teams" element={<CatalogPage />} />
        <Route path="/admin/categories" element={<CatalogPage />} />
        <Route path="/tokens" element={<CatalogPage />} />
      </Route>
    </Routes>
  );
}
