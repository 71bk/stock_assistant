import {
  formatOcrMessage,
  formatOcrMessages,
  isInformationalOcrWarning,
} from './ocrMessages';

describe('OCR message localization', () => {
  it('translates validator codes', () => {
    expect(formatOcrMessage('SETTLEMENT_BEFORE_TRADE')).toBe('交割日不可早於成交日');
    expect(formatOcrMessage('INSTRUMENT_NOT_FOUND')).toBe('找不到對應標的，請確認股票代號');
    expect(formatOcrMessage('INVALID_QUANTITY', 'error')).toBe('交易數量必須大於零');
  });

  it('translates fallback parser warnings', () => {
    expect(
      formatOcrMessage('Vertical text-layer fallback parse; please review before confirming.'),
    ).toBe('系統使用 PDF 文字層辨識，請確認交易內容');
    expect(
      formatOcrMessage('Side inferred as SELL from transaction tax.'),
    ).toBe('系統依交易稅推定此筆為賣出，請確認買賣方向');
  });

  it('translates dynamic statement total mismatches and preserves values', () => {
    expect(
      formatOcrMessage('Parsed amount total 9880 does not match statement total 9900.'),
    ).toBe('成交金額合計不一致：解析結果 9880，對帳單 9900');
    expect(
      formatOcrMessage('Parsed tax total 58 does not match statement total 60.'),
    ).toBe('交易稅合計不一致：解析結果 58，對帳單 60');
  });

  it('keeps Chinese messages and hides unknown English text', () => {
    expect(formatOcrMessage('PDF 密碼錯誤，請重新輸入', 'error')).toBe(
      'PDF 密碼錯誤，請重新輸入',
    );
    expect(formatOcrMessage('Unexpected parser implementation detail')).toBe(
      '辨識結果需要人工確認，請檢查交易內容',
    );
    expect(formatOcrMessage('Unexpected parser implementation detail', 'error')).toBe(
      '處理失敗，請稍後再試或重新上傳文件',
    );
  });

  it('keeps unknown codes available for troubleshooting', () => {
    expect(formatOcrMessage('NEW_WARNING_CODE')).toBe(
      '辨識資料需要人工確認（代碼：NEW_WARNING_CODE）',
    );
    expect(formatOcrMessage('NEW_ERROR_CODE', 'error')).toBe(
      '資料驗證失敗（代碼：NEW_ERROR_CODE）',
    );
  });

  it('deduplicates translated messages and classifies informational warnings', () => {
    expect(
      formatOcrMessages([
        'OCR_VERTICAL_TEXT_LAYER_FALLBACK',
        'Vertical text-layer fallback parse; please review before confirming.',
      ]),
    ).toEqual(['系統使用 PDF 文字層辨識，請確認交易內容']);
    expect(isInformationalOcrWarning('OCR_VERTICAL_TEXT_LAYER_FALLBACK')).toBe(true);
    expect(
      isInformationalOcrWarning(
        'Vertical text-layer fallback parse; please review before confirming.',
      ),
    ).toBe(true);
    expect(isInformationalOcrWarning('Side inferred as SELL from transaction tax.')).toBe(false);
  });
});
