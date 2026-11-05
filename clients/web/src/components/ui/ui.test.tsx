import { render, screen } from '@testing-library/react';
import { Button } from './button';
import { EmptyState } from './empty-state';
import { ProgressBar } from './progress-bar';

describe('ui', () => {
  it('renders the primary button with the gradient', () => {
    render(<Button>Continue</Button>);

    expect(screen.getByRole('button', { name: 'Continue' })).toHaveClass('bg-button-gradient');
  });

  it('exposes progress to assistive technology', () => {
    render(<ProgressBar value={140} label="Onboarding progress" />);

    expect(screen.getByRole('progressbar', { name: 'Onboarding progress' })).toHaveAttribute('aria-valuenow', '100');
  });

  it('shows the empty state title and text', () => {
    render(<EmptyState title="Nothing to review" text="Next review in 6 hours." />);

    expect(screen.getByRole('heading', { name: 'Nothing to review' })).toBeVisible();
    expect(screen.getByText('Next review in 6 hours.')).toBeVisible();
  });
});
