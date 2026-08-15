import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../shared/api/client';
import { useAuth } from '../shared/auth/AuthProvider';
import './LoginPage.css';

const loginSchema = z.object({
  username: z.string().min(1, 'Enter your username'),
  password: z.string().min(1, 'Enter your password'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login(values.username, values.password);
      navigate('/admin', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFormError('Incorrect username or password.');
      } else {
        setFormError('Something went wrong. Try again.');
      }
    }
  });

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={onSubmit} noValidate>
        <h1>ADAGS</h1>
        <p className="login-subtitle">Hospital management system</p>

        <label htmlFor="username">Username</label>
        <input id="username" type="text" autoComplete="username" {...register('username')} />
        {errors.username && <span className="field-error">{errors.username.message}</span>}

        <label htmlFor="password">Password</label>
        <input id="password" type="password" autoComplete="current-password" {...register('password')} />
        {errors.password && <span className="field-error">{errors.password.message}</span>}

        {formError && <div className="form-error" role="alert">{formError}</div>}

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
