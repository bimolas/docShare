/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}"
  ],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        accent: {
          DEFAULT: 'var(--color-primary)',
          foreground: 'hsl(var(--accent-foreground))',
          hover: 'var(--color-primary-hover)',
          active: 'var(--color-primary-active)',
          subtle: 'var(--color-primary-subtle)',
          border: 'var(--color-primary-border)',
        },
        primary: {
          DEFAULT: 'var(--color-primary)',
          foreground: 'hsl(var(--primary-foreground))',
          hover: 'var(--color-primary-hover)',
          active: 'var(--color-primary-active)',
          subtle: 'var(--color-primary-subtle)',
          border: 'var(--color-primary-border)',
        },
        surface: {
          DEFAULT: 'var(--color-surface)',
          bg: 'var(--color-bg)',
          elevated: 'var(--color-surface-elevated)',
          hover: 'var(--color-bg-hover)',
        },
        sdtext: {
          primary: 'var(--color-text-primary)',
          secondary: 'var(--color-text-secondary)',
          muted: 'var(--color-text-muted)',
          disabled: 'var(--color-text-disabled)',
        },
        sdborder: {
          DEFAULT: 'var(--color-border)',
          subtle: 'var(--color-border-subtle)',
          focus: 'var(--color-border-focus)',
        },
        status: {
          success: 'var(--color-success)',
          'success-subtle': 'var(--color-success-subtle)',
          warning: 'var(--color-warning)',
          'warning-subtle': 'var(--color-warning-subtle)',
          danger: 'var(--color-danger)',
          'danger-subtle': 'var(--color-danger-subtle)',
          info: 'var(--color-info)',
          'info-subtle': 'var(--color-info-subtle)',
          secure: 'var(--color-secure)',
        }
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
      },
      boxShadow: {
        'sd-subtle': 'var(--shadow-subtle)',
        'sd-card': 'var(--shadow-card)',
        'sd-floating': 'var(--shadow-floating)',
        'sd-modal': 'var(--shadow-modal)',
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
        'sd-sm': 'var(--radius-sm)',
        'sd-md': 'var(--radius-md)',
        'sd-lg': 'var(--radius-lg)',
        'sd-xl': 'var(--radius-xl)',
        'sd-2xl': 'var(--radius-2xl)',
      },
      transitionTimingFunction: {
        'sd-ease': 'cubic-bezier(0.16, 1, 0.3, 1)',
      }
    },
  },
  plugins: [],
}


