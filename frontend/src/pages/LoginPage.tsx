import { zodResolver } from '@hookform/resolvers/zod';
import { AlertCircle, Eye, EyeOff, Loader2, LogIn, Lock, Stethoscope, User } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError, fetchMe } from '../shared/api/client';
import { useAuth } from '../shared/auth/AuthProvider';
import { landingRouteFor } from '../shared/auth/landingRoute';
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
  const [showPassword, setShowPassword] = useState(false);
  const [capsLockOn, setCapsLockOn] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const username = watch('username');
  const password = watch('password');

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const accessToken = await login(values.username, values.password);
      const me = accessToken ? await fetchMe(accessToken) : null;
      const roleCodes = me?.scopes?.map((scope) => scope.roleCode).filter((code): code is string => Boolean(code)) ?? [];
      const permissions = me?.permissions ?? [];
      navigate(landingRouteFor(roleCodes, permissions), { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFormError('Invalid username or password.');
      } else {
        setFormError('Something went wrong. Try again.');
      }
    }
  });

  const handleCapsLock = (event: React.KeyboardEvent<HTMLInputElement>) => {
    setCapsLockOn(event.getModifierState('CapsLock'));
  };

  return (
    <main className="login-page"><div className="login-layout">
      <div className="login-hero">
        <div className="login-hero-pattern" aria-hidden="true" />
        <div className="login-hero-content">
          <div className="login-mark">
            <Stethoscope size={22} aria-hidden="true" />
          </div>
          <div className="login-hero-text">
            <span className="login-product-name">ADAGS</span>
            <span className="login-tagline">Hospital management system</span>
          </div>
        </div>
        <div className="login-introduction">
          <span className="login-eyebrow">Hospital management system</span>
          <h2>Care starts with<br />a connected team.</h2>
          <p>Your workspace for patient care, hospital operations, and the next step in every visit.</p>
        </div>
        <div className="login-capabilities"><span>Patient care</span><span>Clinical services</span><span>Hospital operations</span></div>
        <p className="login-trust"><Lock size={14} aria-hidden="true" /> Authorized staff access</p>
      </div>

      <div className="login-form-panel">
        <div className="login-mobile-header">
          <div className="login-mark login-mark-sm">
            <Stethoscope size={18} aria-hidden="true" />
          </div>
          <span className="login-product-name">ADAGS</span>
        </div>

        <form className="login-card" onSubmit={onSubmit} noValidate>
          <h1>Welcome back</h1>
          <p className="login-subtitle">Use your staff account to access your workspace.</p>

          {formError && (
            <div className="form-banner" role="alert">
              <AlertCircle size={18} aria-hidden="true" />
              <span>{formError}</span>
            </div>
          )}

          <div className="field">
            <label htmlFor="username">Username</label>
            <div className="input-wrap">
              <User className="input-icon" size={18} aria-hidden="true" />
              <input
                id="username"
                type="text"
                autoComplete="username"
                aria-invalid={errors.username ? true : undefined}
                aria-describedby={errors.username ? 'username-error' : undefined}
                {...register('username')}
              />
            </div>
            {errors.username && (
              <span id="username-error" className="field-error">
                {errors.username.message}
              </span>
            )}
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <div className="input-wrap">
              <Lock className="input-icon" size={18} aria-hidden="true" />
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                aria-invalid={errors.password ? true : undefined}
                aria-describedby={errors.password ? 'password-error' : capsLockOn ? 'password-capslock' : undefined}
                {...register('password')}
                onKeyUp={handleCapsLock}
              />
              <button
                type="button"
                className="input-toggle"
                onClick={() => setShowPassword((shown) => !shown)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
              </button>
            </div>
            {capsLockOn && !errors.password && (
              <span id="password-capslock" className="field-hint">
                Caps lock is on
              </span>
            )}
            {errors.password && (
              <span id="password-error" className="field-error">
                {errors.password.message}
              </span>
            )}
          </div>

          <button type="submit" className="login-submit" disabled={isSubmitting || !username || !password}>
            {isSubmitting ? (
              <Loader2 className="spin" size={18} aria-hidden="true" />
            ) : (
              <LogIn size={18} aria-hidden="true" />
            )}
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>

          <p className="login-footer">
            ADAGS v1.0.0 · Trouble signing in? Contact your administrator
          </p>
        </form>
      </div>
    </div></main>
  );
}
