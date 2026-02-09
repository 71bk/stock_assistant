import React, { Suspense } from 'react';
import { Loading } from './Loading';

interface LazyWrapperProps {
  children: React.ReactNode;
}

export const LazyWrapper: React.FC<LazyWrapperProps> = ({ children }) => (
  <Suspense fallback={<Loading />}>{children}</Suspense>
);
