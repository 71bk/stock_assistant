import React, { useState, useMemo } from 'react';
import { Select, Spin, Tag, Empty, Typography } from 'antd';
import { debounce } from 'lodash';
// import { stocksApi } from '../../api/stocks.api';
import type { Instrument } from '../../api/stocks.api';

const { Text } = Typography;

interface InstrumentSearchProps {
  onSelect: (instrument: Instrument) => void;
  style?: React.CSSProperties;
}

export const InstrumentSearch: React.FC<InstrumentSearchProps> = ({ onSelect, style }) => {
  const [fetching, setFetching] = useState(false);
  const [options, setOptions] = useState<Instrument[]>([]);

  // Mock search function
  const fetchInstruments = async (value: string) => {
    if (!value) {
      setOptions([]);
      return;
    }
    setFetching(true);
    try {
      // In real app: const { data } = await stocksApi.search(value);
      
      // Mock Data
      await new Promise(resolve => setTimeout(resolve, 300));
      const mockData: Instrument[] = [
        { id: '1', symbol: 'AAPL', exchange: 'NASDAQ', market: 'US', name: 'Apple Inc.', type: 'STOCK', currency: 'USD' },
        { id: '2', symbol: 'TSLA', exchange: 'NASDAQ', market: 'US', name: 'Tesla Inc.', type: 'STOCK', currency: 'USD' },
        { id: '3', symbol: '2330', exchange: 'TWSE', market: 'TW', name: '台積電', type: 'STOCK', currency: 'TWD' },
        { id: '4', symbol: '0050', exchange: 'TWSE', market: 'TW', name: '元大台灣50', type: 'ETF', currency: 'TWD' },
        { id: '5', symbol: 'NVDA', exchange: 'NASDAQ', market: 'US', name: 'NVIDIA Corp', type: 'STOCK', currency: 'USD' },
        { id: '6', symbol: 'MSFT', exchange: 'NASDAQ', market: 'US', name: 'Microsoft', type: 'STOCK', currency: 'USD' },
      ].filter(i => i.symbol.toLowerCase().includes(value.toLowerCase()) || i.name.includes(value));

      setOptions(mockData);
    } finally {
      setFetching(false);
    }
  };

  const debounceFetcher = useMemo(() => {
    const loadOptions = (value: string) => {
      fetchInstruments(value);
    };
    return debounce(loadOptions, 600);
  }, []);

  return (
    <Select
      showSearch
      placeholder="Search symbol (e.g. AAPL, 2330)"
      filterOption={false}
      onSearch={debounceFetcher}
      onChange={(value) => {
        // Find the full instrument object from options
        const instrument = options.find(i => i.id === value);
        if (instrument) onSelect(instrument);
      }}
      notFoundContent={fetching ? <Spin size="small" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No stocks found" />}
      style={{ width: '100%', ...style }}
      options={options.map((d) => ({
        value: d.id,
        label: (
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>
              <Tag color={d.market === 'US' ? 'blue' : 'green'}>{d.market}</Tag>
              <Text strong>{d.symbol}</Text> 
              <Text type="secondary" style={{ marginLeft: 8 }}>{d.name}</Text>
            </span>
            <Text type="secondary">{d.exchange}</Text>
          </div>
        ),
      }))}
    />
  );
};

