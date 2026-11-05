import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { en } from './en';

// Interface strings are English (docs/02-functional-requirements.md §0.3); the explanation language is a profile setting.
void i18n.use(initReactI18next).init({
  resources: { en: { translation: en } },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

export default i18n;
