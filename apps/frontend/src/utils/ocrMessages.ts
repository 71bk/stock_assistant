export type OcrMessageKind = 'warning' | 'error';

const OCR_MESSAGE_TRANSLATIONS: Record<string, string> = {
  OCR_VERTICAL_TEXT_LAYER_FALLBACK: '系統使用 PDF 文字層辨識，請確認交易內容',
  SETTLEMENT_BEFORE_TRADE: '交割日不可早於成交日',
  CURRENCY_MISMATCH: '交易幣別與標的幣別不一致，請確認幣別',
  TRADE_DATE_IN_FUTURE: '成交日為未來日期，請確認日期',
  NEGATIVE_FEE: '手續費為負數，請確認金額',
  NEGATIVE_TAX: '交易稅為負數，請確認金額',
  ROW_HASH_MISSING: '無法建立交易識別資訊，請確認交易內容',
  DUPLICATE_IN_STATEMENT: '此文件內有內容相同的交易',
  DUPLICATE_PORTFOLIO_TRADE: '投資組合中可能已有相同交易',
  INSTRUMENT_NOT_FOUND: '找不到對應標的，請確認股票代號',
  AMBIGUOUS_TICKER: '股票代號對應到多個標的，請人工確認',
  MISSING_TRADE_DATE: '缺少成交日',
  MISSING_SIDE: '缺少買賣方向',
  INVALID_SIDE: '買賣方向無效',
  INVALID_QUANTITY: '交易數量必須大於零',
  INVALID_PRICE: '交易價格必須大於零',
  MISSING_CURRENCY: '缺少交易幣別',
  DRAFT_MISSING: '找不到交易草稿',
  'Broker statement table fallback was used; please review before confirming.':
    '系統使用券商表格格式辨識，請確認交易內容',
  'Statement totals matched parsed trades.': '解析結果與對帳單合計一致',
  'Markdown table fallback parse; please review before confirming.':
    '系統使用表格格式辨識，請確認交易內容',
  'Side inferred as SELL from transaction tax.':
    '系統依交易稅推定此筆為賣出，請確認買賣方向',
  'Vertical text-layer fallback parse; please review before confirming.':
    '系統使用 PDF 文字層辨識，請確認交易內容',
  'Rule-based fallback parse; please review before confirming.':
    '系統使用規則式辨識，請確認交易內容',
  'LLM JSON parse failed; broker statement table fallback was used.':
    'AI 回傳格式異常，已改用券商表格格式辨識，請確認交易內容',
  'LLM JSON parse failed; rule-based broker statement fallback was used.':
    'AI 回傳格式異常，已改用規則式辨識，請確認交易內容',
  'LLM parser returned no trades; broker statement table fallback was used.':
    'AI 未辨識出交易，已改用券商表格格式辨識，請確認交易內容',
  'LLM parser returned no trades; rule-based broker statement fallback was used.':
    'AI 未辨識出交易，已改用規則式辨識，請確認交易內容',
  'File content is empty': '上傳檔案內容為空',
  'File not found': '找不到上傳檔案，請重新上傳',
  'Empty OCR response': 'OCR 服務未回傳結果，請重新辨識',
  'Statement not in DRAFT': '文件狀態不允許解析，請重新上傳',
  'AI Worker OCR timeout': 'OCR 服務處理逾時，請稍後重新辨識',
  'Draft missing instrumentId': '缺少股票代號',
  'Draft missing tradeDate': '缺少成交日',
  'Draft missing side': '缺少買賣方向',
  'Draft quantity must be > 0': '交易數量必須大於零',
  'Draft price must be > 0': '交易價格必須大於零',
  'Draft missing currency': '缺少交易幣別',
  'Amount cannot be negative': '金額不可為負數',
};

const INFORMATIONAL_WARNING_MESSAGES = new Set([
  'OCR_VERTICAL_TEXT_LAYER_FALLBACK',
  'Broker statement table fallback was used; please review before confirming.',
  'Statement totals matched parsed trades.',
  'Markdown table fallback parse; please review before confirming.',
  'Vertical text-layer fallback parse; please review before confirming.',
  'Rule-based fallback parse; please review before confirming.',
]);

const TOTAL_MISMATCH_PATTERNS: Array<{
  pattern: RegExp;
  label: string;
}> = [
  {
    pattern: /^Parsed amount total (.+) does not match statement total (.+)\.$/,
    label: '成交金額',
  },
  {
    pattern: /^Parsed fee total (.+) does not match statement total (.+)\.$/,
    label: '手續費',
  },
  {
    pattern: /^Parsed tax total (.+) does not match statement total (.+)\.$/,
    label: '交易稅',
  },
];

const HAS_CHINESE = /[\u3400-\u9fff]/;
const ERROR_CODE = /^[A-Z][A-Z0-9_]+$/;

export function formatOcrMessage(
  message: string | null | undefined,
  kind: OcrMessageKind = 'warning',
): string {
  const normalized = message?.trim();
  if (!normalized) {
    return kind === 'error' ? '處理失敗，請稍後再試' : '辨識結果需要人工確認';
  }

  const translated = OCR_MESSAGE_TRANSLATIONS[normalized];
  if (translated) {
    return translated;
  }

  for (const { pattern, label } of TOTAL_MISMATCH_PATTERNS) {
    const match = normalized.match(pattern);
    if (match) {
      return `${label}合計不一致：解析結果 ${match[1]}，對帳單 ${match[2]}`;
    }
  }

  if (normalized.startsWith('AI Worker OCR failed')) {
    return 'OCR 服務處理失敗，請稍後重新辨識';
  }

  if (HAS_CHINESE.test(normalized)) {
    return normalized;
  }

  if (ERROR_CODE.test(normalized)) {
    const prefix = kind === 'error' ? '資料驗證失敗' : '辨識資料需要人工確認';
    return `${prefix}（代碼：${normalized}）`;
  }

  return kind === 'error'
    ? '處理失敗，請稍後再試或重新上傳文件'
    : '辨識結果需要人工確認，請檢查交易內容';
}

export function formatOcrMessages(
  messages: string[] | null | undefined,
  kind: OcrMessageKind = 'warning',
): string[] {
  return [...new Set((messages ?? []).map((message) => formatOcrMessage(message, kind)))];
}

export function isInformationalOcrWarning(message: string): boolean {
  return INFORMATIONAL_WARNING_MESSAGES.has(message.trim());
}
