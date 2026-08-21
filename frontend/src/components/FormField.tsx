import type { ReactNode } from 'react';

interface FormFieldProps {
  label: string;
  htmlFor: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: ReactNode;
}

export function FormField({
  label,
  htmlFor,
  required,
  hint,
  error,
  children,
}: FormFieldProps) {
  return (
    <div className={error ? 'form-field form-field-invalid' : 'form-field'}>
      <label htmlFor={htmlFor}>
        {label}
        {required && (
          <span className="required-mark" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </label>
      {children}
      {hint && !error && <small className="form-hint">{hint}</small>}
      {error && (
        <small className="form-error" role="alert">
          {error}
        </small>
      )}
    </div>
  );
}
