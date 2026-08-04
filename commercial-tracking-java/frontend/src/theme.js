import { createTheme } from '@mui/material/styles'

// Design tokens for Commercial Tracking — an institutional chain-of-custody tool.
// The identity is a trustworthy, precise blue on cool-grey surfaces; the signature is
// precision applied to the data that matters here — tracking identifiers set in a
// consistent tabular monospace, and a crisp, semantic status system.

// Cool-grey neutral ramp: text, dividers, and surfaces read as one calm system.
const ink = {
  900: '#0f2433', // primary text — deep slate, not pure black
  700: '#33485a',
  600: '#4c6274', // secondary text
  500: '#67798a',
  300: '#c9d5df',
  200: '#dde6ee', // borders
  100: '#e8eef4', // dividers / hairlines
  50: '#f4f7fa' // app background
}

// Institutional blue, resolved into surface tints as well as the strong marks.
const blue = {
  main: '#0b5cab',
  dark: '#073b70', // headings and pressed states
  mid: '#0e6ba8',
  light: '#e6f0fa', // selected / hover surface tint
  soft: '#f2f7fc' // faint header wash
}

// Status colors, each with a strong mark and a soft surface, tuned for AA text contrast.
const status = {
  success: { main: '#137a44', soft: '#e7f4ec' },
  warning: { main: '#8a5300', soft: '#faf0dd' },
  error: { main: '#b42318', soft: '#fcecea' },
  info: { main: '#0e6ba8', soft: '#e8f2fa' }
}

// A single monospace stack for every tracking number, checksum, and identifier.
export const MONO = '"Roboto Mono", "SFMono-Regular", "Cascadia Mono", "Consolas", ui-monospace, monospace'

const cardShadow = '0 1px 2px rgba(15,36,51,.05), 0 2px 10px rgba(15,36,51,.06)'
const focusRing = '0 0 0 3px rgba(11,92,171,.28)'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: blue.main, dark: blue.dark, light: blue.light, contrastText: '#ffffff' },
    secondary: { main: '#0f766e', contrastText: '#ffffff' },
    background: { default: ink[50], paper: '#ffffff' },
    success: { main: status.success.main },
    warning: { main: status.warning.main },
    error: { main: status.error.main },
    info: { main: status.info.main },
    text: { primary: ink[900], secondary: ink[600] },
    divider: ink[100]
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: '"Roboto", "Segoe UI", Arial, sans-serif',
    // A deliberate scale: tight, confident headings; calm, legible body and data.
    h1: { fontWeight: 700, fontSize: '2rem', lineHeight: 1.2, letterSpacing: '-0.02em' },
    h2: { fontWeight: 700, fontSize: '1.625rem', lineHeight: 1.22, letterSpacing: '-0.02em' },
    h3: { fontWeight: 700, fontSize: '1.375rem', lineHeight: 1.25, letterSpacing: '-0.015em' },
    h4: { fontWeight: 700, fontSize: '1.5rem', lineHeight: 1.25, letterSpacing: '-0.02em' },
    h5: { fontWeight: 700, fontSize: '1.15rem', lineHeight: 1.3, letterSpacing: '-0.01em' },
    h6: { fontWeight: 700, fontSize: '1rem', lineHeight: 1.35, letterSpacing: '-0.005em' },
    subtitle1: { fontWeight: 600, fontSize: '0.95rem', lineHeight: 1.45 },
    subtitle2: { fontWeight: 600, fontSize: '0.8125rem', lineHeight: 1.4 },
    body1: { fontSize: '0.9375rem', lineHeight: 1.55 },
    body2: { fontSize: '0.8125rem', lineHeight: 1.5 },
    button: { fontWeight: 600, fontSize: '0.875rem', letterSpacing: '0.01em' },
    overline: { fontWeight: 700, fontSize: '0.6875rem', letterSpacing: '0.09em', lineHeight: 1.6 },
    caption: { fontSize: '0.75rem', lineHeight: 1.45 }
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        ':root': { '--ct-mono': MONO, '--ct-focus-ring': focusRing },
        body: {
          minWidth: 320,
          backgroundColor: ink[50],
          WebkitFontSmoothing: 'antialiased',
          MozOsxFontSmoothing: 'grayscale'
        },
        // Tracking numbers, checksums, and IDs share one precise, tabular treatment.
        '.ct-mono': { fontFamily: 'var(--ct-mono)', fontFeatureSettings: '"tnum" 1', letterSpacing: '0.01em' },
        // Keyboard focus is always visible, never a mystery.
        ':focus-visible': { outline: 'none', boxShadow: focusRing, borderRadius: '6px' },
        // A calm heartbeat for the "listening for scanner" indicator (stilled under reduced motion below).
        '@keyframes ctPulse': { '0%, 100%': { opacity: 1 }, '50%': { opacity: 0.3 } },
        '@media (prefers-reduced-motion: reduce)': {
          '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' }
        }
      }
    },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    MuiAppBar: {
      styleOverrides: {
        root: { backgroundColor: '#ffffff', color: ink[900], borderBottom: `1px solid ${ink[200]}`, boxShadow: 'none' }
      }
    },
    MuiDrawer: {
      styleOverrides: { paper: { backgroundColor: '#ffffff', borderColor: ink[200] } }
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { textTransform: 'none', fontWeight: 600, borderRadius: 8, minHeight: 40, paddingInline: 16 },
        sizeSmall: { minHeight: 32, paddingInline: 12 },
        containedPrimary: { '&:hover': { backgroundColor: blue.dark } }
      }
    },
    MuiCard: {
      styleOverrides: { root: { border: `1px solid ${ink[200]}`, borderRadius: 12, boxShadow: cardShadow } }
    },
    MuiCardContent: { styleOverrides: { root: { '&:last-child': { paddingBottom: 20 } } } },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, borderRadius: 8, height: 26 },
        label: { paddingInline: 10 },
        outlined: { borderColor: ink[200] }
      }
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          margin: '2px 10px',
          borderRadius: 8,
          minHeight: 44,
          '&:hover': { backgroundColor: blue.soft },
          '&.Mui-selected': { backgroundColor: blue.light, color: blue.dark, fontWeight: 600 },
          '&.Mui-selected:hover': { backgroundColor: blue.light },
          '&.Mui-selected .MuiListItemIcon-root': { color: blue.main }
        }
      }
    },
    MuiListItemText: { styleOverrides: { primary: { fontSize: '0.9rem', fontWeight: 500 } } },
    MuiListItemIcon: { styleOverrides: { root: { minWidth: 38, color: ink[500] } } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          backgroundColor: '#ffffff',
          '& .MuiOutlinedInput-notchedOutline': { borderColor: ink[300] },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: ink[500] },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: blue.main, borderWidth: 2 }
        }
      }
    },
    MuiTableCell: {
      styleOverrides: {
        root: { padding: '13px 16px', borderColor: ink[100] },
        head: {
          fontWeight: 700,
          fontSize: '0.75rem',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          color: ink[600],
          backgroundColor: blue.soft,
          borderBottom: `1px solid ${ink[200]}`
        }
      }
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          '&:hover': { backgroundColor: ink[50] },
          '&.Mui-selected': { backgroundColor: blue.light },
          '&.Mui-selected:hover': { backgroundColor: blue.light }
        }
      }
    },
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: 10, alignItems: 'flex-start' },
        standardSuccess: { backgroundColor: status.success.soft, color: '#0c4f2c' },
        standardWarning: { backgroundColor: status.warning.soft, color: '#6a3f00' },
        standardError: { backgroundColor: status.error.soft, color: '#8a1a12' },
        standardInfo: { backgroundColor: status.info.soft, color: '#0a4a74' }
      }
    },
    MuiTabs: { styleOverrides: { indicator: { height: 3, borderRadius: 3 } } },
    MuiTab: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600, minHeight: 48, fontSize: '0.9rem' } } },
    MuiDialog: { styleOverrides: { paper: { borderRadius: 14 } } },
    MuiDialogTitle: { styleOverrides: { root: { fontWeight: 700, fontSize: '1.15rem' } } },
    MuiTooltip: {
      styleOverrides: {
        tooltip: { backgroundColor: ink[900], borderRadius: 6, fontSize: '0.75rem', padding: '6px 10px' },
        arrow: { color: ink[900] }
      }
    },
    MuiDivider: { styleOverrides: { root: { borderColor: ink[100] } } }
  }
})
