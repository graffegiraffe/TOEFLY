/** Blurred brand circles behind glass: mesh-auth for sign-in and onboarding, mesh-app behind the shell. */
export function Mesh({ variant }: { variant: 'auth' | 'app' }) {
  return (
    <div className={variant === 'app' ? 'mesh app' : 'mesh'} aria-hidden="true">
      <i className="b1" />
      <i className="b2" />
      <i className="b3" />
    </div>
  );
}
