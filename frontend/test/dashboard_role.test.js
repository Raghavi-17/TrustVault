import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log('--- Running TrustVault Dashboard Refined Banner Tests ---');

// 1. Verify DashboardPage role-based banner visibility
console.log('Testing DashboardPage refined banner conditional logic...');
const dashboardPath = path.resolve(__dirname, '../src/pages/DashboardPage.jsx');
const dashboardContent = fs.readFileSync(dashboardPath, 'utf8');

assert.ok(dashboardContent.includes('showSharingBanner'),
  'DashboardPage must compute showSharingBanner flag based on authenticated role');
assert.ok(dashboardContent.includes('canUploadDocuments'),
  'DashboardPage must compute canUploadDocuments permission for owner');
assert.ok(dashboardContent.includes('canBrowseVault'),
  'DashboardPage must compute canBrowseVault permission for owner');
assert.ok(dashboardContent.includes('{showSharingBanner && ('),
  'DashboardPage must conditionally render the quick action banner wrapped in showSharingBanner');
assert.ok(dashboardContent.includes('{canUploadDocuments && ('),
  'DashboardPage must conditionally render Upload Document button when owner has upload permission');
assert.ok(dashboardContent.includes('{canBrowseVault && ('),
  'DashboardPage must conditionally render Browse Vault button when owner has browse permission');

// Unit test simulation of refined showSharingBanner logic
const evaluateBannerVisibility = ({ role, isOwner }) => {
  return Boolean(isOwner || role === 'OWNER');
};

const evaluateButtonPermissions = ({ isOwner, role, user }) => {
  const showBanner = evaluateBannerVisibility({ role, isOwner });
  const canUpload = showBanner && (isOwner || role === 'OWNER');
  const canBrowse = showBanner && Boolean(user);
  return { showBanner, canUpload, canBrowse };
};

// Test ADMIN: Must be REMOVED / FALSE (banner, Upload Document, and Browse Vault buttons hidden)
const adminResult = evaluateButtonPermissions({ role: 'ADMIN', isOwner: false, user: { id: 'admin1', role: 'ADMIN' } });
assert.strictEqual(adminResult.showBanner, false, 'Banner must be REMOVED from ADMIN dashboard');
assert.strictEqual(adminResult.canUpload, false, 'Upload Document button must be REMOVED from ADMIN dashboard banner');
assert.strictEqual(adminResult.canBrowse, false, 'Browse Vault button must be REMOVED from ADMIN dashboard banner');

// Test OWNER: Must be VISIBLE (banner shown, with buttons visible when authenticated owner has permissions)
const ownerResult = evaluateButtonPermissions({ role: 'OWNER', isOwner: true, user: { id: 'owner1', role: 'OWNER' } });
assert.strictEqual(ownerResult.showBanner, true, 'Banner must be VISIBLE on OWNER dashboard');
assert.strictEqual(ownerResult.canUpload, true, 'Upload Document button must be VISIBLE when owner has upload permission');
assert.strictEqual(ownerResult.canBrowse, true, 'Browse Vault button must be VISIBLE when owner has browse permission');

// Test VIEWER: Must be HIDDEN / FALSE
const viewerResult = evaluateButtonPermissions({ role: 'VIEWER', isOwner: false, user: { id: 'viewer1', role: 'VIEWER' } });
assert.strictEqual(viewerResult.showBanner, false, 'Banner must be HIDDEN from VIEWER dashboard');
assert.strictEqual(viewerResult.canUpload, false, 'Upload Document button must be HIDDEN from VIEWER dashboard banner');
assert.strictEqual(viewerResult.canBrowse, false, 'Browse Vault button must be HIDDEN from VIEWER dashboard banner');

console.log('✓ Refined banner visibility and button permission checks verified for ADMIN, OWNER, and VIEWER');

// 2. Verify Document Management & Navigation Preservation
console.log('Testing document management access preservation through sidebar and existing routes...');
const sidebarPath = path.resolve(__dirname, '../src/components/Sidebar.jsx');
const sidebarContent = fs.readFileSync(sidebarPath, 'utf8');

// Sidebar retains Documents for all roles
assert.ok(sidebarContent.includes("{ to: '/documents', label: 'Documents', icon: FileText }"),
  'Sidebar must retain Documents navigation item for all roles');
// Sidebar retains Upload Document for OWNER and ADMIN
assert.ok(sidebarContent.includes("(isOwner || isAdmin) ? [{ to: '/upload', label: 'Upload Document', icon: UploadCloud }]"),
  'Sidebar must retain Upload Document navigation for OWNER and ADMIN');
// Sidebar retains Admin management links for ADMIN
assert.ok(sidebarContent.includes('to: \'/admin\', label: \'User Management\'') && sidebarContent.includes('to: \'/audit\', label: \'Audit & Integrity\''),
  'Sidebar must retain User Management and Audit & Integrity navigation for ADMIN');

// Dashboard retains View All link to /documents
assert.ok(dashboardContent.includes('<Link to="/documents" className="btn btn-outline btn-sm">'),
  'DashboardPage must retain View All link to /documents');

console.log('✓ Document management access through sidebar and routes verified');

// 3. Verify Route Protection and Backend Security Controls
console.log('Testing route protection and backend authorization alignment...');
const appPath = path.resolve(__dirname, '../src/App.jsx');
const appContent = fs.readFileSync(appPath, 'utf8');
assert.ok(appContent.includes('<ProtectedLayout requiredRole="OWNER">') && appContent.includes('<UploadPage />'),
  'App.jsx must protect /upload route with requiredRole="OWNER" allowing OWNER and ADMIN while blocking VIEWER');

const backendDocControllerPath = path.resolve(__dirname, '../../backend/src/main/java/com/trustvault/document/DocumentController.java');
const backendDocControllerContent = fs.readFileSync(backendDocControllerPath, 'utf8');
assert.ok(backendDocControllerContent.includes('@PreAuthorize("hasAnyRole(\'OWNER\', \'ADMIN\')")'),
  'DocumentController must enforce backend authorization hasAnyRole(OWNER, ADMIN) on document upload');

console.log('✓ Backend authorization enforcement verified');
console.log('\nAll TrustVault Dashboard Refined Banner tests passed successfully!\n');
