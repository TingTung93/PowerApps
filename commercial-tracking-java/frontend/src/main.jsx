import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Alert, AppBar, Box, Button, Card, CardContent, Chip, CircularProgress,
  Container, CssBaseline, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, FormControl, IconButton, InputLabel, MenuItem, Select, Snackbar,
  Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Tabs, TextField, ThemeProvider, Toolbar, Tooltip, Typography
} from '@mui/material'
import LocalShippingRounded from '@mui/icons-material/LocalShippingRounded'
import QrCodeScannerRounded from '@mui/icons-material/QrCodeScannerRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import PrintRounded from '@mui/icons-material/PrintRounded'
import PersonAddAltRounded from '@mui/icons-material/PersonAddAltRounded'
import BlockRounded from '@mui/icons-material/BlockRounded'
import SyncRounded from '@mui/icons-material/SyncRounded'
import WarningAmberRounded from '@mui/icons-material/WarningAmberRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import FolderRounded from '@mui/icons-material/FolderRounded'
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import { api } from './api'
import { theme } from './theme'

const locations = ['Main Receiving', 'Loading Dock', 'Mailroom', 'Warehouse']

function statusColor(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'WARNING') return 'warning'
  if (status === 'ERROR') return 'error'
  return 'info'
}

function App() {
  const [state, setState] = useState(null)
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState('Inbound')
  const [location, setLocation] = useState(locations[0])
  const [recipient, setRecipient] = useState('')
  const [scan, setScan] = useState('')
  const [tab, setTab] = useState(0)
  const [selected, setSelected] = useState(null)
  const [result, setResult] = useState({ kind: 'INFO', message: 'Ready for the next scan.' })
  const [toast, setToast] = useState('')
  const [dialog, setDialog] = useState(null)
  const scanRef = useRef(null)

  const refresh = useCallback(async (quiet = false) => {
    try {
      if (!quiet) setBusy(true)
      setState(await api.state())
    } catch (error) {
      setResult({ kind: 'ERROR', message: error.message })
    } finally {
      if (!quiet) setBusy(false)
    }
  }, [])

  useEffect(() => {
    refresh()
    const timer = setInterval(() => refresh(true), 15000)
    return () => clearInterval(timer)
  }, [refresh])

  useEffect(() => {
    if (!busy && state?.configured) scanRef.current?.focus()
  }, [busy, state])

  const submit = async event => {
    event.preventDefault()
    if (!scan.trim()) return
    setBusy(true)
    setResult({ kind: 'SAVING', message: 'Saving locally and submitting to the synchronized folder…' })
    try {
      const response = await api.scan({ raw: scan, mode, location, recipient })
      if (response.confirmationRequired) {
        setDialog({ type: 'confirmScan', payload: response, original: { raw: scan, mode, location, recipient } })
        setResult({ kind: 'WARNING', message: 'Confirm the ambiguous barcode before saving.' })
      } else {
        setResult({ kind: response.kind, message: response.message })
        setScan('')
        await refresh(true)
      }
    } catch (error) {
      setResult({ kind: 'ERROR', message: error.message })
    } finally {
      setBusy(false)
    }
  }

  const confirmScan = async () => {
    setBusy(true)
    try {
      const response = await api.scan({ ...dialog.original, confirmed: 'true' })
      setResult({ kind: response.kind, message: response.message })
      setDialog(null)
      setScan('')
      await refresh(true)
    } catch (error) {
      setResult({ kind: 'ERROR', message: error.message })
    } finally { setBusy(false) }
  }

  const configure = async values => {
    setBusy(true)
    try {
      await api.configure(values)
      setDialog(null)
      setToast('Shared test folder configured')
      await refresh()
    } catch (error) {
      setResult({ kind: 'ERROR', message: error.message })
    } finally { setBusy(false) }
  }

  const assign = async name => {
    try {
      await api.assignRecipient({ trackingNumber: selected.trackingNumber, recipient: name })
      setDialog(null); setToast('Recipient assignment submitted'); await refresh()
    } catch (error) { setResult({ kind: 'ERROR', message: error.message }) }
  }

  const voidPackage = async reason => {
    try {
      await api.voidPackage({ trackingNumber: selected.trackingNumber, reason })
      setDialog(null); setToast('Package void submitted'); await refresh()
    } catch (error) { setResult({ kind: 'ERROR', message: error.message }) }
  }

  const createManifest = async () => {
    try {
      const response = await api.manifest({ location })
      setToast(`Manifest created: ${response.fileName}`)
    } catch (error) { setResult({ kind: 'ERROR', message: error.message }) }
  }

  const packages = state?.packages || []
  const session = state?.session || []
  const stats = useMemo(() => ({
    session: session.length,
    active: packages.filter(p => p.status === 'READY_FOR_PICKUP').length,
    released: packages.filter(p => p.status === 'PICKED_UP').length,
    conflicts: (state?.conflicts || []).length
  }), [packages, session, state])

  if (!state) return <Box sx={{ display: 'grid', placeItems: 'center', height: '100vh' }}><CircularProgress /></Box>

  return (
    <Box sx={{ minHeight: '100vh' }}>
      <AppBar position="sticky" elevation={0} sx={{ background: 'linear-gradient(110deg,#073b70,#0b5cab 65%,#087f5b)' }}>
        <Toolbar sx={{ gap: 2 }}>
          <LocalShippingRounded fontSize="large" />
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6">Commercial Tracking</Typography>
            <Typography variant="caption" sx={{ opacity: .82 }}>Portable release candidate · {state.deviceId}</Typography>
          </Box>
          <Chip icon={<SyncRounded />} label={`${state.eventCount} shared events`} sx={{ bgcolor: 'rgba(255,255,255,.16)', color: 'white', '& .MuiChip-icon': { color: 'white' } }} />
          <Tooltip title="Refresh shared events"><IconButton color="inherit" onClick={() => refresh()}><RefreshRounded /></IconButton></Tooltip>
          <Tooltip title="Exit local application"><IconButton color="inherit" onClick={() => api.shutdown().finally(() => window.close())}><LogoutRounded /></IconButton></Tooltip>
        </Toolbar>
      </AppBar>

      <Container maxWidth="xl" sx={{ py: 3 }}>
        {!state.configured && <Alert severity="warning" action={<Button color="inherit" onClick={() => setDialog({ type: 'setup' })}>Configure</Button>}>Select an empty synchronized pilot folder before scanning.</Alert>}

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(0,2fr) minmax(300px,1fr)' }, gap: 2.5, mt: state.configured ? 0 : 2 }}>
          <Card>
            <CardContent sx={{ p: { xs: 2, md: 3 } }}>
              <Stack direction="row" alignItems="center" spacing={1.5} mb={2}>
                <Box sx={{ display: 'grid', placeItems: 'center', width: 46, height: 46, borderRadius: 2, bgcolor: 'primary.light', color: 'primary.dark' }}><QrCodeScannerRounded /></Box>
                <Box>
                  <Typography variant="h6">Package scanner</Typography>
                  <Typography variant="body2" color="text.secondary">Scanner focus returns automatically after every completed action.</Typography>
                </Box>
              </Stack>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} mb={2}>
                <FormControl sx={{ minWidth: 150 }}><InputLabel id="mode-label">Mode</InputLabel><Select id="mode" name="mode" labelId="mode-label" label="Mode" value={mode} onChange={e => setMode(e.target.value)}><MenuItem value="Inbound">Inbound</MenuItem><MenuItem value="Outbound">Outbound</MenuItem></Select></FormControl>
                <FormControl sx={{ minWidth: 210 }} disabled={mode === 'Outbound'}><InputLabel id="location-label">Location</InputLabel><Select id="location" name="location" labelId="location-label" label="Location" value={location} onChange={e => setLocation(e.target.value)}>{locations.map(item => <MenuItem key={item} value={item}>{item}</MenuItem>)}</Select></FormControl>
                <TextField name="recipient" label="Recipient (optional)" value={recipient} onChange={e => setRecipient(e.target.value)} sx={{ flexGrow: 1 }} />
              </Stack>
              <Box component="form" onSubmit={submit}>
                <TextField name="barcode" inputRef={scanRef} fullWidth autoComplete="off" disabled={busy || !state.configured}
                  value={scan} onChange={e => setScan(e.target.value)}
                  label={busy ? 'Processing—wait for result' : 'Scan package or enter tracking number'}
                  placeholder="Scanner input appears here"
                  InputProps={{ sx: { fontFamily: 'monospace', fontSize: 20, minHeight: 62 } }} />
              </Box>
              <Alert severity={statusColor(result.kind)} variant="outlined" sx={{ mt: 2, alignItems: 'center', '& .MuiAlert-message': { fontWeight: 500 } }}
                icon={result.kind === 'SAVING' ? <CircularProgress size={20} /> : undefined}>
                <b>{result.kind}</b> · {result.message}
              </Alert>
            </CardContent>
          </Card>

          <Stack spacing={2}>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5 }}>
              <StatCard icon={<QrCodeScannerRounded />} label="This session" value={stats.session} color="#0b5cab" />
              <StatCard icon={<Inventory2Rounded />} label="Awaiting pickup" value={stats.active} color="#087f5b" />
              <StatCard icon={<LocalShippingRounded />} label="Released" value={stats.released} color="#6b5aa6" />
              <StatCard icon={<WarningAmberRounded />} label="Conflicts" value={stats.conflicts} color={stats.conflicts ? '#b42318' : '#667784'} />
            </Box>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="subtitle2" color="text.secondary">Shared test folder</Typography>
                <Typography variant="body2" sx={{ my: 1, wordBreak: 'break-all' }}>{state.sharedRoot || 'Not configured'}</Typography>
                <Button startIcon={<FolderRounded />} size="small" onClick={() => setDialog({ type: 'setup' })}>Change folder</Button>
              </CardContent>
            </Card>
          </Stack>
        </Box>

        <Card sx={{ mt: 2.5 }}>
          <Box sx={{ px: 2, pt: 1 }}>
            <Tabs value={tab} onChange={(_, value) => setTab(value)}>
              <Tab label={`Current session (${session.length})`} />
              <Tab label={`Package history (${packages.length})`} />
              <Tab label={`Conflicts (${stats.conflicts})`} />
              <Tab label={`Diagnostics (${(state.errors || []).length})`} />
            </Tabs>
          </Box>
          <Divider />
          {tab === 0 && <PackageTable rows={session} onSelect={setSelected} selected={selected} />}
          {tab === 1 && <PackageTable rows={packages} onSelect={setSelected} selected={selected} />}
          {tab === 2 && <MessageList values={state.conflicts} empty="No conflicts detected." />}
          {tab === 3 && <MessageList values={state.errors} empty="No malformed shared events." />}
          {(tab === 0 || tab === 1) && (
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ p: 2, bgcolor: '#fbfcfd', borderTop: '1px solid #e3e9ee' }}>
              <Button startIcon={<PrintRounded />} onClick={createManifest}>Print inbound session manifest</Button>
              <Button startIcon={<PersonAddAltRounded />} disabled={!selected} onClick={() => setDialog({ type: 'recipient' })}>Assign recipient</Button>
              <Button color="error" startIcon={<BlockRounded />} disabled={!selected} onClick={() => setDialog({ type: 'void' })}>Void package</Button>
            </Stack>
          )}
        </Card>
      </Container>

      <ActionDialog dialog={dialog} state={state} onClose={() => setDialog(null)} onConfigure={configure}
        onConfirmScan={confirmScan} onAssign={assign} onVoid={voidPackage} />
      <Snackbar open={!!toast} autoHideDuration={3500} onClose={() => setToast('')} message={toast} />
    </Box>
  )
}

function StatCard({ icon, label, value, color }) {
  return <Card><CardContent sx={{ p: '16px !important' }}><Stack direction="row" alignItems="center" spacing={1.5}><Box sx={{ color }}>{icon}</Box><Box><Typography variant="h5" fontWeight={700}>{value}</Typography><Typography variant="caption" color="text.secondary">{label}</Typography></Box></Stack></CardContent></Card>
}

function PackageTable({ rows, onSelect, selected }) {
  if (!rows.length) return <Box sx={{ p: 6, textAlign: 'center' }}><Inventory2Rounded sx={{ fontSize: 42, color: '#a8b5bf' }} /><Typography color="text.secondary">No packages to display.</Typography></Box>
  return <TableContainer sx={{ maxHeight: 430 }}><Table stickyHeader size="small"><TableHead><TableRow><TableCell>Tracking</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell><TableCell>Recipient</TableCell><TableCell>Status</TableCell><TableCell>Recorded UTC</TableCell><TableCell>Device</TableCell></TableRow></TableHead><TableBody>
    {rows.map((row, index) => <TableRow hover key={`${row.eventId || row.trackingNumber}-${index}`} selected={selected?.trackingNumber === row.trackingNumber} onClick={() => onSelect(row)} sx={{ cursor: 'pointer' }}>
      <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{row.trackingNumber}</TableCell><TableCell>{row.carrier || 'Other'}</TableCell><TableCell>{row.location || '—'}</TableCell><TableCell>{row.recipient || 'Unassigned'}</TableCell><TableCell><Chip size="small" label={(row.status || '').replaceAll('_', ' ')} color={row.status === 'CONFLICT' ? 'error' : row.status === 'PICKED_UP' ? 'default' : 'success'} variant="outlined" /></TableCell><TableCell>{row.occurredUtc || row.lastEventUtc}</TableCell><TableCell>{row.deviceId || row.lastDevice}</TableCell>
    </TableRow>)}
  </TableBody></Table></TableContainer>
}

function MessageList({ values = [], empty }) {
  return <Stack spacing={1} sx={{ p: 3 }}>{values.length ? values.map((value, index) => <Alert key={index} severity="warning">{value}</Alert>) : <Alert severity="success">{empty}</Alert>}</Stack>
}

function ActionDialog({ dialog, state, onClose, onConfigure, onConfirmScan, onAssign, onVoid }) {
  const [value, setValue] = useState('')
  useEffect(() => setValue(dialog?.type === 'setup' ? (state?.sharedRoot || '') : ''), [dialog, state])
  if (!dialog) return null
  const titles = { setup: 'Configure synchronized test folder', confirmScan: 'Confirm parsed barcode', recipient: 'Assign recipient', void: 'Void package' }
  const action = () => {
    if (dialog.type === 'setup') onConfigure({ sharedRoot: value })
    if (dialog.type === 'confirmScan') onConfirmScan()
    if (dialog.type === 'recipient') onAssign(value)
    if (dialog.type === 'void') onVoid(value)
  }
  return <Dialog open maxWidth="sm" fullWidth onClose={onClose}><DialogTitle>{titles[dialog.type]}</DialogTitle><DialogContent>
    {dialog.type === 'setup' && <><Alert severity="warning" sx={{ mb: 2 }}>Use an empty, non-production OneDrive-synchronized pilot folder.</Alert><TextField autoFocus fullWidth label="Full folder path" value={value} onChange={e => setValue(e.target.value)} placeholder="C:\Users\...\CommercialTrackingPilot" /></>}
    {dialog.type === 'confirmScan' && <Alert severity="warning">Tracking: <b>{dialog.payload.trackingNumber}</b><br />Carrier: {dialog.payload.carrier}<br />Confidence: {dialog.payload.confidence}</Alert>}
    {dialog.type === 'recipient' && <TextField autoFocus fullWidth label="Recipient" value={value} onChange={e => setValue(e.target.value)} />}
    {dialog.type === 'void' && <><Alert severity="error" sx={{ mb: 2 }}>The original history remains. Enter a reason to create an audited void event.</Alert><TextField autoFocus fullWidth multiline minRows={2} label="Void reason" value={value} onChange={e => setValue(e.target.value)} /></>}
  </DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" color={dialog.type === 'void' ? 'error' : 'primary'} disabled={(dialog.type !== 'confirmScan') && !value.trim()} onClick={action}>{dialog.type === 'confirmScan' ? 'Confirm and save' : 'Continue'}</Button></DialogActions></Dialog>
}

createRoot(document.getElementById('root')).render(<ThemeProvider theme={theme}><CssBaseline /><App /></ThemeProvider>)
