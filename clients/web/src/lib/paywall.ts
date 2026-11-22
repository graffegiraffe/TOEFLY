import { requiredEntitlement } from './billing';

// The Paywall opens wherever a request is refused for a missing right (docs/05 §2.9, docs/07 §3.21): the API client
// reports the refusal here and the host mounted in the app frame shows the dialog.

type Listener = (entitlement: string) => void;

const listeners = new Set<Listener>();

export function onPaywall(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function openPaywall(entitlement: string): void {
  listeners.forEach((listener) => listener(entitlement));
}

/** Reads a refused response without consuming it; opens the Paywall for `403 entitlement-required`. */
export async function reportRefusal(response: Response): Promise<void> {
  if (response.status !== 403) return;
  try {
    const problem: unknown = await response.clone().json();
    const entitlement = requiredEntitlement(response.status, problem);
    if (entitlement !== null) openPaywall(entitlement);
  } catch {
    // not a problem document
  }
}
