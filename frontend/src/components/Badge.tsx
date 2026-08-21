import type { ReactNode } from 'react';

interface BadgeProps {
  value: string;
  children?: ReactNode;
}

export function Badge({ value, children }: BadgeProps) {
  return (
    <span className={`badge badge-${value.toLowerCase().replace('_', '-')}`}>
      {children ?? value}
    </span>
  );
}
