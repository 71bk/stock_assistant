import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

try {
  // Get git commit hash
  const commit = execSync('git rev-parse --short HEAD').toString().trim();
  
  // Get package version
  const packageJsonPath = path.resolve(__dirname, '../package.json');
  const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf-8'));
  const version = packageJson.version;

  const content = `export const VERSION = {
  version: '${version}',
  commit: '${commit}',
  timestamp: '${new Date().toISOString()}',
};
`;

  const outputPath = path.resolve(__dirname, '../src/version.ts');
  fs.writeFileSync(outputPath, content);
  
  console.log(`Generated version.ts: ${version}+${commit}`);
} catch (error) {
  console.error('Failed to generate version file:', error);
  // Fallback for dev environment without git or if failed
  const content = `export const VERSION = {
  version: '0.0.0',
  commit: 'dev',
  timestamp: '${new Date().toISOString()}',
};
`;
  const outputPath = path.resolve(__dirname, '../src/version.ts');
  fs.writeFileSync(outputPath, content);
}
