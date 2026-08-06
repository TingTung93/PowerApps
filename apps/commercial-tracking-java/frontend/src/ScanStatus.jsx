import React from 'react'
import { Box, Button, Card, CardContent, Chip, CircularProgress, Stack, Typography } from '@mui/material'
import QrCodeScannerRounded from '@mui/icons-material/QrCodeScannerRounded'
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import WarningAmberRounded from '@mui/icons-material/WarningAmberRounded'
import ErrorOutlineRounded from '@mui/icons-material/ErrorOutlineRounded'
import { formatDate } from './format'

// The scan result is the screen operators watch, so it gets a purpose-built, glanceable panel:
// a state-colored icon disc, a large heading, and the tracking identity in tabular monospace.
export const SCAN_STATES = {
  ready: { tint: '#e8f2fa', fg: '#0e6ba8', ring: '#cfe4f5', icon: 'scanner' },
  capturing: { tint: '#e8f2fa', fg: '#0e6ba8', ring: '#cfe4f5', icon: 'scanner' },
  saving: { tint: '#e8f2fa', fg: '#0e6ba8', ring: '#cfe4f5', icon: 'spinner' },
  success: { tint: '#e7f4ec', fg: '#137a44', ring: '#bfe3cd', icon: 'success' },
  review: { tint: '#faf0dd', fg: '#8a5300', ring: '#ecd9b0', icon: 'warning' },
  error: { tint: '#fcecea', fg: '#b42318', ring: '#f2c9c4', icon: 'error' }
}

export function ScanStatus({ result, location }) {
  const s = SCAN_STATES[result.state] || SCAN_STATES.ready
  const icon = s.icon === 'spinner' ? <CircularProgress size={26} sx={{ color: s.fg }} />
    : s.icon === 'success' ? <CheckCircleRounded />
    : s.icon === 'warning' ? <WarningAmberRounded />
    : s.icon === 'error' ? <ErrorOutlineRounded />
    : <QrCodeScannerRounded />
  const meta = [result.occurredUtc && `${location} · ${formatDate(result.occurredUtc)}`, result.trackingNumber && `Recipient: ${result.recipient || 'Unassigned'}`].filter(Boolean).join('  ·  ')
  return <Card role="status" aria-live={result.state === 'error' ? 'assertive' : 'polite'} sx={{ mb: 2, backgroundColor: s.tint, borderColor: s.ring, boxShadow: 'none' }}>
    <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', py: 2.5, minHeight: 92 }}>
      <Box aria-hidden sx={{ flexShrink: 0, width: 52, height: 52, borderRadius: '50%', display: 'grid', placeItems: 'center', backgroundColor: '#fff', border: `1px solid ${s.ring}`, color: s.fg, '& svg': { fontSize: 28 } }}>{icon}</Box>
      <Box sx={{ minWidth: 0, flexGrow: 1 }}>
        <Typography variant="h5" sx={{ color: s.fg }}>{result.heading}</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.25 }}>{result.message}</Typography>
        {result.trackingNumber && <Stack direction="row" alignItems="center" spacing={1} sx={{ mt: 1.25 }} flexWrap="wrap" useFlexGap>
          {result.carrier && <Chip size="small" label={result.carrier} sx={{ backgroundColor: '#fff', borderColor: s.ring }} variant="outlined" />}
          <Typography className="ct-mono" sx={{ fontWeight: 700, fontSize: 18, overflowWrap: 'anywhere' }}>{result.trackingNumber}</Typography>
        </Stack>}
        {meta && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>{meta}</Typography>}
        {(result.trackingNumber || result.state === 'error') && <Button size="small" sx={{ mt: 1, ml: -1 }} onClick={() => navigator.clipboard?.writeText(result.trackingNumber || result.message)}>Copy {result.trackingNumber ? 'tracking number' : 'error details'}</Button>}
      </Box>
    </CardContent>
  </Card>
}
