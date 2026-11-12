import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import type { AdminDrop } from '../../api/types';
import { Button } from '../../components/Button';
import { formatMoney } from '../../lib/format';

/** Value for a <input type="datetime-local"> in the visitor's time zone. */
function localInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function nextHour(offsetHours: number): string {
  const date = new Date();
  date.setMinutes(0, 0, 0);
  date.setHours(date.getHours() + 1 + offsetHours);
  return localInputValue(date);
}

export function NewDropForm({ onCreated }: { onCreated: (drop: AdminDrop) => void }) {
  const queryClient = useQueryClient();
  const products = useQuery({ queryKey: ['products'], queryFn: endpoints.products, staleTime: 60_000 });

  const [productId, setProductId] = useState('');
  const [title, setTitle] = useState('');
  const [price, setPrice] = useState('');
  const [start, setStart] = useState(() => nextHour(0));
  const [end, setEnd] = useState(() => nextHour(2));
  const [stock, setStock] = useState('50');
  const [publish, setPublish] = useState(true);

  const create = useMutation({
    mutationFn: endpoints.admin.createDrop,
    onSuccess: (drop) => {
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      void queryClient.invalidateQueries({ queryKey: ['drops'] });
      onCreated(drop);
    },
  });

  const selected = products.data?.find((p) => String(p.id) === productId);

  function chooseProduct(id: string) {
    setProductId(id);
    const product = products.data?.find((p) => String(p.id) === id);
    if (product) {
      if (!title) setTitle(`${product.series}: ${product.name} Drop`);
      if (!price) setPrice(String(product.retailPrice));
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate({
      productId: Number(productId),
      title,
      dropPrice: Number(price),
      startTime: new Date(start).toISOString(),
      endTime: new Date(end).toISOString(),
      totalStock: Number(stock),
      publish,
    });
  }

  const error = create.error instanceof ApiError ? create.error : null;
  const fieldError = (name: string) => error?.fieldErrors[name];
  const input = 'mt-1.5 w-full rounded-2xl border border-line bg-white px-4 py-3 text-base outline-none focus:border-pink-400 focus:ring-3 focus:ring-pink-50';

  return (
    <form onSubmit={submit} className="grid max-w-3xl grid-cols-1 gap-5 rounded-3xl bg-white p-7 shadow-card sm:grid-cols-2" noValidate>
      <label className="text-sm font-semibold sm:col-span-2">
        Product
        <select className={input} value={productId} onChange={(e) => chooseProduct(e.target.value)} required>
          <option value="" disabled>Choose a product…</option>
          {products.data?.map((p) => (
            <option key={p.id} value={p.id}>{p.name} · {p.series} ({formatMoney(p.retailPrice)} retail)</option>
          ))}
        </select>
        {fieldError('productId') && <span className="mt-1 block text-sm text-pink-600">{fieldError('productId')}</span>}
      </label>

      <label className="text-sm font-semibold sm:col-span-2">
        Title
        <input className={input} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} required />
        {fieldError('title') && <span className="mt-1 block text-sm text-pink-600">{fieldError('title')}</span>}
      </label>

      <label className="text-sm font-semibold">
        Drop price (USD)
        <input className={input} type="number" min="0.01" step="0.01" inputMode="decimal" value={price}
               onChange={(e) => setPrice(e.target.value)} required />
        {selected && Number(price) > 0 && Number(price) < selected.retailPrice && (
          <span className="mt-1 block text-xs font-medium text-live-ink">
            {Math.round((1 - Number(price) / selected.retailPrice) * 100)}% off retail
          </span>
        )}
        {fieldError('dropPrice') && <span className="mt-1 block text-sm text-pink-600">{fieldError('dropPrice')}</span>}
      </label>

      <label className="text-sm font-semibold">
        Units
        <input className={input} type="number" min="1" max="100000" step="1" value={stock}
               onChange={(e) => setStock(e.target.value)} required />
        {fieldError('totalStock') && <span className="mt-1 block text-sm text-pink-600">{fieldError('totalStock')}</span>}
      </label>

      <label className="text-sm font-semibold">
        Starts
        <input className={input} type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} required />
        {fieldError('startTime') && <span className="mt-1 block text-sm text-pink-600">{fieldError('startTime')}</span>}
      </label>

      <label className="text-sm font-semibold">
        Ends
        <input className={input} type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} required />
        {fieldError('endTime') && <span className="mt-1 block text-sm text-pink-600">{fieldError('endTime')}</span>}
      </label>

      <label className="flex items-center gap-3 text-sm font-semibold sm:col-span-2">
        <input type="checkbox" checked={publish} onChange={(e) => setPublish(e.target.checked)} className="size-5 accent-pink-600" />
        Publish now (otherwise save as a draft you can publish later)
      </label>

      {error && Object.keys(error.fieldErrors).length === 0 && (
        <p role="alert" className="rounded-2xl bg-pink-50 px-4 py-3 text-sm font-medium text-[#A02656] sm:col-span-2">{error.message}</p>
      )}

      <div className="sm:col-span-2">
        <Button type="submit" size="lg" disabled={create.isPending || !productId}>
          {create.isPending ? 'Saving…' : publish ? 'Create and publish' : 'Save draft'}
        </Button>
      </div>
    </form>
  );
}
