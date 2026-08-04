import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Alert, AppBar, Box, Button, Card, CardContent, Chip, CssBaseline, Dialog, DialogActions,
  DialogContent, DialogTitle, Divider, Drawer,
  FormControl, FormControlLabel, InputLabel, List, ListItemButton, ListItemIcon, ListItemText,
  MenuItem, Select, Snackbar, Stack,
  Switch, Table, TableBody, TableCell, TableHead, TablePagination, TableRow,
  TextField, ThemeProvider, Toolbar, Typography
} from '@mui/material'
import DashboardRounded from '@mui/icons-material/DashboardRounded'
import QrCodeScannerRounded from '@mui/icons-material/QrCodeScannerRounded'
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded'
import AppRegistrationRounded from '@mui/icons-material/AppRegistrationRounded'
import QrCode2Rounded from '@mui/icons-material/QrCode2Rounded'
import TroubleshootRounded from '@mui/icons-material/TroubleshootRounded'
import AssessmentRounded from '@mui/icons-material/AssessmentRounded'
import FactCheckRounded from '@mui/icons-material/FactCheckRounded'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import FolderRounded from '@mui/icons-material/FolderRounded'
import DownloadRounded from '@mui/icons-material/DownloadRounded'
import PlaylistAddCheckRounded from '@mui/icons-material/PlaylistAddCheckRounded'
import RestoreRounded from '@mui/icons-material/RestoreRounded'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import QRCode from 'qrcode'
import { api } from './api'
import { theme } from './theme'

const DRAWER = 240

class AppErrorBoundary extends React.Component {
  constructor(props) { super(props); this.state = { error: null } }
  static getDerivedStateFromError(error) { return { error } }
  render() {
    if (!this.state.error) return this.props.children
    return <Box sx={{ p: 4 }}><Alert severity="error">
      The application screen failed: {this.state.error.message}. Refresh and check item history before retrying any operation.
    </Alert><Button sx={{ mt: 2 }} variant="contained" onClick={() => window.location.reload()}>Reload application</Button></Box>
  }
}
const NAV = [
  ['dashboard', 'Dashboard', <DashboardRounded />],
  ['scan', 'Receive stock', <QrCodeScannerRounded />],
  ['batch', 'Batch scanner', <PlaylistAddCheckRounded />],
  ['inventory', 'Inventory', <Inventory2Rounded />],
  ['archive', 'Archive', <RestoreRounded />],
  ['count', 'Inventory count', <FactCheckRounded />],
  ['management', 'Management', <AssessmentRounded />],
  ['registration', 'Registration', <AppRegistrationRounded />],
  ['labels', 'Paper count ledger', <QrCode2Rounded />],
  ['settings', 'Settings', <SettingsRounded />],
  ['diagnostics', 'Diagnostics', <TroubleshootRounded />]
]

function expiryColor(iso) {
  if (!iso) return 'default'
  const days = (new Date(iso) - new Date()) / 86400000
  if (days < 0) return 'error'
  if (days <= 7) return 'error'
  if (days <= 30) return 'warning'
  return 'default'
}

function App() {
  const [view, setView] = useState('dashboard')
  const [state, setState] = useState(null)
  const [notices, setNotices] = useState([])
  const notify = useCallback((message, severity = 'info') => {
    if (message) setNotices(current => [...current, { id: Date.now() + Math.random(), message, severity }])
  }, [])
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)

  const navigate = useCallback(next => {
    if (next === view) return
    if (window.__medsupplyDirty && !window.confirm('Leave this screen? Unsubmitted work will be kept where possible, but may not be posted.')) return
    setView(next)
  }, [view])

  const refresh = useCallback(async () => {
    try { setState(await api.state()) } catch (e) { notify(e.message, 'error') }
  }, [notify])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 15000)
    return () => clearInterval(id)
  }, [refresh])

  const run = useCallback(async (fn, ok) => {
    if (busyRef.current) return false
    busyRef.current = true; setBusy(true)
    try {
      const result = await fn()
      if (result?.queued) notify(result.message, 'warning')
      else if (ok || result?.message) notify(ok || result.message, 'success')
      await refresh(); return true
    }
    catch (e) { notify(e.message, 'error'); return false }
    finally { busyRef.current = false; setBusy(false) }
  }, [refresh, notify])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <style>{`@media print {
        .app-chrome { display: none !important; }
        .ledger-screen-only { display: none !important; }
        .ledger-print { display: block !important; }
        body { background: white !important; }
        @page { size: landscape; margin: 0.4in; }
      }`}</style>
      <AppBar className="app-chrome" position="fixed" elevation={0}
        sx={{ zIndex: t => t.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 2 }}>
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6" fontWeight={700}>Medical Supply Tracking</Typography>
            <Typography variant="caption" sx={{ opacity: .82 }}>
              Inventory operations and supply readiness
            </Typography>
          </Box>
          {state?.configured && <Chip label="Shared store connected" color="success" size="small" />}
        </Toolbar>
      </AppBar>
      <Drawer className="app-chrome" variant="permanent" sx={{ width: DRAWER, '& .MuiDrawer-paper': {
        width: DRAWER, borderRightColor: 'divider', backgroundImage: 'none'
      } }}>
        <Toolbar />
        <Typography variant="overline" color="text.secondary" sx={{ px: 2.5, pt: 2 }}>Workspaces</Typography>
        <List sx={{ px: 1 }}>
          {NAV.map(([key, label, icon]) => (
            <ListItemButton key={key} selected={view === key} onClick={() => navigate(key)}
              sx={{ borderRadius: 1.5, mb: .5 }}>
              <ListItemIcon>{icon}</ListItemIcon>
              <ListItemText primary={label} />
            </ListItemButton>
          ))}
        </List>
        <Box sx={{ mt: 'auto', p: 2 }}>
          <Divider sx={{ mb: 2 }} />
          <Typography variant="caption" color="text.secondary" display="block">Current folder</Typography>
          <Typography variant="caption" title={state?.sharedRoot || ''} noWrap display="block">
            {state?.sharedRoot || 'Not configured'}
          </Typography>
        </Box>
      </Drawer>
      <Box component="main" aria-busy={busy} sx={{ ml: `${DRAWER}px`, p: { xs: 2, md: 3 }, mt: 8,
        minHeight: '100vh', '@media print': { ml: 0, p: 0, mt: 0 } }}>
        <Box component="fieldset" disabled={busy} sx={{ maxWidth: 1440, mx: 'auto', border: 0, p: 0, m: 0, minWidth: 0 }}>
        {state?.configured && !state.trailComplete && <Alert severity="error" sx={{ mb: 2 }}>
          Audit trail may be incomplete: {state.unreadableEventCount || 0} load errors and {state.pendingCount || 0} pending events.
          Reports are blocked until this is resolved. See Diagnostics.
        </Alert>}
        {!state ? <Typography>Loading…</Typography>
          : !state.configured && !['diagnostics', 'settings'].includes(view)
          ? <FolderPrompt run={run} />
          : {
              dashboard: <Dashboard state={state} run={run} setView={navigate} />,
              scan: <Scan state={state} run={run} refresh={refresh} setToast={notify} />,
              batch: <BatchScanner refresh={refresh} setToast={notify} setView={navigate} />,
              inventory: <Inventory state={state} run={run} />,
              archive: <Archive state={state} run={run} />,
              count: <Count state={state} run={run} />,
              management: <Management state={state} run={run} setView={navigate} />,
              registration: <Registration state={state} run={run} />,
              labels: <Labels state={state} />,
              settings: <Settings state={state} run={run} />,
              diagnostics: <Diagnostics state={state} run={run} />
            }[view]}
        </Box>
      </Box>
      <Snackbar open={notices.length > 0} autoHideDuration={5000}
        onClose={() => setNotices(current => current.slice(1))}>
        <Alert severity={notices[0]?.severity || 'info'} variant="filled"
          onClose={() => setNotices(current => current.slice(1))}>{notices[0]?.message || ''}</Alert>
      </Snackbar>
      {busy && <><Box className="app-chrome" aria-hidden="true" sx={{ position: 'fixed', inset: 0,
        bgcolor: 'rgba(255,255,255,.15)', cursor: 'wait', zIndex: 1400 }} />
        <Box className="app-chrome" role="status" aria-live="polite" sx={{ position: 'fixed', right: 20, bottom: 20,
          bgcolor: 'background.paper', boxShadow: 3, borderRadius: 2, px: 2, py: 1, zIndex: 1500 }}>Working…</Box></>}
    </ThemeProvider>
  )
}

function useUnsavedGuard(dirty) {
  useEffect(() => {
    window.__medsupplyDirty = !!dirty
    const warn = event => { if (dirty) { event.preventDefault(); event.returnValue = '' } }
    window.addEventListener('beforeunload', warn)
    return () => { window.removeEventListener('beforeunload', warn); window.__medsupplyDirty = false }
  }, [dirty])
}

function usePagedRows(rows, defaultSize = 50) {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(defaultSize)
  useEffect(() => {
    if (page * pageSize >= rows.length) setPage(0)
  }, [rows.length, page, pageSize])
  return {
    pageRows: rows.slice(page * pageSize, page * pageSize + pageSize),
    pager: <TablePagination component="div" count={rows.length} page={page}
      onPageChange={(_, value) => setPage(value)} rowsPerPage={pageSize}
      onRowsPerPageChange={event => { setPageSize(Number(event.target.value)); setPage(0) }}
      rowsPerPageOptions={[25, 50, 100]} />
  }
}

function FolderPrompt({ run }) {
  const chooseFolder = () => run(async () => {
    const result = await api.chooseFolder()
    if (result.cancelled) throw new Error('Folder selection cancelled')
  }, 'Folder set')
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Synchronized folder</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Choose the local OneDrive-synchronized folder used by your team.
      </Typography>
      <Button variant="contained" onClick={chooseFolder}>Choose folder…</Button>
    </CardContent></Card>
  )
}

function PageHeader({ title, description, actions }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between"
      alignItems={{ sm: 'center' }} spacing={2} sx={{ mb: 3 }}>
      <Box>
        <Typography variant="h5">{title}</Typography>
        <Typography variant="body2" color="text.secondary">{description}</Typography>
      </Box>
      {actions && <Stack direction="row" spacing={1}>{actions}</Stack>}
    </Stack>
  )
}

function EmptyState({ children }) {
  return <Box sx={{ py: 5, textAlign: 'center', color: 'text.secondary' }}>{children}</Box>
}

function Dashboard({ state, run, setView }) {
  const d = state.dashboard || {}
  const tiles = [
    ['Active SKUs', d.distinctSkus, 'primary.main'],
    ['On-hand value', `$${(d.onHandValue || 0).toFixed(2)}`, 'secondary.main'],
    ['Total units', d.totalUnits, 'text.primary'],
    ['Expired', d.expired, 'error.main'],
    ['Expiring in 30 days', d.expiring30, 'warning.main'],
    ['Out of stock', d.outOfStock, 'error.main']
  ]
  const reorder = (state.reorder || []).filter(r => r.needsReorder)
  return (
    <Stack spacing={2}>
      <PageHeader title="Operations dashboard"
        description="A live view of inventory health, readiness, and action items."
        actions={<Button startIcon={<DownloadRounded />} variant="outlined"
          onClick={() => run(() => api.report(), 'Management report exported')}>Export report</Button>} />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
        {tiles.map(([label, value, color]) => (
          <Card key={label} sx={{ minWidth: 175, flex: '1 1 175px' }}><CardContent>
            <Typography variant="h4" color={color}>{value ?? 0}</Typography>
            <Typography variant="body2" color="text.secondary">{label}</Typography>
          </CardContent></Card>
        ))}
      </Box>
      {(d.expired > 0 || d.expiring7 > 0) &&
        <Alert severity="warning">Review {d.expired || 0} expired and {d.expiring7 || 0} near-expiry stock lines.</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' }, gap: 2 }}>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ px: 2.5, py: 2 }}>
          <Typography variant="h6">Priority replenishment</Typography>
          <Typography variant="body2" color="text.secondary">Items currently below PAR or calculated reorder point.</Typography>
        </Box>
        <Table size="small">
          <TableHead><TableRow><TableCell>Product</TableCell><TableCell>On hand</TableCell>
            <TableCell>Target</TableCell><TableCell>Order</TableCell><TableCell>Est. cost</TableCell></TableRow></TableHead>
          <TableBody>
            {reorder.slice(0, 10).map(r => (
              <TableRow key={r.gtin}><TableCell>{r.name || r.gtin}</TableCell><TableCell>{r.onHand}</TableCell>
                <TableCell>{r.parProvided ? r.par : r.suggestedPar}</TableCell><TableCell>{r.suggestedOrderQty}</TableCell>
                <TableCell>{(r.estimatedCost || 0).toFixed(2)}</TableCell></TableRow>
            ))}
          </TableBody>
        </Table>
        {!reorder.length && <EmptyState>No replenishment actions right now.</EmptyState>}
      </CardContent></Card>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Quick actions</Typography>
        <Stack spacing={1}>
          <Button variant="contained" onClick={() => setView('scan')}>Receive stock</Button>
          <Button variant="outlined" onClick={() => setView('count')}>Start inventory count</Button>
          <Button variant="outlined" onClick={() => setView('registration')}>Register product</Button>
          <Button variant="text" onClick={() => setView('management')}>Open management workspace</Button>
        </Stack>
        <Divider sx={{ my: 2 }} />
        <Typography variant="caption" color="text.secondary">Activity in last 7 days</Typography>
        <Typography variant="h5">{d.activeEventsLast7 || 0}</Typography>
      </CardContent></Card>
      </Box>
    </Stack>
  )
}

function Scan({ state, run, refresh, setToast }) {
  const scanner = state.settings || {}
  const [raw, setRaw] = useState('')
  const [supplemental, setSupplemental] = useState('')
  const [qty, setQty] = useState(String(scanner.scannerDefaultQuantity || 1))
  const [preview, setPreview] = useState(null)
  const [metadata, setMetadata] = useState({ name: '', manufacturer: '', category: '' })
  const [lookingUp, setLookingUp] = useState(false)
  const quantityRef = useRef(null)
  const barcodeRef = useRef(null)
  const autoSubmitted = useRef(false)
  useUnsavedGuard(!!raw.trim() || !!supplemental.trim() || !!preview)

  const inspect = async () => {
    if (!raw.trim() || lookingUp) return
    setLookingUp(true)
    autoSubmitted.current = false
    try {
      const combined = supplemental.trim() ? `${raw}${String.fromCharCode(29)}${supplemental}` : raw
      const result = await api.previewReceive({ raw: combined })
      const catalog = result.catalog || {}
      const gudid = result.gudid || {}
      setPreview(result)
      setMetadata({
        name: catalog.name || gudid.brandName || gudid.deviceDescription || gudid.name || '',
        manufacturer: catalog.manufacturer || gudid.companyName || gudid.manufacturer || '',
        category: catalog.category || (gudid.gmdnTerms || []).join('; ') || gudid.category || ''
      })
      window.setTimeout(() => {
        quantityRef.current?.focus()
        quantityRef.current?.select()
      }, 0)
    } catch (e) {
      setPreview(null)
      setToast(e.message)
    } finally {
      setLookingUp(false)
    }
  }

  const submit = async () => {
    if (!preview) {
      await inspect()
      return
    }
    try {
      if (!preview.registered) {
        if (!metadata.name.trim()) throw new Error('Choose or enter a product name before receiving.')
        await api.register({
          gtin: preview.gtin,
          name: metadata.name.trim(),
          manufacturer: metadata.manufacturer.trim(),
          category: metadata.category.trim(),
          source: preview.gudid?.found ? 'GUDID' : 'MANUAL'
        })
      }
      const combined = supplemental.trim() ? `${raw}${String.fromCharCode(29)}${supplemental}` : raw
      await api.receive({ raw: combined, quantity: qty, force: 'true' })
      setRaw('')
      setSupplemental('')
      setQty(String(scanner.scannerDefaultQuantity || 1))
      setPreview(null)
      setMetadata({ name: '', manufacturer: '', category: '' })
      setToast(`Received ${qty} × ${metadata.name || preview.gtin}`)
      if (scanner.scannerSound) {
        const context = new (window.AudioContext || window.webkitAudioContext)()
        const oscillator = context.createOscillator(); oscillator.connect(context.destination)
        oscillator.frequency.value = 880; oscillator.start(); oscillator.stop(context.currentTime + .08)
      }
      if (scanner.scannerAutoFocus) window.setTimeout(() => barcodeRef.current?.focus(), 0)
      await refresh()
    } catch (e) { setToast(e.message) }
  }

  const gudid = preview?.gudid || {}
  useEffect(() => {
    if (scanner.scannerAutoSubmit && preview?.registered && !autoSubmitted.current) {
      autoSubmitted.current = true
      submit()
    }
  }, [preview])
  const nameChoices = [...new Set([
    gudid.brandName,
    gudid.deviceDescription,
    ...(gudid.gmdnTerms || [])
  ].filter(Boolean))]

  return (
    <Stack spacing={2}>
    <PageHeader title="Receive stock"
      description="Scan, verify the encoded label and product identity, then post the receipt." />
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>New receipt</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <TextField inputRef={barcodeRef} autoFocus={scanner.scannerAutoFocus !== false} fullWidth size="small" label="GTIN / primary barcode" value={raw}
          onChange={e => { setRaw(e.target.value); setPreview(null) }}
          onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); inspect() } }} />
        <Button variant="outlined" disabled={!raw.trim() || lookingUp} onClick={inspect}
          sx={{ minWidth: 110 }}>{lookingUp ? 'Looking up…' : 'Review scan'}</Button>
      </Stack>
      <TextField fullWidth size="small" sx={{ mt: 1 }} label="Optional second scan — lot / expiration"
        value={supplemental} onChange={e => { setSupplemental(e.target.value); setPreview(null) }}
        onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); inspect() } }}
        helperText="Use when the device identifier and production data are on separate barcodes." />

      {preview && <Stack spacing={2.5} sx={{ mt: 3 }}>
        <Divider />
        <Box>
          <Typography variant="overline" color="text.secondary">Encoded barcode information</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 2, mt: 1 }}>
            {[['GTIN', preview.gtin], ['Lot / batch', preview.lot || 'Not encoded'],
              ['Expiration', preview.expirationIso || 'Not encoded']].map(([label, value]) =>
              <Box key={label} sx={{ p: 1.5, bgcolor: 'action.hover', borderRadius: 1 }}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <Typography variant="body1" fontWeight={600}>{value}</Typography>
              </Box>)}
          </Box>
        </Box>

        <Box>
          <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1.5 }}>
            <Typography variant="overline" color="text.secondary">Product identity</Typography>
            <Chip size="small" color={preview.registered ? 'success' : gudid.found ? 'info' : 'warning'}
              label={preview.registered ? 'Registered catalog item' : gudid.found ? 'FDA GUDID match' : 'Manual details required'} />
          </Stack>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, gap: 2 }}>
            <TextField label="Product name" value={metadata.name} disabled={preview.registered}
              onChange={e => setMetadata(m => ({ ...m, name: e.target.value }))}
              helperText={!preview.registered && nameChoices.length ? 'Choose a GUDID value below or enter a clearer inventory name.' : ' '} />
            <TextField label="Manufacturer" value={metadata.manufacturer} disabled={preview.registered}
              onChange={e => setMetadata(m => ({ ...m, manufacturer: e.target.value }))} />
            <TextField label="Generic name / category" value={metadata.category} disabled={preview.registered}
              onChange={e => setMetadata(m => ({ ...m, category: e.target.value }))} />
            <TextField inputRef={quantityRef} label="Quantity received" type="number" value={qty}
              inputProps={{ min: 1 }} onChange={e => setQty(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); submit() } }} />
          </Box>
          {!preview.registered && nameChoices.length > 0 && <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 1 }}>
            {nameChoices.map((choice, index) => <Button key={choice} size="small" variant="outlined"
              onClick={() => setMetadata(m => ({ ...m, name: choice }))}>
              Use {index === 0 && choice === gudid.brandName ? 'trade name' :
                choice === gudid.deviceDescription ? 'description' : 'generic name'}: {choice}
            </Button>)}
          </Stack>}
        </Box>

        {gudid.lookupFailed && <Alert severity="warning">GUDID lookup failed: {gudid.error || 'service unavailable'}. Enter product details manually.</Alert>}
        {gudid.found && <Box sx={{ p: 2, border: 1, borderColor: 'divider', borderRadius: 1 }}>
          <Typography variant="subtitle2" gutterBottom>FDA GUDID details</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: .75 }}>
            <Typography variant="body2"><b>Trade / brand name:</b> {gudid.brandName || '—'}</Typography>
            <Typography variant="body2"><b>Manufacturer:</b> {gudid.companyName || '—'}</Typography>
            <Typography variant="body2"><b>Device description:</b> {gudid.deviceDescription || '—'}</Typography>
            <Typography variant="body2"><b>Generic name:</b> {(gudid.gmdnTerms || []).join('; ') || '—'}</Typography>
            <Typography variant="body2"><b>Model:</b> {gudid.versionModelNumber || '—'}</Typography>
            <Typography variant="body2"><b>Catalog number:</b> {gudid.catalogNumber || '—'}</Typography>
          </Box>
        </Box>}

        <Stack direction="row" justifyContent="flex-end">
          <Button variant="contained" size="large" disabled={!qty || Number(qty) <= 0 || !metadata.name.trim()}
            onClick={submit}>Receive stock</Button>
        </Stack>
      </Stack>}
    </CardContent></Card>
    <Alert severity="info">Scan and press Enter. The cursor moves to quantity after the label and product details are ready for review.</Alert>
    </Stack>
  )
}

const BATCH_QUEUE_KEY = 'medsupply.batchScannerQueue'

function BatchScanner({ refresh, setToast, setView }) {
  const [buffer, setBuffer] = useState('')
  const [posting, setPosting] = useState(false)
  const [queue, setQueue] = useState(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(BATCH_QUEUE_KEY) || '[]')
      return Array.isArray(saved) ? saved : []
    } catch (error) {
      return []
    }
  })
  useUnsavedGuard(!!buffer.trim() || queue.some(item => item.status !== 'posted'))
  const { pageRows: queueRows, pager: queuePager } = usePagedRows(queue)

  useEffect(() => {
    sessionStorage.setItem(BATCH_QUEUE_KEY, JSON.stringify(queue))
  }, [queue])

  const addScan = rawValue => {
    const raw = rawValue.trim()
    if (!raw) return
    setQueue(current => {
      const existing = current.find(item => item.raw === raw && item.status !== 'posted')
      if (existing) {
        return current.map(item => item.id === existing.id
          ? { ...item, quantity: item.quantity + 1, status: 'queued', message: '' }
          : item)
      }
      return [...current, {
        id: `${Date.now()}-${Math.random()}`,
        raw,
        quantity: 1,
        status: 'queued',
        message: ''
      }]
    })
  }

  const acceptRelease = value => {
    const parts = value.split(/\r\n|\n|\r/)
    if (parts.length === 1) {
      setBuffer(value)
      return
    }
    parts.slice(0, -1).forEach(addScan)
    setBuffer(parts[parts.length - 1])
  }

  const finishBuffer = () => {
    addScan(buffer)
    setBuffer('')
  }

  const updateItem = (id, update) => setQueue(current =>
    current.map(item => item.id === id ? { ...item, ...update } : item))

  const postBatch = async () => {
    const pending = queue.filter(item => item.status !== 'posted')
    if (!pending.length) return
    setPosting(true)
    let posted = 0
    let unresolved = 0
    for (const item of pending) {
      updateItem(item.id, { status: 'posting', message: '' })
      try {
        const result = await api.receive({
          raw: item.raw,
          quantity: String(item.quantity),
          force: 'false'
        })
        if (result.needsRegistration) {
          unresolved += 1
          updateItem(item.id, {
            status: 'registration',
            message: `Register GTIN ${result.gtin} before posting`,
            gtin: result.gtin,
            suggestion: result.suggestion || {}
          })
        } else {
          posted += 1
          updateItem(item.id, { status: 'posted', message: result.message || 'Posted' })
        }
      } catch (error) {
        updateItem(item.id, { status: 'error', message: error.message })
      }
    }
    setPosting(false)
    await refresh()
    setToast(`${posted} line${posted === 1 ? '' : 's'} posted${unresolved ? `; ${unresolved} need registration` : ''}`)
  }

  const openRegistration = item => {
    sessionStorage.setItem('medsupply.registrationDraft', JSON.stringify({
      gtin: item.gtin || '',
      name: item.suggestion?.name || '',
      manufacturer: item.suggestion?.manufacturer || '',
      category: item.suggestion?.category || ''
    }))
    setView('registration')
  }

  const statusColor = status => ({
    queued: 'default', posting: 'info', posted: 'success', registration: 'warning', error: 'error'
  }[status] || 'default')
  const pendingCount = queue.filter(item => item.status !== 'posted').length
  const scanCount = queue.reduce((sum, item) => sum + item.quantity, 0)

  return (
    <Stack spacing={2}>
      <PageHeader title="Batch scanner"
        description="Capture and review scans released from a scanner's onboard memory before updating inventory."
        actions={<Button variant="contained" disabled={!pendingCount || posting} onClick={postBatch}>
          {posting ? 'Posting…' : `Post batch (${pendingCount})`}
        </Button>} />
      <Alert severity="info">
        Configure the scanner to send Enter, CR/LF, or Tab after each stored barcode. Place the cursor below,
        release the scanner memory, then review the queue before posting.
      </Alert>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Scanner capture</Typography>
        <TextField autoFocus fullWidth multiline minRows={3} label="Release stored scans here"
          value={buffer} onChange={event => acceptRelease(event.target.value)}
          onKeyDown={event => {
            if (event.key === 'Enter' || event.key === 'Tab') {
              event.preventDefault()
              finishBuffer()
            }
          }}
          helperText="Each terminator adds one scan. Repeated identical scans are combined into a quantity." />
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          <Button variant="outlined" disabled={!buffer.trim()} onClick={finishBuffer}>Add current scan</Button>
          <Chip label={`${queue.length} queue lines`} />
          <Chip label={`${scanCount} total scans`} />
        </Stack>
      </CardContent></Card>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ px: 2.5, py: 2, display: 'flex', justifyContent: 'space-between' }}>
          <Box><Typography variant="h6">Release queue</Typography>
            <Typography variant="body2" color="text.secondary">Nothing is written until Post batch is selected.</Typography></Box>
          <Stack direction="row" spacing={1}>
            <Button size="small" onClick={() => setQueue(current => current.filter(item => item.status !== 'posted'))}>
              Clear posted
            </Button>
            <Button size="small" color="error" onClick={() => setQueue([])}>Clear all</Button>
          </Stack>
        </Box>
        <Table size="small"><TableHead><TableRow><TableCell>Captured barcode</TableCell>
          <TableCell sx={{ width: 130 }}>Quantity</TableCell><TableCell sx={{ width: 150 }}>Status</TableCell>
          <TableCell>Message</TableCell><TableCell sx={{ width: 120 }}>Action</TableCell></TableRow></TableHead>
          <TableBody>{queueRows.map(item => <TableRow key={item.id}>
            <TableCell sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{item.raw}</TableCell>
            <TableCell><TextField size="small" type="number" value={item.quantity}
              disabled={item.status === 'posted' || posting} inputProps={{ min: 1 }}
              onChange={event => updateItem(item.id, {
                quantity: Math.max(1, Number(event.target.value) || 1), status: 'queued'
              })} /></TableCell>
            <TableCell><Chip size="small" color={statusColor(item.status)} label={item.status} /></TableCell>
            <TableCell>{item.message}</TableCell>
            <TableCell>{item.status === 'registration'
              ? <Button size="small" onClick={() => openRegistration(item)}>Register</Button>
              : item.status !== 'posted' && <Button size="small" color="error"
                onClick={() => setQueue(current => current.filter(row => row.id !== item.id))}>Remove</Button>}</TableCell>
          </TableRow>)}</TableBody></Table>{queuePager}
        {!queue.length && <EmptyState>Release scanner memory to begin a batch.</EmptyState>}
      </CardContent></Card>
    </Stack>
  )
}

function Inventory({ state, run }) {
  const [filter, setFilter] = useState('')
  const [manufacturer, setManufacturer] = useState('')
  const [category, setCategory] = useState('')
  const [action, setAction] = useState(null)
  const [actionValue, setActionValue] = useState('')
  const [selected, setSelected] = useState(null)
  const [history, setHistory] = useState([])
  const [historyError, setHistoryError] = useState('')
  const openDetails = async line => {
    setSelected(line)
    setHistory([])
    setHistoryError('')
    try {
      const result = await api.itemHistory({
        gtin: line.gtin, lot: line.lot, expirationIso: line.expirationIso
      })
      setHistory(result.events || [])
    } catch (error) { setHistoryError(error.message) }
  }
  const eventLabel = type => ({
    STOCK_RECEIVED: 'Received', STOCK_PICKED: 'Picked', STOCK_ADJUSTED: 'Adjusted',
    STOCK_ARCHIVED: 'Archived', STOCK_VOIDED: 'Voided', STOCK_RESTORED: 'Restored'
  }[type] || type)
  const manufacturers = [...new Set((state.stock || []).map(l => l.manufacturer).filter(Boolean))].sort()
  const categories = [...new Set((state.stock || []).map(l => l.category).filter(Boolean))].sort()
  const rows = (state.stock || []).filter(l => l.active &&
    (!manufacturer || l.manufacturer === manufacturer) && (!category || l.category === category) &&
    (l.name + l.gtin + l.lot).toLowerCase().includes(filter.toLowerCase()))
  const { pageRows: inventoryRows, pager: inventoryPager } = usePagedRows(rows)
  const beginAction = (type, line) => {
    setAction({ type, line })
    setActionValue(type === 'adjust' ? String(line.quantity) : type === 'pick' ? '1' : '')
  }
  const submitAction = async () => {
    const { type, line } = action
    const payload = { gtin: line.gtin, lot: line.lot, expirationIso: line.expirationIso }
    let succeeded = false
    if (type === 'pick') succeeded = await run(() => api.pick({ ...payload, quantity: actionValue }), 'Picked')
    if (type === 'adjust') succeeded = await run(() => api.adjust({ ...payload, quantity: actionValue }), 'Adjusted')
    if (type === 'archive') succeeded = await run(() => api.archive({ ...payload, reason: actionValue }), 'Archived')
    if (succeeded) setAction(null)
  }
  return (
    <Stack spacing={2}>
    <PageHeader title="Inventory" description="Search active stock by product, GTIN, or lot and perform daily transactions."
      actions={<Button color="warning" variant="outlined" onClick={() => run(() => api.archiveExpired(), 'Expired inventory archived')}>
        Archive expired items</Button>} />
    <Card><CardContent>
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <TextField size="small" label="Search inventory" value={filter}
          onChange={e => setFilter(e.target.value)} sx={{ width: 340 }} />
        <FormControl size="small" sx={{ minWidth: 180 }}><InputLabel>Manufacturer</InputLabel>
          <Select value={manufacturer} label="Manufacturer" onChange={e => setManufacturer(e.target.value)}>
            <MenuItem value="">All manufacturers</MenuItem>{manufacturers.map(v => <MenuItem key={v} value={v}>{v}</MenuItem>)}
          </Select></FormControl>
        <FormControl size="small" sx={{ minWidth: 160 }}><InputLabel>Category</InputLabel>
          <Select value={category} label="Category" onChange={e => setCategory(e.target.value)}>
            <MenuItem value="">All categories</MenuItem>{categories.map(v => <MenuItem key={v} value={v}>{v}</MenuItem>)}
          </Select></FormControl>
        <Chip label={`${rows.length} active stock lines`} />
      </Stack>
      <Table size="small">
        <TableHead><TableRow><TableCell>Product</TableCell><TableCell>GTIN</TableCell><TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
          <TableCell>Qty</TableCell><TableCell>Actions</TableCell></TableRow></TableHead>
        <TableBody>
          {inventoryRows.map(l => (
            <TableRow hover key={l.itemKey} onClick={() => openDetails(l)}
              sx={{ cursor: 'pointer' }} aria-label={`View history for ${l.name || l.gtin}, lot ${l.lot || 'none'}`}>
              <TableCell><Typography variant="body2" fontWeight={600}>{l.name || 'Unregistered product'}</Typography>
                <Typography variant="caption" color="text.secondary">{l.manufacturer || l.category || ''}</Typography></TableCell>
              <TableCell>{l.gtin}</TableCell><TableCell>{l.lot || '—'}</TableCell>
              <TableCell><Chip size="small" color={expiryColor(l.expirationIso)} label={l.expirationIso || '—'} /></TableCell>
              <TableCell>{l.quantity}</TableCell>
              <TableCell onClick={e => e.stopPropagation()}>
                <Button size="small" onClick={() => beginAction('pick', l)}>Pick</Button>
                <Button size="small" onClick={() => beginAction('adjust', l)}>Adjust</Button>
                <Button size="small" color="error" onClick={() => beginAction('archive', l)}>Archive</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {inventoryPager}
      {!rows.length && <EmptyState>No active inventory matches this search.</EmptyState>}
    </CardContent></Card>
    <Dialog open={!!action} onClose={() => setAction(null)} fullWidth maxWidth="xs">
      <DialogTitle>{action?.type === 'pick' ? 'Pick stock' : action?.type === 'adjust' ? 'Adjust count' : 'Archive lot'}</DialogTitle>
      <DialogContent dividers><Typography variant="body2" sx={{ mb: 2 }}>
        {action?.line.name || action?.line.gtin} · Lot {action?.line.lot || 'not recorded'} · {action?.line.quantity} on hand
      </Typography><TextField autoFocus fullWidth type={action?.type === 'archive' ? 'text' : 'number'}
        label={action?.type === 'archive' ? 'Archive reason' : action?.type === 'pick' ? 'Quantity to pick' : 'New quantity'}
        inputProps={action?.type === 'pick' ? { min: 1, max: action?.line.quantity } : { min: 0 }}
        value={actionValue} onChange={e => setActionValue(e.target.value)} /></DialogContent>
      <DialogActions><Button onClick={() => setAction(null)}>Cancel</Button>
        <Button variant="contained" color={action?.type === 'archive' ? 'error' : 'primary'}
          disabled={!actionValue.trim()} onClick={submitAction}>Confirm</Button></DialogActions>
    </Dialog>
    <Dialog open={!!selected} onClose={() => setSelected(null)} fullWidth maxWidth="md">
      {selected && <>
        <DialogTitle>{selected.name || 'Inventory item'}</DialogTitle>
        <DialogContent dividers>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 2, mb: 3 }}>
            {[['GTIN', selected.gtin], ['Lot / batch', selected.lot || 'Not recorded'],
              ['Expiration', selected.expirationIso || 'Not recorded'], ['On hand', selected.quantity],
              ['Manufacturer', selected.manufacturer || 'Not recorded'],
              ['Category', selected.category || 'Not recorded']].map(([label, value]) =>
              <Box key={label}><Typography variant="caption" color="text.secondary">{label}</Typography>
                <Typography variant="body2" fontWeight={600}>{value}</Typography></Box>)}
          </Box>
          <Typography variant="h6" gutterBottom>Lot history</Typography>
          {historyError && <Alert severity="error" sx={{ mb: 2 }}>{historyError}</Alert>}
          <Table size="small">
            <TableHead><TableRow><TableCell>Date and time</TableCell><TableCell>Change</TableCell>
              <TableCell>Balance</TableCell><TableCell>User / workstation</TableCell><TableCell>Details</TableCell></TableRow></TableHead>
            <TableBody>{history.map((event, index) => <TableRow key={`${event.occurredUtc}-${index}`}>
              <TableCell>{new Date(event.occurredUtc).toLocaleString()}</TableCell>
              <TableCell><Typography variant="body2" fontWeight={600}>{eventLabel(event.eventType)}</Typography>
                <Typography variant="caption" color="text.secondary">{event.change || '—'}</Typography></TableCell>
              <TableCell>{event.balance}</TableCell>
              <TableCell>{event.actor || 'Unknown'}<Typography variant="caption" color="text.secondary" display="block">{event.deviceId}</Typography></TableCell>
              <TableCell>{event.reason || (event.barcode ? 'Barcode receipt' : '—')}</TableCell>
            </TableRow>)}</TableBody>
          </Table>
          {!history.length && !historyError && <EmptyState>Loading lot history…</EmptyState>}
        </DialogContent>
        <DialogActions><Button onClick={() => setSelected(null)}>Close</Button></DialogActions>
      </>}
    </Dialog>
    </Stack>
  )
}

function Archive({ state, run }) {
  const [filter, setFilter] = useState('')
  const rows = (state.stock || []).filter(line => !line.active &&
    `${line.name}${line.gtin}${line.lot}`.toLowerCase().includes(filter.toLowerCase()))
  const { pageRows: archiveRows, pager: archivePager } = usePagedRows(rows)
  return <Stack spacing={2}>
    <PageHeader title="Archive" description="Browse inactive inventory lots and restore them with an append-only audit event." />
    <Card><CardContent><Stack direction="row" spacing={1} sx={{ mb: 2 }}>
      <TextField size="small" label="Search archive" value={filter} onChange={e => setFilter(e.target.value)} />
      <Chip label={`${rows.length} archived lots`} /></Stack>
      <Table size="small"><TableHead><TableRow><TableCell>Product</TableCell><TableCell>GTIN</TableCell>
        <TableCell>Lot</TableCell><TableCell>Expiration</TableCell><TableCell align="right">Quantity</TableCell>
        <TableCell>Action</TableCell></TableRow></TableHead><TableBody>{archiveRows.map(line => <TableRow key={line.itemKey}>
          <TableCell>{line.name || 'Unregistered product'}</TableCell><TableCell>{line.gtin}</TableCell>
          <TableCell>{line.lot || '—'}</TableCell><TableCell>{line.expirationIso || '—'}</TableCell>
          <TableCell align="right">{line.quantity}</TableCell><TableCell><Button size="small" startIcon={<RestoreRounded />}
            onClick={() => run(() => api.restore({ gtin: line.gtin, lot: line.lot,
              expirationIso: line.expirationIso, reason: 'Restored by operator' }), 'Lot restored')}>Restore</Button></TableCell>
        </TableRow>)}</TableBody></Table>{archivePager}{!rows.length && <EmptyState>No archived lots match this search.</EmptyState>}
    </CardContent></Card>
  </Stack>
}

function Count({ state, run }) {
  const active = (state.stock || []).filter(line => line.active)
  const [filter, setFilter] = useState('')
  const [scanValue, setScanValue] = useState('')
  const [selectedKey, setSelectedKey] = useState('')
  const [delta, setDelta] = useState('')
  const [scanMessage, setScanMessage] = useState('')
  const [counts, setCounts] = useState(() => Object.fromEntries(
    active.map(line => [line.itemKey, String(line.quantity)])))

  useEffect(() => {
    setCounts(current => Object.fromEntries(active.map(line => [
      line.itemKey, current[line.itemKey] ?? String(line.quantity)
    ])))
  }, [state.stock])

  const rows = active.filter(line =>
    `${line.name} ${line.gtin} ${line.lot}`.toLowerCase().includes(filter.toLowerCase()))
  const { pageRows: countRows, pager: countPager } = usePagedRows(rows)
  const changed = active.filter(line =>
    Number(counts[line.itemKey]) !== line.quantity && counts[line.itemKey] !== '')
  useUnsavedGuard(!!scanValue.trim() || !!delta.trim() || changed.length > 0)
  const selected = active.find(line => line.itemKey === selectedKey)

  const resolveLedgerQr = () => {
    const scanned = scanValue.trim()
    const key = scanned.startsWith('MSITEM:') ? scanned.slice(7) : scanned
    const match = active.find(line => line.itemKey === key || line.gtin === key)
    if (!match) {
      setSelectedKey('')
      setScanMessage('No active stock line matches that QR code.')
      return
    }
    setSelectedKey(match.itemKey)
    setDelta('')
    setScanMessage('')
  }

  const applyDelta = () => {
    if (!selected) return
    const amount = Number(delta)
    if (!Number.isInteger(amount) || amount === 0) {
      setScanMessage('Enter a whole-number delta such as -2 or +3.')
      return
    }
    const newQuantity = selected.quantity + amount
    if (newQuantity < 0) {
      setScanMessage('That delta would make inventory negative.')
      return
    }
    run(async () => {
      await api.adjust({
        gtin: selected.gtin,
        lot: selected.lot,
        expirationIso: selected.expirationIso,
        quantity: String(newQuantity)
      })
      setScanValue('')
      setSelectedKey('')
      setDelta('')
      setScanMessage('')
    }, `Applied ${amount > 0 ? '+' : ''}${amount} to ${selected.name || selected.gtin}`)
  }

  const save = () => run(async () => {
    for (const line of changed) {
      await api.adjust({
        gtin: line.gtin,
        lot: line.lot,
        expirationIso: line.expirationIso,
        quantity: counts[line.itemKey]
      })
    }
  }, `${changed.length} count${changed.length === 1 ? '' : 's'} posted`)

  return (
    <Stack spacing={2}>
      <PageHeader title="Inventory count"
        description="Reconcile the paper ledger by scanning its QR codes, or enter physical counts in bulk."
        actions={<Button variant="contained" disabled={!changed.length} onClick={save}>
          Post {changed.length || ''} adjustment{changed.length === 1 ? '' : 's'}
        </Button>} />
      <Card><CardContent>
        <Typography variant="h6">Scan paper ledger QR</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Scan a row from the printed ledger, then enter the signed change recorded on paper.
        </Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ md: 'center' }}>
          <TextField autoFocus size="small" label="Scan QR code" value={scanValue}
            onChange={event => setScanValue(event.target.value)}
            onKeyDown={event => event.key === 'Enter' && resolveLedgerQr()} sx={{ minWidth: 340 }} />
          <Button variant="outlined" onClick={resolveLedgerQr}>Look up</Button>
          {selected && <>
            <Divider orientation="vertical" flexItem />
            <Box sx={{ minWidth: 220 }}><Typography variant="body2" fontWeight={700}>
              {selected.name || selected.gtin}</Typography>
              <Typography variant="caption">Lot {selected.lot || '—'} · Current {selected.quantity}</Typography></Box>
            <TextField size="small" label="Delta (+ / -)" type="number" value={delta}
              onChange={event => setDelta(event.target.value)}
              onKeyDown={event => event.key === 'Enter' && applyDelta()} sx={{ width: 150 }} />
            <Button variant="contained" onClick={applyDelta}>Apply delta</Button>
          </>}
        </Stack>
        {scanMessage && <Alert severity="warning" sx={{ mt: 2 }}>{scanMessage}</Alert>}
      </CardContent></Card>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ px: 2, pt: 2 }}><Typography variant="h6">Bulk physical count</Typography>
          <Typography variant="body2" color="text.secondary">Enter counted quantities directly when transcribing the full sheet.</Typography></Box>
        <Box sx={{ p: 2, display: 'flex', gap: 2, alignItems: 'center' }}>
          <TextField size="small" label="Find product or lot" value={filter}
            onChange={event => setFilter(event.target.value)} sx={{ width: 340 }} />
          <Chip label={`${active.length} stock lines`} />
          <Chip color={changed.length ? 'warning' : 'default'} label={`${changed.length} changed`} />
        </Box>
        <Table size="small">
          <TableHead><TableRow><TableCell>Product</TableCell><TableCell>GTIN</TableCell>
            <TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
            <TableCell align="right">System qty</TableCell><TableCell sx={{ width: 150 }}>Counted qty</TableCell>
            <TableCell align="right">Variance</TableCell></TableRow></TableHead>
          <TableBody>{countRows.map(line => {
            const counted = counts[line.itemKey]
            const variance = counted === '' ? 0 : Number(counted) - line.quantity
            return <TableRow key={line.itemKey} sx={variance ? { backgroundColor: '#fff8e1' } : undefined}>
              <TableCell><Typography variant="body2" fontWeight={600}>{line.name || 'Unregistered product'}</Typography></TableCell>
              <TableCell>{line.gtin}</TableCell><TableCell>{line.lot || '—'}</TableCell>
              <TableCell>{line.expirationIso || '—'}</TableCell><TableCell align="right">{line.quantity}</TableCell>
              <TableCell><TextField size="small" type="number" value={counted ?? ''}
                inputProps={{ min: 0 }} onChange={event => setCounts(value => ({
                  ...value, [line.itemKey]: event.target.value
                }))} /></TableCell>
              <TableCell align="right"><Chip size="small" color={variance ? 'warning' : 'default'}
                label={variance > 0 ? `+${variance}` : variance} /></TableCell>
            </TableRow>
          })}</TableBody>
        </Table>
        {countPager}
        {!rows.length && <EmptyState>No active stock lines match this count.</EmptyState>}
      </CardContent></Card>
    </Stack>
  )
}

function Management({ state, run, setView }) {
  const [retiring, setRetiring] = useState(null)
  const [retireReason, setRetireReason] = useState('')
  const reorder = (state.reorder || []).filter(item => item.needsReorder)
  const catalog = state.catalog || []
  const { pageRows: reorderRows, pager: reorderPager } = usePagedRows(reorder)
  const { pageRows: catalogRows, pager: catalogPager } = usePagedRows(catalog)
  const estimated = reorder.reduce((sum, item) => sum + (item.estimatedCost || 0), 0)
  return (
    <Stack spacing={2}>
      <PageHeader title="Management"
        description="Plan replenishment, review catalog controls, and export decision-ready reports."
        actions={<Button variant="contained" startIcon={<DownloadRounded />}
          onClick={() => run(() => api.report(), 'HTML, CSV, and PDF reports exported')}>
          Export report package
        </Button>} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 2 }}>
        <Card><CardContent><Typography color="text.secondary" variant="body2">Reorder lines</Typography>
          <Typography variant="h4">{reorder.length}</Typography></CardContent></Card>
        <Card><CardContent><Typography color="text.secondary" variant="body2">Estimated order cost</Typography>
          <Typography variant="h4">${estimated.toFixed(2)}</Typography></CardContent></Card>
        <Card><CardContent><Typography color="text.secondary" variant="body2">Catalog products</Typography>
          <Typography variant="h4">{catalog.length}</Typography></CardContent></Card>
      </Box>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ p: 2.5 }}><Typography variant="h6">Replenishment plan</Typography>
          <Typography variant="body2" color="text.secondary">PAR values take precedence; other targets use consumption history.</Typography></Box>
        <Table size="small"><TableHead><TableRow><TableCell>Product</TableCell><TableCell>GTIN</TableCell>
          <TableCell align="right">On hand</TableCell><TableCell align="right">Target</TableCell>
          <TableCell align="right">Order qty</TableCell><TableCell>Basis</TableCell>
          <TableCell align="right">Est. cost</TableCell></TableRow></TableHead>
          <TableBody>{reorderRows.map(item => <TableRow key={item.gtin}>
            <TableCell>{item.name || item.gtin}</TableCell><TableCell>{item.gtin}</TableCell>
            <TableCell align="right">{item.onHand}</TableCell>
            <TableCell align="right">{item.parProvided ? item.par : item.suggestedPar}</TableCell>
            <TableCell align="right"><Chip color="warning" size="small" label={item.suggestedOrderQty} /></TableCell>
            <TableCell>{item.parProvided ? 'Configured PAR' : 'Consumption model'}</TableCell>
            <TableCell align="right">${(item.estimatedCost || 0).toFixed(2)}</TableCell>
          </TableRow>)}</TableBody></Table>{reorderPager}
        {!reorder.length && <EmptyState>No items currently need replenishment.</EmptyState>}
      </CardContent></Card>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ p: 2.5 }}><Typography variant="h6">Catalog controls</Typography></Box>
        <Table size="small"><TableHead><TableRow><TableCell>Product</TableCell><TableCell>Manufacturer</TableCell>
          <TableCell>Category</TableCell><TableCell align="right">Unit price</TableCell>
          <TableCell align="right">PAR</TableCell><TableCell>Source</TableCell><TableCell>Actions</TableCell></TableRow></TableHead>
          <TableBody>{catalogRows.map(item => <TableRow key={item.gtin}><TableCell>{item.name}</TableCell>
            <TableCell>{item.manufacturer || '—'}</TableCell><TableCell>{item.category || '—'}</TableCell>
            <TableCell align="right">${(item.unitPrice || 0).toFixed(2)}</TableCell>
            <TableCell align="right">{item.par >= 0 ? item.par : 'Not set'}</TableCell>
            <TableCell>{item.active === false ? <Chip size="small" label="Retired" /> : item.source || '—'}</TableCell>
            <TableCell>{item.active !== false && <Button size="small" onClick={() => {
              sessionStorage.setItem('medsupply.registrationDraft', JSON.stringify(item)); setView('registration')
            }}>Edit</Button>}{item.active !== false && <Button size="small" color="error" onClick={() => {
              setRetiring(item); setRetireReason('')
            }}>Retire</Button>}</TableCell></TableRow>)}</TableBody></Table>{catalogPager}
      </CardContent></Card>
      <Dialog open={!!retiring} onClose={() => setRetiring(null)} fullWidth maxWidth="xs">
        <DialogTitle>Retire catalog product</DialogTitle><DialogContent dividers>
          <Typography variant="body2" sx={{ mb: 2 }}>{retiring?.name} ({retiring?.gtin})</Typography>
          <TextField autoFocus fullWidth label="Retirement reason" value={retireReason}
            onChange={event => setRetireReason(event.target.value)} /></DialogContent>
        <DialogActions><Button onClick={() => setRetiring(null)}>Cancel</Button><Button color="error"
          variant="contained" disabled={!retireReason.trim()} onClick={async () => {
            if (await run(() => api.retireProduct({ gtin: retiring.gtin, reason: retireReason }), 'Product retired'))
              setRetiring(null)
          }}>Retire</Button></DialogActions>
      </Dialog>
    </Stack>
  )
}

function Settings({ state, run }) {
  const initial = state.settings || {}
  const initialForm = Object.fromEntries(Object.entries(initial).map(([key, value]) => [key, String(value)]))
  const [form, setForm] = useState(() => Object.fromEntries(
    Object.entries(initial).map(([key, value]) => [key, String(value)])))
  const [distro, setDistro] = useState(() => (state.distro || []).join('\n'))
  useUnsavedGuard(JSON.stringify(form) !== JSON.stringify(initialForm)
    || distro !== (state.distro || []).join('\n'))
  const set = (key, value) => setForm(current => ({ ...current, [key]: value }))
  const save = () => run(() => api.settings(form), 'Settings saved')
  const chooseFolder = () => run(async () => {
    const result = await api.chooseFolder()
    if (result.cancelled) throw new Error('Folder selection cancelled')
  }, 'Shared folder changed')
  const numberField = (key, label, helper) => <TextField size="small" type="number" label={label}
    value={form[key] ?? ''} helperText={helper} onChange={event => set(key, event.target.value)} />
  return (
    <Stack spacing={2}>
      <PageHeader title="Settings" description="Configure this workstation and inventory planning behavior."
        actions={<Button variant="contained" onClick={save}>Save settings</Button>} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>
        <Card><CardContent><Typography variant="h6" gutterBottom>Workstation</Typography>
          <Stack spacing={2}>
            <TextField size="small" label="Device ID" value={form.deviceId ?? ''}
              onChange={event => set('deviceId', event.target.value)} />
            <TextField size="small" label="Authenticated Windows user" value={initial.actor ?? ''}
              disabled helperText="Derived from the signed-in Windows account; cannot be edited." />
            {numberField('scannerMinimumLength', 'Minimum scanner length', 'Reject shorter scanner input')}
            {numberField('scannerDefaultQuantity', 'Default received quantity', 'Initial quantity after each scan')}
            <FormControlLabel control={<Switch checked={form.scannerAutoFocus === 'true'}
              onChange={event => set('scannerAutoFocus', String(event.target.checked))} />} label="Auto-focus scanner fields" />
            <FormControlLabel control={<Switch checked={form.scannerSound === 'true'}
              onChange={event => set('scannerSound', String(event.target.checked))} />} label="Play confirmation sound" />
            <FormControlLabel control={<Switch checked={form.scannerAutoSubmit === 'true'}
              onChange={event => set('scannerAutoSubmit', String(event.target.checked))} />} label="Auto-submit reviewed scans" />
            <Divider />
            <Typography variant="body2" color="text.secondary">Shared OneDrive folder</Typography>
            <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>{state.sharedRoot || 'Not configured'}</Typography>
            <Button startIcon={<FolderRounded />} variant="outlined" onClick={chooseFolder}>Change folder…</Button>
            <Button color="error" variant="outlined" onClick={() => api.shutdown()}>Quit Medical Supply</Button>
          </Stack>
        </CardContent></Card>
        <Card><CardContent><Typography variant="h6" gutterBottom>Inventory planning</Typography>
          <Stack spacing={2}>
            {numberField('staleDays', 'Stale inventory threshold (days)', 'Flags stock with no recent activity')}
            {numberField('reorderWindowDays', 'Usage history window (days)', 'Period used to calculate average consumption')}
            {numberField('reorderLeadDays', 'Supplier lead time (days)', 'Expected delivery lead time')}
            {numberField('reorderSafetyDays', 'Safety stock (days)', 'Additional usage buffer')}
            {numberField('reorderCoverageDays', 'Order coverage (days)', 'Target stock coverage after replenishment')}
          </Stack>
        </CardContent></Card>
        <Card><CardContent><Typography variant="h6" gutterBottom>FDA AccessGUDID</Typography>
          <FormControlLabel control={<Switch checked={form.gudidEnabled === 'true'}
            onChange={event => set('gudidEnabled', String(event.target.checked))} />}
            label="Use GUDID suggestions for unknown products" />
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1, wordBreak: 'break-all' }}>
            {initial.gudidEndpoint}
          </Typography>
        </CardContent></Card>
        <Card><CardContent><Typography variant="h6" gutterBottom>Expiry distribution list</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Shared recipient data for future expiry notifications. Sending notifications is deferred beyond RC1.
          </Typography>
          <TextField fullWidth multiline minRows={4} label="One email address per line" value={distro}
            onChange={event => setDistro(event.target.value)} />
          <Button sx={{ mt: 2 }} variant="outlined" onClick={() => run(() => api.distro({ members: distro }),
            'Distribution list saved')}>Save distribution list</Button>
        </CardContent></Card>
      </Box>
    </Stack>
  )
}

function Registration({ state, run }) {
  const emptyRegistration = { gtin: '', name: '', manufacturer: '', category: '', unitPrice: '', par: '', notes: '' }
  const [form, setForm] = useState(() => {
    try {
      return { ...emptyRegistration, ...JSON.parse(sessionStorage.getItem('medsupply.registrationDraft') || '{}') }
    } catch (error) {
      return emptyRegistration
    }
  })
  const [lookupError, setLookupError] = useState('')
  useUnsavedGuard(Object.values(form).some(value => String(value || '').trim().length > 0))
  useEffect(() => {
    sessionStorage.setItem('medsupply.registrationDraft', JSON.stringify(form))
  }, [form])
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const lookup = async () => {
    setLookupError('')
    try {
      const r = await api.gudid({ gtin: form.gtin })
      if (r.lookupFailed) throw new Error(r.error || 'GUDID lookup failed')
      if (r.enabled && r.found) setForm(f => ({ ...f, name: r.name || f.name, manufacturer: r.manufacturer || f.manufacturer, category: r.category || f.category }))
      if (r.enabled && !r.found) setLookupError('No GUDID record was found for this GTIN.')
    } catch (e) { setLookupError(e.message) }
  }
  const save = () => run(async () => {
    await api.register({
      ...form,
      unitPrice: form.unitPrice || '0',
      par: form.par === '' ? '-1' : form.par,
      source: 'MANUAL'
    })
    sessionStorage.removeItem('medsupply.registrationDraft')
    setForm(emptyRegistration)
  }, 'Saved')
  const categories = [...new Set((state.catalog || []).map(c => c.category).filter(Boolean))]
  return (
    <Stack spacing={2}>
    <PageHeader title="Product registration"
      description="Create or maintain catalog records, PAR levels, pricing, and product classification." />
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Register / update product</Typography>
      {lookupError && <Alert severity="warning" sx={{ mb: 2 }}>{lookupError}</Alert>}
      <Stack spacing={1} sx={{ maxWidth: 520 }}>
        <Stack direction="row" spacing={1}>
          <TextField fullWidth size="small" label="GTIN" value={form.gtin} onChange={e => set('gtin', e.target.value)} />
          <Button onClick={lookup}>GUDID lookup</Button>
        </Stack>
        <TextField size="small" label="Name" value={form.name} onChange={e => set('name', e.target.value)} />
        <TextField size="small" label="Manufacturer" value={form.manufacturer} onChange={e => set('manufacturer', e.target.value)} />
        <TextField size="small" label="Category" value={form.category} onChange={e => set('category', e.target.value)}
          helperText={categories.length ? `Existing: ${categories.join(', ')}` : ''} />
        <Stack direction="row" spacing={1}>
          <TextField size="small" label="Unit price" value={form.unitPrice} onChange={e => set('unitPrice', e.target.value)} />
          <TextField size="small" label="PAR (blank = none)" value={form.par} onChange={e => set('par', e.target.value)} />
        </Stack>
        <TextField size="small" label="Notes" value={form.notes} onChange={e => set('notes', e.target.value)} />
        <Button variant="contained" onClick={save}>Save product</Button>
      </Stack>
    </CardContent></Card>
    </Stack>
  )
}

function Labels({ state }) {
  const [urls, setUrls] = useState({})
  const rows = (state.stock || []).filter(line => line.active).sort((a, b) =>
    (a.category || 'Uncategorized').localeCompare(b.category || 'Uncategorized')
      || (a.name || a.gtin).localeCompare(b.name || b.gtin)
      || a.lot.localeCompare(b.lot))
  const groups = rows.reduce((result, line) => {
    const category = line.category || 'Uncategorized'
    if (!result[category]) result[category] = []
    result[category].push(line)
    return result
  }, {})
  useEffect(() => {
    let cancelled = false
    const stock = (state.stock || []).filter(line => line.active)
    Promise.all(stock.map(line => QRCode.toDataURL(`MSITEM:${line.itemKey}`, { margin: 1, width: 96 })
      .then(url => [line.itemKey, url]))).then(pairs => {
        if (!cancelled) setUrls(Object.fromEntries(pairs))
      })
    return () => { cancelled = true }
  }, [state.stock])
  return (
    <Box>
      <Box className="ledger-screen-only">
        <PageHeader title="Paper count ledger"
          description="Print the complete category-sorted inventory, record counts while away from the workstation, then scan row QR codes in Inventory Count."
          actions={<Button variant="contained" onClick={() => window.print()}>Print inventory ledger</Button>} />
        <Alert severity="info" sx={{ mb: 2 }}>
          Write the physical count and variance on paper. Back at the workstation, open Inventory Count and scan each changed row.
        </Alert>
      </Box>
      <Box className="ledger-print">
        <Box sx={{ mb: 2 }}>
          <Typography variant="h5">Medical Supply Inventory Count Ledger</Typography>
          <Typography variant="body2">Printed {new Date().toLocaleString()} · {rows.length} active stock lines</Typography>
          <Typography variant="caption">Location / team: ____________________  Counter: ____________________  Date: __________</Typography>
        </Box>
        {Object.entries(groups).map(([category, lines]) => <Box key={category}
          sx={{ mb: 2, breakInside: 'avoid-page' }}>
          <Typography variant="subtitle1" fontWeight={800} sx={{ px: 1, py: .5,
            bgcolor: '#e8eaf6', border: '1px solid #9fa8da' }}>{category}</Typography>
          <Table size="small" sx={{ '& td, & th': { border: '1px solid #9ca3af', py: .4, px: .7 } }}>
            <TableHead><TableRow><TableCell sx={{ width: 64 }}>Lookup</TableCell>
              <TableCell>Product / GTIN</TableCell><TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
              <TableCell align="right">System</TableCell><TableCell sx={{ width: 90 }}>Counted</TableCell>
              <TableCell sx={{ width: 80 }}>Delta</TableCell><TableCell sx={{ width: 70 }}>Initials</TableCell>
            </TableRow></TableHead>
            <TableBody>{lines.map(line => <TableRow key={line.itemKey}>
              <TableCell>{urls[line.itemKey] && <img src={urls[line.itemKey]} width="52" height="52" alt="QR lookup" />}</TableCell>
              <TableCell><Typography variant="body2" fontWeight={700}>{line.name || 'Unregistered product'}</Typography>
                <Typography variant="caption">{line.gtin}</Typography></TableCell>
              <TableCell>{line.lot || '—'}</TableCell><TableCell>{line.expirationIso || '—'}</TableCell>
              <TableCell align="right">{line.quantity}</TableCell><TableCell></TableCell>
              <TableCell></TableCell><TableCell></TableCell>
            </TableRow>)}</TableBody>
          </Table>
        </Box>)}
      </Box>
      {!rows.length && <Card><EmptyState>No active inventory is available for the ledger.</EmptyState></Card>}
    </Box>
  )
}

function Diagnostics({ state, run }) {
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Diagnostics</Typography>
      <Typography variant="body2">Shared root: {state.sharedRoot || '(not set)'}</Typography>
      <Typography variant="body2">Events: {state.eventCount} · Pending: {state.pendingCount}</Typography>
      <Typography variant="body2">GUDID enabled: {String(state.gudidEnabled)}</Typography>
      <Typography variant="body2" color="error" sx={{ whiteSpace: 'pre-wrap' }}>
        {(state.errors || []).join('\n')}
      </Typography>
    </CardContent></Card>
  )
}

createRoot(document.getElementById('root')).render(<AppErrorBoundary><App /></AppErrorBoundary>)
