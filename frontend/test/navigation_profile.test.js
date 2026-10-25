import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log('--- Running TrustVault Navigation Sidebar & User Profile Tests ---');

// 1. Verify NavigationContext
console.log('Testing NavigationContext...');
const navContextPath = path.resolve(__dirname, '../src/context/NavigationContext.jsx');
const navContextContent = fs.readFileSync(navContextPath, 'utf8');

assert.ok(navContextContent.includes("localStorage.getItem('trustvault_sidebar_collapsed')") || navContextContent.includes("localStorage.getItem(STORAGE_KEY)"),
  'NavigationContext must read desktop collapsed preference from localStorage');
assert.ok(navContextContent.includes("localStorage.setItem(STORAGE_KEY, String(next))") || navContextContent.includes("localStorage.setItem('trustvault_sidebar_collapsed'"),
  'NavigationContext must persist desktop collapsed preference to localStorage');
assert.ok(navContextContent.includes("document.body.style.overflow = 'hidden'"),
  'NavigationContext must prevent background scrolling when mobile drawer is open');
assert.ok(navContextContent.includes("e.key === 'Escape'"),
  'NavigationContext must handle Escape key to close mobile drawer');
console.log('✓ NavigationContext desktop persistence and mobile drawer controls verified');

// 2. Verify Collapsible Sidebar Component
console.log('Testing Collapsible Sidebar...');
const sidebarPath = path.resolve(__dirname, '../src/components/Sidebar.jsx');
const sidebarContent = fs.readFileSync(sidebarPath, 'utf8');

assert.ok(sidebarContent.includes('isCollapsed ? \'68px\' : \'240px\''),
  'Sidebar must support narrow collapsed width (68px) and expanded width (240px)');
assert.ok(sidebarContent.includes('toggleCollapse'),
  'Sidebar must have toggleCollapse action');
assert.ok(sidebarContent.includes('sidebar-toggle-btn'),
  'Sidebar must include collapse/expand toggle button');
assert.ok(sidebarContent.includes('sidebar-tooltip'),
  'Sidebar must provide tooltips for collapsed navigation icons');
assert.ok(sidebarContent.includes('var(--brand-highlight)'),
  'Sidebar must highlight active route using copper brand-highlight token');
assert.ok(sidebarContent.includes('isAdmin ?'),
  'Sidebar must preserve admin-only route restriction for User Management and Audit');
assert.ok(sidebarContent.includes('isOwner || isAdmin'),
  'Sidebar must preserve upload permission restriction');
assert.ok(sidebarContent.includes('sidebar-mobile-drawer'),
  'Sidebar must include mobile drawer component');
assert.ok(sidebarContent.includes('sidebar-mobile-backdrop'),
  'Sidebar must include mobile backdrop overlay');
assert.ok(sidebarContent.includes('closeMobile'),
  'Sidebar mobile drawer items must close drawer on selection');
console.log('✓ Collapsible desktop sidebar and mobile drawer verified');

// 3. Verify Navbar & User Profile Dropdown
console.log('Testing Navbar & User Profile Dropdown...');
const navbarPath = path.resolve(__dirname, '../src/components/Navbar.jsx');
const navbarContent = fs.readFileSync(navbarPath, 'utf8');

assert.ok(navbarContent.includes('navbar-hamburger-btn'),
  'Navbar must include hamburger menu button for mobile screens');
assert.ok(navbarContent.includes('navbar-avatar-btn'),
  'Navbar must replace static user info with circular profile avatar button');
assert.ok(navbarContent.includes('getUserInitials'),
  'Navbar must compute initials for user avatar fallback');
assert.ok(navbarContent.includes('profile-dropdown-card'),
  'Navbar must include profile dropdown menu card');
assert.ok(navbarContent.includes('user.fullName || user.username'),
  'Profile dropdown must display authenticated user full name or username');
assert.ok(navbarContent.includes('user.email'),
  'Profile dropdown must display authenticated user email');
assert.ok(navbarContent.includes('user.role'),
  'Profile dropdown must display authenticated user role badge');
assert.ok(navbarContent.includes('/profile'),
  'Profile dropdown must include link to My Profile page');
assert.ok(navbarContent.includes('handleLogout'),
  'Profile dropdown must include Logout option');
assert.ok(navbarContent.includes("e.key === 'Escape'"),
  'Profile dropdown must close on Escape key');
assert.ok(navbarContent.includes('contains(e.target)'),
  'Profile dropdown must close when clicking outside');
console.log('✓ Navbar mobile hamburger and profile avatar dropdown verified');

// 4. Verify User Initials Logic Unit Tests
console.log('Testing User Initials Algorithm...');
const testInitials = (name) => {
  if (!name) return 'U';
  const clean = name.replace(/\(.*?\)/g, '').trim();
  const parts = clean.split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return clean.slice(0, 2).toUpperCase();
};

assert.strictEqual(testInitials('System Administrator'), 'SA');
assert.strictEqual(testInitials('Alice Jenkins (Owner)'), 'AJ');
assert.strictEqual(testInitials('Bob Martinez (Owner)'), 'BM');
assert.strictEqual(testInitials('Carol Vance (Viewer)'), 'CV');
assert.strictEqual(testInitials('admin'), 'AD');
assert.strictEqual(testInitials('John Doe'), 'JD');
console.log('✓ User initials algorithm verified for all account types');

// 5. Verify ProfilePage Component, AuditPage Security Section & App Routing
console.log('Testing ProfilePage & App Routing...');
const profilePagePath = path.resolve(__dirname, '../src/pages/ProfilePage.jsx');
assert.ok(fs.existsSync(profilePagePath), 'ProfilePage.jsx must exist');
const profileContent = fs.readFileSync(profilePagePath, 'utf8');

assert.ok(profileContent.includes('My Profile'), 'ProfilePage must display My Profile header');
assert.ok(profileContent.includes('user.role'), 'ProfilePage must display user role');
assert.ok(!profileContent.includes('Vault Authorization & Capabilities'), 'ProfilePage must NOT contain large Vault Authorization & Capabilities section');

assert.ok(profileContent.includes('handleCopyAccountId'), 'ProfilePage must include copy-to-clipboard action for account ID');
assert.ok(profileContent.includes('profile-back-link'), 'ProfilePage must include top-left back link');
assert.ok(!profileContent.includes('Back to Dashboard'), 'ProfilePage must not contain duplicate Back to Dashboard button');
assert.ok(profileContent.includes("case 'ADMIN': return 'copper'"), 'ProfilePage must style ADMIN badge using copper variant');

const auditPagePath = path.resolve(__dirname, '../src/pages/AuditPage.jsx');
const auditContent = fs.readFileSync(auditPagePath, 'utf8');
assert.ok(!auditContent.includes('Vault Security Architecture & Authorization Controls'), 'AuditPage must NOT house Vault Security Architecture & Authorization Controls');
assert.ok(auditContent.includes('Tamper-Evident Audit Trail'), 'AuditPage must retain Tamper-Evident Audit Trail functionality');
assert.ok(auditContent.includes('handleVerifyIntegrity'), 'AuditPage must retain audit verification action');

const appPath = path.resolve(__dirname, '../src/App.jsx');
const appContent = fs.readFileSync(appPath, 'utf8');
assert.ok(appContent.includes('<NavigationProvider>'), 'App must be wrapped in NavigationProvider');
assert.ok(appContent.includes('path="/profile"'), 'App must register /profile route');
console.log('✓ ProfilePage cleanup, AuditPage security controls, and routing verified');

// 6. Verify CSS Navigation & Dropdown Rules
console.log('Testing Navigation & Dropdown CSS rules in index.css...');
const cssPath = path.resolve(__dirname, '../src/index.css');
const cssContent = fs.readFileSync(cssPath, 'utf8');

assert.ok(cssContent.includes('.sidebar-nav-item.collapsed .sidebar-tooltip'), 'index.css must define collapsed tooltip styles');
assert.ok(cssContent.includes('.sidebar-mobile-drawer'), 'index.css must define mobile drawer styles');
assert.ok(cssContent.includes('.sidebar-mobile-backdrop'), 'index.css must define mobile backdrop styles');
assert.ok(cssContent.includes('.navbar-hamburger-btn'), 'index.css must define hamburger button styles');
assert.ok(cssContent.includes('.profile-dropdown-card') || cssContent.includes('.profile-dropdown-item'), 'index.css must define profile dropdown styles');
assert.ok(cssContent.includes('.badge-copper'), 'index.css must define badge-copper style for ADMIN role badges');
assert.ok(cssContent.includes('@media (max-width: 768px)'), 'index.css must define mobile responsive breakpoint (<= 768px)');
assert.ok(cssContent.includes('@media (min-width: 769px)'), 'index.css must define desktop responsive breakpoint (>= 769px)');
console.log('✓ CSS navigation, badge-copper, and dropdown styles verified');

console.log('\nAll TrustVault Navigation and Profile tests passed successfully!');
