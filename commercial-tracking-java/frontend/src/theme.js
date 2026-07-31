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
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { textTransform: 'none', fontWeight: 600, minHeight: 40 } }
    },
    MuiCard: {
      styleOverrides: { root: { border: '1px solid #dfe7ee', boxShadow: '0 4px 16px rgba(20,47,68,.06)' } }
    },
    MuiTableCell: {
      styleOverrides: { head: { fontWeight: 700, color: '#314b5f', background: '#f6f9fb' } }
    }
  }
})
