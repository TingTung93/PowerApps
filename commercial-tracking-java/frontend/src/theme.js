import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#0b5cab', dark: '#073b70', light: '#d9ebfb' },
    secondary: { main: '#087f5b' },
    background: { default: '#f3f6f9', paper: '#ffffff' },
    success: { main: '#14804a' },
    warning: { main: '#a15c00' },
    error: { main: '#b42318' },
    text: { primary: '#172b3a', secondary: '#526777' }
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"Roboto", "Segoe UI", Arial, sans-serif',
    h4: { fontWeight: 700, letterSpacing: '-0.02em' },
    h6: { fontWeight: 700 }
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { minWidth: 320 },
        '@media (prefers-reduced-motion: reduce)': {
          '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' }
        }
      }
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { textTransform: 'none', fontWeight: 600, minHeight: 40 } }
    },
    MuiCard: {
      styleOverrides: { root: { border: '1px solid #dfe7ee', boxShadow: '0 2px 8px rgba(20,47,68,.05)' } }
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          margin: '2px 10px',
          borderRadius: 8,
          minHeight: 44,
          '&.Mui-selected': { backgroundColor: '#e7f1fa', color: '#073b70' },
          '&.Mui-selected .MuiListItemIcon-root': { color: '#0b5cab' }
        }
      }
    },
    MuiListItemIcon: { styleOverrides: { root: { minWidth: 40, color: '#526777' } } },
    MuiOutlinedInput: { styleOverrides: { root: { '&.Mui-focused': { outline: '2px solid rgba(11,92,171,.18)' } } } },
    MuiTableCell: {
      styleOverrides: { head: { fontWeight: 700, color: '#314b5f', background: '#f6f9fb' } }
    }
  }
})
