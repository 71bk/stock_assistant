import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';

// With globals: true, afterEach is available globally
// @ts-expect-error - afterEach is global
afterEach(() => {
  cleanup();
});
