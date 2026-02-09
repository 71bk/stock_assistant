import { render, screen } from '@testing-library/react';
import { Loading } from './Loading';

describe('Loading', () => {
  it('renders with default tip', () => {
    render(<Loading />);
    expect(screen.getByText('加載中...')).toBeInTheDocument();
  });

  it('renders with custom tip', () => {
    render(<Loading tip="Processing..." />);
    expect(screen.getByText('Processing...')).toBeInTheDocument();
  });
});
