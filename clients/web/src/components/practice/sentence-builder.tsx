import {
  DndContext,
  PointerSensor,
  TouchSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import { GripVertical, RotateCcw } from 'lucide-react';
import { useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { emptySlots, place, placeInFirstFree, pool, remove, type Slots } from '@/lib/practice/chunks';
import { cn } from '@/lib/cn';
import { templateOf, type ItemContent } from './content';

interface SentenceBuilderProps {
  item: ItemContent;
  value: Slots;
  onChange: (slots: Slots) => void;
}

/**
 * DragChunks of docs/07-ux-ui.md §3.10 (US-09.01): drag with a mouse or a finger, tap to place or return, and the
 * keyboard: Enter places a fragment in the first free slot, Space picks it up, arrows choose the slot, Enter drops,
 * Escape cancels, Backspace on a placed fragment returns it (WCAG 2.5.7).
 */
export function SentenceBuilder({ item, value, onChange }: SentenceBuilderProps) {
  const { t } = useTranslation();
  const chunks = item.chunks ?? [];
  const textOf = (id: string) => chunks.find((chunk) => chunk.id === id)?.text ?? id;
  const [held, setHeld] = useState<{ id: string; slot: number } | null>(null);
  const [message, setMessage] = useState('');
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 120, tolerance: 6 } }),
  );

  const update = (slots: Slots) => onChange(slots);

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    const id = String(active.id).replace(/^chunk-/, '');
    if (!over) return;
    if (over.id === 'pool') {
      update(remove(value, id));
      setMessage(t('practice.sentence.returned', { text: textOf(id) }));
    } else {
      const slot = Number(String(over.id).replace('slot-', ''));
      update(place(value, id, slot));
      setMessage(t('practice.sentence.dropped', { text: textOf(id), n: slot + 1 }));
    }
  };

  const onChunkKey = (id: string, inSlot: boolean) => (event: KeyboardEvent<HTMLButtonElement>) => {
    if (held) {
      if (
        event.key === 'ArrowRight' ||
        event.key === 'ArrowDown' ||
        event.key === 'ArrowLeft' ||
        event.key === 'ArrowUp'
      ) {
        event.preventDefault();
        const step = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1 : -1;
        setHeld({ ...held, slot: (held.slot + step + value.length) % value.length });
      } else if (event.key === 'Enter') {
        event.preventDefault();
        update(place(value, held.id, held.slot));
        setMessage(t('practice.sentence.dropped', { text: textOf(held.id), n: held.slot + 1 }));
        setHeld(null);
      } else if (event.key === 'Escape') {
        event.preventDefault();
        setHeld(null);
        setMessage(t('practice.sentence.cancelled'));
      }
      return;
    }
    if (event.key === ' ') {
      event.preventDefault();
      const start = Math.max(0, inSlot ? value.indexOf(id) : value.indexOf(null));
      setHeld({ id, slot: start });
      setMessage(t('practice.sentence.pickedUp', { text: textOf(id) }));
    } else if (event.key === 'Backspace' && inSlot) {
      event.preventDefault();
      update(remove(value, id));
      setMessage(t('practice.sentence.returned', { text: textOf(id) }));
    }
  };

  const onChunkClick = (id: string, inSlot: boolean) => {
    if (held) return;
    if (inSlot) {
      update(remove(value, id));
      setMessage(t('practice.sentence.returned', { text: textOf(id) }));
    } else {
      const next = placeInFirstFree(value, id);
      update(next);
      const slot = next.indexOf(id);
      if (slot >= 0) setMessage(t('practice.sentence.dropped', { text: textOf(id), n: slot + 1 }));
    }
  };

  const parts = templateOf(item).map((part, i, all) => ({
    part,
    slot: all.slice(0, i).filter((p) => p.kind === 'SLOT').length,
  }));
  const free = pool(
    chunks.map((chunk) => chunk.id),
    value,
  );

  return (
    <DndContext sensors={sensors} onDragEnd={onDragEnd}>
      <div className="flex flex-col gap-6">
        {item.contextLine && (
          <p className="max-w-[80%] self-start rounded-2xl rounded-bl-sm bg-surface-sunken px-4 py-3 text-body text-ink">
            {item.contextLine}
          </p>
        )}
        <div
          role="group"
          aria-label={t('practice.sentence.slots')}
          className="flex flex-wrap items-center gap-x-2 gap-y-3 rounded-xl border border-border bg-surface-task p-4 text-body-lg"
        >
          {parts.map(({ part, slot: index }, i) => {
            if (part.kind === 'FIXED') {
              return (
                <span key={i} className="text-ink">
                  {part.text}
                </span>
              );
            }
            return (
              <SlotView
                key={i}
                index={index}
                target={held?.slot === index}
                chunkId={value[index] ?? null}
                text={value[index] ? textOf(value[index]!) : ''}
                onClick={onChunkClick}
                onKeyDown={onChunkKey}
              />
            );
          })}
        </div>
        <PoolView label={t('practice.sentence.pool')}>
          {free.map((id) => (
            <Chunk
              key={id}
              id={id}
              text={textOf(id)}
              held={held?.id === id}
              onClick={() => onChunkClick(id, false)}
              onKeyDown={onChunkKey(id, false)}
            />
          ))}
        </PoolView>
        <div className="flex items-center justify-between gap-3">
          <p className="text-caption text-ink-muted">{t('practice.sentence.chunkHint')}</p>
          <Button variant="ghost" onClick={() => update(emptySlots(value.length))}>
            <RotateCcw className="size-4" aria-hidden="true" />
            {t('practice.sentence.reset')}
          </Button>
        </div>
        <p className="sr-only" aria-live="assertive">
          {message}
        </p>
      </div>
    </DndContext>
  );
}

function PoolView({ label, children }: { label: string; children: React.ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({ id: 'pool' });
  return (
    <div
      ref={setNodeRef}
      role="group"
      aria-label={label}
      className={cn('flex min-h-14 flex-wrap gap-2 rounded-xl p-2', isOver && 'bg-accent-soft')}
    >
      {children}
    </div>
  );
}

interface SlotViewProps {
  index: number;
  chunkId: string | null;
  text: string;
  target: boolean;
  onClick: (id: string, inSlot: boolean) => void;
  onKeyDown: (id: string, inSlot: boolean) => (event: KeyboardEvent<HTMLButtonElement>) => void;
}

function SlotView({ index, chunkId, text, target, onClick, onKeyDown }: SlotViewProps) {
  const { t } = useTranslation();
  const { setNodeRef, isOver } = useDroppable({ id: `slot-${index}` });
  return (
    <span
      ref={setNodeRef}
      data-slot={index}
      aria-label={chunkId ? undefined : t('practice.sentence.emptySlot', { n: index + 1 })}
      role={chunkId ? undefined : 'img'}
      className={cn(
        'inline-flex min-h-10 min-w-20 items-center justify-center rounded-lg border border-dashed border-border-strong px-1',
        (isOver || target) && 'border-solid border-accent bg-accent-soft',
      )}
    >
      {chunkId && (
        <Chunk
          id={chunkId}
          text={text}
          inSlot
          label={`${t('practice.sentence.slot', { n: index + 1 })}: ${text}`}
          onClick={() => onClick(chunkId, true)}
          onKeyDown={onKeyDown(chunkId, true)}
        />
      )}
    </span>
  );
}

interface ChunkProps {
  id: string;
  text: string;
  inSlot?: boolean;
  held?: boolean;
  label?: string;
  onClick: () => void;
  onKeyDown: (event: KeyboardEvent<HTMLButtonElement>) => void;
}

function Chunk({ id, text, inSlot, held, label, onClick, onKeyDown }: ChunkProps) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: `chunk-${id}` });
  // the keyboard is handled here, not by dnd-kit, so its own key listeners and roles are left out
  const { onKeyDown: _ignored, ...pointer } = listeners ?? {};
  void _ignored;
  return (
    <button
      ref={setNodeRef}
      type="button"
      data-chunk={id}
      aria-label={label}
      aria-pressed={held || undefined}
      aria-describedby={attributes['aria-describedby']}
      onClick={onClick}
      onKeyDown={onKeyDown}
      {...pointer}
      style={transform ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0) scale(1.04)` } : undefined}
      className={cn(
        'inline-flex touch-none items-center gap-1 rounded-full border border-border-strong bg-surface px-3 py-1.5 text-body font-medium text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring',
        isDragging && 'relative z-10 shadow-e2',
        held && 'border-2 border-primary',
        inSlot && 'border-transparent bg-accent-soft',
      )}
    >
      {!inSlot && <GripVertical className="size-3 text-ink-subtle" aria-hidden="true" />}
      {text}
    </button>
  );
}
