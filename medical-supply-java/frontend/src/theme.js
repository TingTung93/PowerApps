import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: {
    primary: { main: '#4f46a5', dark: '#37317d', light: '#7775c6' },
    secondary: { main: '#0f766e' },
    error: { main: '#b91c1c' },
    warning: { main: '#d97706' },
    success: { main: '#15803d' },
    background: { default: '#f4f6fa', paper: '#ffffff' }
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: 'Roboto, Segoe UI, Arial, sans-serif',
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 650 }
  },
  components: {
    MuiCard: { styleOverrides: { root: { border: '1px solid #e4e7ec', boxShadow: '0 1px 3px rgba(16,24,40,.06)' } } },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } } },
    MuiTableHead: { styleOverrides: { root: { backgroundColor: '#f8fafc' } } },
    MuiTableCell: { styleOverrides: { head: { fontWeight: 700, color: '#475467' } } }
  }
})
