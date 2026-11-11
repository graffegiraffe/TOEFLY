import { cn } from '@/lib/cn';
import { COVERS } from './covers';

interface CoverProps {
  id: string;
  className?: string;
}

/** Decorative: the title next to it names the week or the section. */
export function Cover({ id, className }: CoverProps) {
  return (
    <div
      className={cn('cover', className)}
      aria-hidden="true"
      data-cover={id}
      // the art is our own static SVG from design/prototype, not user content
      dangerouslySetInnerHTML={{ __html: COVERS[id] ?? '' }}
    />
  );
}
