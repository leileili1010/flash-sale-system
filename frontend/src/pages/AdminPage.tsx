import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { DropsTable } from '../features/admin/DropsTable';
import { InventoryHealth } from '../features/admin/InventoryHealth';
import { NewDropForm } from '../features/admin/NewDropForm';

const tabs = [
  { id: 'drops', label: 'Drops' },
  { id: 'new', label: 'New drop' },
  { id: 'inventory', label: 'Inventory health' },
] as const;
type TabId = (typeof tabs)[number]['id'];

export function AdminPage() {
  const [params, setParams] = useSearchParams();
  const tab: TabId = tabs.find((t) => t.id === params.get('tab'))?.id ?? 'drops';
  const [created, setCreated] = useState<string | null>(null);
  const go = (id: TabId) => setParams(id === 'drops' ? {} : { tab: id }, { replace: true });

  return (
    <div className="flex flex-col gap-6 pt-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-[40px] font-bold leading-tight">Admin console</h1>
          <p className="mt-1 text-muted">Schedule drops and keep an eye on inventory.</p>
        </div>
        <div role="tablist" aria-label="Admin sections" className="flex rounded-full bg-white p-1 shadow-card">
          {tabs.map((t) => (
            <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => { setCreated(null); go(t.id); }}
                    className={`rounded-full px-4 py-2 text-sm font-bold transition ${tab === t.id ? 'bg-ink text-white' : 'text-muted hover:text-ink'}`}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {created && tab === 'drops' && (
        <p role="status" className="rounded-2xl bg-live-ring px-4 py-3 text-sm font-medium text-live-ink">{created}</p>
      )}
      <section role="tabpanel" aria-label={tabs.find((t) => t.id === tab)!.label}>
        {tab === 'drops' && <DropsTable />}
        {tab === 'new' && (
          <NewDropForm onCreated={(drop) => {
            setCreated(drop.status === 'DRAFT' ? `Draft “${drop.title}” saved.` : `“${drop.title}” is published.`);
            go('drops');
          }} />
        )}
        {tab === 'inventory' && <InventoryHealth />}
      </section>
    </div>
  );
}
