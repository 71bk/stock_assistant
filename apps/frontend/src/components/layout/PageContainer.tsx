import React from 'react';

interface PageContainerProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
  className?: string;
}

/**
 * A wrapper for pages that need scrolling.
 * Since MainLayout now has overflow: hidden, normal pages must wrap their content
 * in this container to enable vertical scrolling.
 */
export const PageContainer: React.FC<PageContainerProps> = ({ children, style, className }) => {
  return (
    <div
      className={className}
      style={{
        height: '100%',
        overflowY: 'auto',
        paddingRight: 8, // Avoid content hiding behind scrollbar
        ...style,
      }}
    >
      {children}
    </div>
  );
};
