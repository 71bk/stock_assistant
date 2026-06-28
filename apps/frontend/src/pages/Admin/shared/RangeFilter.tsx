import React from 'react';
import { DatePicker, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import { useAdminRangeStore } from '../../../stores/adminRange.store';

const { RangePicker } = DatePicker;
const { Text } = Typography;

/** Shared "統計期間" date range picker used at the top of every analytics page. */
export const RangeFilter: React.FC = () => {
  const range = useAdminRangeStore((s) => s.range);
  const setRange = useAdminRangeStore((s) => s.setRange);

  return (
    <Space wrap>
      <Text strong>統計期間</Text>
      <RangePicker
        value={range}
        allowClear={false}
        presets={[
          { label: '今日', value: [dayjs(), dayjs()] },
          { label: '本月', value: [dayjs().startOf('month'), dayjs()] },
          { label: '最近 7 天', value: [dayjs().subtract(6, 'day'), dayjs()] },
          { label: '最近 30 天', value: [dayjs().subtract(29, 'day'), dayjs()] },
        ]}
        disabledDate={(current) =>
          current.isAfter(dayjs(), 'day')
          || current.isBefore(dayjs().subtract(89, 'day'), 'day')
        }
        onChange={(value) => {
          if (value?.[0] && value?.[1]) {
            setRange([value[0], value[1]]);
          }
        }}
      />
    </Space>
  );
};

export default RangeFilter;
