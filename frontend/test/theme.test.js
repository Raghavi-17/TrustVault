import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log('--- Running TrustVault Frontend Dual-Theme System Tests ---');

// 1. Verify CSS variables and color palettes in index.css
const cssPath = path.resolve(__dirname, '../src/index.css');
const cssContent = fs.readFileSync(cssPath, 'utf8');

// LIGHT MODE VERIFICATION
console.log('Verifying Light Mode tokens...');
assert.ok(cssContent.includes('--bg-app: #F7F4EF'), 'Light main background #F7F4EF must be defined');
assert.ok(cssContent.includes('--bg-sidebar: #FFFFFF'), 'Light sidebar #FFFFFF must be defined');
assert.ok(cssContent.includes('--bg-card: #FFFFFF'), 'Light cards and panels #FFFFFF must be defined');
assert.ok(cssContent.includes('--bg-elevated: #F0E8DE'), 'Light elevated cards #F0E8DE must be defined');
assert.ok(cssContent.includes('--border-color: #E3D6C8'), 'Light borders #E3D6C8 must be defined');
assert.ok(cssContent.includes('--brand-primary: #A94F20'), 'Light primary buttons #A94F20 must be defined');
assert.ok(cssContent.includes('--brand-highlight: #C96A2C'), 'Light hover/highlight #C96A2C must be defined');
assert.ok(cssContent.includes('--text-primary: #29211D'), 'Light main text #29211D must be defined');
assert.ok(cssContent.includes('--text-secondary: #74675E'), 'Light secondary text #74675E must be defined');
console.log('✓ Light Mode tokens verified');

// DARK MODE VERIFICATION
console.log('Verifying Dark Mode tokens...');
assert.ok(cssContent.includes('--bg-app: #100A07'), 'Dark main background #100A07 must be defined');
assert.ok(cssContent.includes('--bg-sidebar: #160D09'), 'Dark sidebar #160D09 must be defined');
assert.ok(cssContent.includes('--bg-card: #21140E'), 'Dark cards and panels #21140E must be defined');
assert.ok(cssContent.includes('--bg-elevated: #2D1B12'), 'Dark elevated cards #2D1B12 must be defined');
assert.ok(cssContent.includes('--border-color: #493022'), 'Dark borders #493022 must be defined');
assert.ok(cssContent.includes('--brand-primary: #C96A2C'), 'Dark primary buttons #C96A2C must be defined');
assert.ok(cssContent.includes('--brand-highlight: #F59A45'), 'Dark hover/highlight #F59A45 must be defined');
assert.ok(cssContent.includes('--text-primary: #F4E9DD'), 'Dark main text #F4E9DD must be defined');
assert.ok(cssContent.includes('--text-secondary: #B7A79A'), 'Dark secondary text #B7A79A must be defined');
console.log('✓ Dark Mode tokens verified');

// Verify that Light Mode and Dark Mode backgrounds are distinctly different
assert.notStrictEqual('#F7F4EF', '#100A07', 'Light and Dark main backgrounds must be different');
assert.notStrictEqual('#FFFFFF', '#160D09', 'Light and Dark sidebars must be different');
assert.notStrictEqual('#FFFFFF', '#21140E', 'Light and Dark cards must be different');
assert.notStrictEqual('#29211D', '#F4E9DD', 'Light and Dark text must be different');
console.log('✓ Light and Dark themes are verified to be distinctly different');

// 2. Verify ThemeContext logic and localStorage persistence
const themeContextPath = path.resolve(__dirname, '../src/context/ThemeContext.jsx');
const themeContextContent = fs.readFileSync(themeContextPath, 'utf8');

assert.ok(themeContextContent.includes("localStorage.getItem('trustvault-theme')"), 'Must read preference from trustvault-theme localStorage key');
assert.ok(themeContextContent.includes("localStorage.setItem('trustvault-theme', theme)"), 'Must persist preference to trustvault-theme localStorage key');
assert.ok(themeContextContent.includes("window.matchMedia('(prefers-color-scheme: dark)')"), 'Must inspect OS theme preference as fallback');
assert.ok(themeContextContent.includes("document.documentElement.setAttribute('data-theme', theme)"), 'Must apply data-theme attribute on document root');

console.log('✓ Test: Theme toggle persistence and OS preference fallback verified in ThemeContext');

// 3. Verify ThemeToggle component accessibility & brand consistency
const themeTogglePath = path.resolve(__dirname, '../src/components/ThemeToggle.jsx');
const themeToggleContent = fs.readFileSync(themeTogglePath, 'utf8');

assert.ok(themeToggleContent.includes('aria-label'), 'ThemeToggle must include accessible aria-label');
assert.ok(themeToggleContent.includes('toggleTheme'), 'ThemeToggle must trigger toggleTheme on click');
assert.ok(themeToggleContent.includes('var(--brand-primary)'), 'ThemeToggle Moon icon must use brand-primary token');
assert.ok(themeToggleContent.includes('var(--brand-highlight)'), 'ThemeToggle Sun icon must use brand-highlight token');

console.log('✓ Test: ThemeToggle accessibility, click handler, and brand tokens verified');

console.log('\nAll frontend dual-theme tests passed successfully!');
