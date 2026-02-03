import React, { useState, useMemo } from 'react';
import { Select, Spin, Tag, Empty, Typography } from 'antd';
import { debounce } from 'lodash';
import { stocksApi } from '../../api/stocks.api';
import type { Instrument } from '../../api/stocks.api';
import type { ApiResponse } from '../../types/api';

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
      const res = await stocksApi.search(value);
      const data = (res as unknown as ApiResponse<Instrument[]>).data;
      setOptions(data);
    } catch (e) {
      console.error(e);
      setOptions([]);
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
      placeholder="搜尋代號 (如 AAPL, 2330)"
      filterOption={false}
      onSearch={debounceFetcher}
      onChange={(value) => {
        // Find the full instrument object from options
        const instrument = options.find(i => String(i.instrumentId) === String(value));
        if (instrument) onSelect(instrument);
      }}
      notFoundContent={fetching ? <Spin size="small" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="找不到股票" />}
      style={{ width: '100%', ...style }}
      options={options.map((d) => ({
        value: d.instrumentId,
        label: (
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>
              <Tag color={d.market === 'US' ? 'blue' : 'green'}>{d.market}</Tag>
              <Text strong>{d.ticker}</Text>
              <Text type="secondary" style={{ marginLeft: 8 }}>
                {d.nameZh || d.nameEn}
              </Text>
            </span>
            <Text type="secondary">{d.exchange}</Text>
          </div>
        ),
      }))}
    />
  );
};

