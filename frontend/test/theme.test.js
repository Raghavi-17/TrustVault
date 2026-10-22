import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log('--- Running TrustVault Frontend Design & Theme System Tests ---');

// 1. Verify CSS variables and color palettes in index.css
const cssPath = path.resolve(__dirname, '../src/index.css');
const cssContent = fs.readFileSync(cssPath, 'utf8');

// Check Light theme colors
assert.ok(cssContent.includes('--bg-app: #F7F7F0'), 'Light background #F7F7F0 must be defined');
assert.ok(cssContent.includes('--brand-primary: #4CAF7D'), 'Light primary brand #4CAF7D must be defined');
assert.ok(cssContent.includes('--brand-hover: #378E65'), 'Light primary hover #378E65 must be defined');
assert.ok(cssContent.includes('--bg-surface: #E2EFE5'), 'Soft sage surface #E2EFE5 must be defined');
assert.ok(cssContent.includes('--bg-sidebar: #EEF5EF'), 'Sidebar background #EEF5EF must be defined');
assert.ok(cssContent.includes('--text-primary: #26352B'), 'Primary text #26352B must be defined');
assert.ok(cssContent.includes('--border-color: #DDE5DB'), 'Border color #DDE5DB must be defined');

// Check Dark theme colors (Forest Charcoal + Muted Sage, NOT navy blue)
assert.ok(cssContent.includes('--bg-app: #18231E'), 'Dark background #18231E must be defined');
assert.ok(cssContent.includes('--bg-card: #24332B'), 'Dark card #24332B must be defined');
assert.ok(cssContent.includes('--bg-sidebar: #202D25'), 'Dark sidebar #202D25 must be defined');
assert.ok(cssContent.includes('--brand-primary: #83C99C'), 'Dark primary brand #83C99C must be defined');
assert.ok(cssContent.includes('--brand-hover: #A0DDB3'), 'Dark hover #A0DDB3 must be defined');
assert.ok(cssContent.includes('--bg-surface: #34483A'), 'Dark secondary surface #34483A must be defined');
assert.ok(cssContent.includes('--text-primary: #F3F6F0'), 'Dark primary text #F3F6F0 must be defined');

console.log('✓ Test: Sage Green + Cream and Forest Charcoal color palettes verified in index.css');

// 2. Verify ThemeContext logic and localStorage persistence
const themeContextPath = path.resolve(__dirname, '../src/context/ThemeContext.jsx');
const themeContextContent = fs.readFileSync(themeContextPath, 'utf8');

assert.ok(themeContextContent.includes("localStorage.getItem('trustvault-theme')"), 'Must read preference from trustvault-theme localStorage key');
assert.ok(themeContextContent.includes("localStorage.setItem('trustvault-theme', theme)"), 'Must persist preference to trustvault-theme localStorage key');
assert.ok(themeContextContent.includes("window.matchMedia('(prefers-color-scheme: dark)')"), 'Must inspect OS theme preference as fallback');
assert.ok(themeContextContent.includes("document.documentElement.setAttribute('data-theme', theme)"), 'Must apply data-theme attribute on document root');

console.log('✓ Test: Theme toggle persistence and OS preference fallback verified in ThemeContext');

// 3. Verify ThemeToggle component accessibility
const themeTogglePath = path.resolve(__dirname, '../src/components/ThemeToggle.jsx');
const themeToggleContent = fs.readFileSync(themeTogglePath, 'utf8');

assert.ok(themeToggleContent.includes('aria-label'), 'ThemeToggle must include accessible aria-label');
assert.ok(themeToggleContent.includes('toggleTheme'), 'ThemeToggle must trigger toggleTheme on click');

console.log('✓ Test: ThemeToggle accessibility and click handler verified');

console.log('\nAll frontend theme tests passed successfully!');
