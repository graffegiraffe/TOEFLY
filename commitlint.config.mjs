// Conventional Commits with TOEFLY scopes (docs/12-implementation-plan.md §2.3).
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'header-max-length': [2, 'always', 72],
    'body-max-line-length': [2, 'always', 200],
    // The subject starts in lower case; proper nouns inside it (iPhone, README) keep their case.
    'subject-case': [2, 'never', ['sentence-case', 'start-case', 'pascal-case', 'upper-case']],
    'subject-full-stop': [2, 'never', '.'],
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'refactor', 'perf', 'test', 'docs', 'build', 'ci', 'chore', 'style', 'revert', 'release'],
    ],
    'scope-enum': [
      2,
      'always',
      [
        'gateway', 'profile', 'content', 'factory', 'practice', 'assessment', 'speech', 'planning', 'tutor-agent',
        'mockexam', 'vocabulary', 'classroom', 'billing', 'notification', 'analytics', 'ai-gateway', 'keycloak',
        'bom', 'events', 'web-common', 'security', 'observability', 'outbox', 'test-common',
        'web', 'ios', 'landing',
        'content-seed', 'content-scripts', 'compose', 'helm', 'k8s', 'argocd', 'ci', 'docs', 'plan', 'adr',
        'thesis', 'prototype', 'readme', 'release', 'tools',
      ],
    ],
    'trailer-exists': [0],
  },
};
