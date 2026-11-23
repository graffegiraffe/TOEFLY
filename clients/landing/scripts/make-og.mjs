// Builds src/assets/og.png (Open Graph card) and public/apple-touch-icon.png from the brand gradient and the hero
// screenshot. Run after replacing the hero screenshot: node scripts/make-og.mjs
import sharp from 'sharp';

const root = new URL('../', import.meta.url);
const W = 1200;
const H = 630;

const background = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#04AC6A"/><stop offset=".5" stop-color="#3CC98A"/><stop offset="1" stop-color="#A7EBC8"/>
  </linearGradient></defs>
  <rect width="${W}" height="${H}" fill="url(#g)"/>
  <rect x="0" y="0" width="${W}" height="250" fill="#F4F8F5"/>
  <text x="64" y="120" font-family="Inter, Helvetica, Arial, sans-serif" font-size="76" font-weight="700" letter-spacing="-2"><tspan fill="#14231A">TOE</tspan><tspan fill="#04AC6A">FLY</tspan></text>
  <text x="64" y="190" font-family="Inter, Helvetica, Arial, sans-serif" font-size="38" font-weight="600" fill="#14231A">Prepare for the new TOEFL iBT week by week</text>
</svg>`);

const hero = await sharp(new URL('src/assets/screenshots/hero-light.png', root).pathname)
  .resize({ width: 1000 })
  .toBuffer();

await sharp(background)
  .composite([{ input: hero, top: 236, left: 120 }])
  .png({ compressionLevel: 9 })
  .toFile(new URL('src/assets/og.png', root).pathname);

const icon = Buffer.from(
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 180"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#04AC6A"/><stop offset=".5" stop-color="#3CC98A"/><stop offset="1" stop-color="#A7EBC8"/></linearGradient></defs><rect width="180" height="180" fill="url(#g)"/><path d="M50 56h80M90 56v74" stroke="#fff" stroke-width="20" stroke-linecap="round"/></svg>`,
);
await sharp(icon).resize(180, 180).png().toFile(new URL('public/apple-touch-icon.png', root).pathname);

console.log('Wrote src/assets/og.png and public/apple-touch-icon.png');
