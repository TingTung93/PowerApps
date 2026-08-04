import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: {
    primary: { main: '#6264a7' },
    error: { main: '#b91c1c' },
    warning: { main: '#d97706' },
    background: { default: '#f5f5f5' }
  },
  shape: { borderRadius: 8 },
  typography: { fontFamily: 'Roboto, Segoe UI, Arial, sans-serif' }
})
