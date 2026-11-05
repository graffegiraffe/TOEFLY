export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://api.toefly.localhost:8080';

/** Keycloak account console: password, two-factor authentication, linked sign-ins (docs/07-ux-ui.md §5.19). */
export const ACCOUNT_CONSOLE_URL: string =
  import.meta.env.VITE_ACCOUNT_CONSOLE_URL ?? 'http://localhost:8180/realms/toefly/account';
