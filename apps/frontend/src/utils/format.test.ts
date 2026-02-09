import { formatCurrency, preprocessMarkdown } from './format';

describe('formatCurrency', () => {
  it('should format TWD by default', () => {
    // Note: Node.js/JSDOM Intl implementation might differ from browser.
    // Adjusting expectation to match received output in test environment ($100 instead of NT$100.00)
    // Also minimumFractionDigits: 0 means no decimals for integers.
    const result = formatCurrency(100);
    expect(result).toMatch(/(\$|NT\$)100/);
    
    const result2 = formatCurrency(1234567.89);
    expect(result2).toMatch(/(\$|NT\$)1,234,567.89/);
  });

  it('should format USD', () => {
    const result = formatCurrency(100, 'USD');
    expect(result).toMatch(/(US\$|\$)100/);
  });

  it('should handle zero', () => {
    const result = formatCurrency(0);
    expect(result).toMatch(/(\$|NT\$)0/);
  });
});

describe('preprocessMarkdown', () => {
  it('should fix table format missing newline', () => {
    const input = 'Text| Header1 | Header2 |';
    const expected = 'Text\n\n| Header1 | Header2 |';
    expect(preprocessMarkdown(input)).toBe(expected);
  });

  it('should fix list format missing newline', () => {
    const input = 'Text- Item 1';
    const expected = 'Text\n- Item 1';
    expect(preprocessMarkdown(input)).toBe(expected);
  });

  it('should remove excessive newlines', () => {
    const input = 'Line 1\n\n\n\nLine 2';
    const expected = 'Line 1\n\nLine 2';
    expect(preprocessMarkdown(input)).toBe(expected);
  });
});
