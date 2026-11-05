import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ResponseError } from '@/api/generated';
import { SaveStatus } from './settings-ui';

describe('Settings save status', () => {
  it('shows Saved after a save', () => {
    render(<SaveStatus saved error={null} />);
    expect(screen.getByRole('status')).toHaveTextContent('Saved');
  });

  it('explains a version conflict (412) instead of a generic error', () => {
    render(<SaveStatus saved={false} error={new ResponseError(new Response(null, { status: 412 }))} />);
    expect(screen.getByRole('alert')).toHaveTextContent('changed in another window');
  });

  it('shows the generic error otherwise', () => {
    render(<SaveStatus saved={false} error={new Error('boom')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
  });
});
