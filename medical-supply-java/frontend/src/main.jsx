import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  AppBar, Box, Button, Card, CardContent, Chip, CssBaseline, Divider, Drawer, IconButton,
  List, ListItemButton, ListItemIcon, ListItemText, Snackbar, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, ThemeProvider, Toolbar, Typography
} from '@mui/material'
import DashboardRounded from '@mui/icons-material/DashboardRounded'
import QrCodeScannerRounded from '@mui/icons-material/QrCodeScannerRounded'
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded'
import AppRegistrationRounded from '@mui/icons-material/AppRegistrationRounded'
import QrCode2Rounded from '@mui/icons-material/QrCode2Rounded'
import TroubleshootRounded from '@mui/icons-material/TroubleshootRounded'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import QRCode from 'qrcode'
import { api } from './api'
import { theme } from './theme'

const DRAWER = 240
const NAV = [
  ['dashboard', 'Dashboard', <DashboardRounded />],
  ['scan', 'Scan', <QrCodeScannerRounded />],
  ['inventory', 'Inventory', <Inventory2Rounded />],
  ['registration', 'Registration', <AppRegistrationRounded />],
  ['labels', 'Labels', <QrCode2Rounded />],
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
  const [toast, setToast] = useState('')

  const refresh = useCallback(async () => {
    try { setState(await api.state()) } catch (e) { setToast(e.message) }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 15000)
    return () => clearInterval(id)
  }, [refresh])

  const run = useCallback(async (fn, ok) => {
    try { await fn(); if (ok) setToast(ok); await refresh() }
    catch (e) { setToast(e.message) }
  }, [refresh])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AppBar position="fixed" sx={{ zIndex: t => t.zIndex.drawer + 1 }}>
        <Toolbar><Typography variant="h6">Medical Supply Tracking</Typography></Toolbar>
      </AppBar>
      <Drawer variant="permanent" sx={{ width: DRAWER, '& .MuiDrawer-paper': { width: DRAWER } }}>
        <Toolbar />
        <List>
          {NAV.map(([key, label, icon]) => (
            <ListItemButton key={key} selected={view === key} onClick={() => setView(key)}>
              <ListItemIcon>{icon}</ListItemIcon>
              <ListItemText primary={label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>
      <Box component="main" sx={{ ml: `${DRAWER}px`, p: 3, mt: 8 }}>
        {!state ? <Typography>Loading…</Typography> : !state.configured && view !== 'diagnostics'
          ? <FolderPrompt run={run} state={state} />
          : {
              dashboard: <Dashboard state={state} run={run} />,
              scan: <Scan run={run} refresh={refresh} setToast={setToast} />,
              inventory: <Inventory state={state} run={run} />,
              registration: <Registration state={state} run={run} />,
              labels: <Labels state={state} />,
              diagnostics: <Diagnostics state={state} run={run} />
            }[view]}
      </Box>
      <Snackbar open={!!toast} autoHideDuration={4000} onClose={() => setToast('')} message={toast} />
    </ThemeProvider>
  )
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

function Dashboard({ state, run }) {
  const d = state.dashboard || {}
  const tiles = [
    ['SKUs', d.distinctSkus], ['On-hand value', (d.onHandValue || 0).toFixed(2)],
    ['Expired', d.expired], ['Expiring 7d', d.expiring7], ['Expiring 30d', d.expiring30],
    ['Out of stock', d.outOfStock], ['Stale', d.stale]
  ]
  const reorder = (state.reorder || []).filter(r => r.needsReorder)
  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
        {tiles.map(([label, value]) => (
          <Card key={label} sx={{ minWidth: 140 }}><CardContent>
            <Typography variant="h4">{value ?? 0}</Typography>
            <Typography variant="body2" color="text.secondary">{label}</Typography>
          </CardContent></Card>
        ))}
      </Box>
      <Button variant="outlined" sx={{ alignSelf: 'flex-start' }}
        onClick={() => run(() => api.report(), 'Report exported')}>Export management report</Button>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Reorder needed</Typography>
        <Table size="small">
          <TableHead><TableRow><TableCell>Product</TableCell><TableCell>On hand</TableCell>
            <TableCell>Target</TableCell><TableCell>Order</TableCell><TableCell>Est. cost</TableCell></TableRow></TableHead>
          <TableBody>
            {reorder.map(r => (
              <TableRow key={r.gtin}><TableCell>{r.name || r.gtin}</TableCell><TableCell>{r.onHand}</TableCell>
                <TableCell>{r.parProvided ? r.par : r.suggestedPar}</TableCell><TableCell>{r.suggestedOrderQty}</TableCell>
                <TableCell>{(r.estimatedCost || 0).toFixed(2)}</TableCell></TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent></Card>
    </Stack>
  )
}

function Scan({ run, refresh, setToast }) {
  const [raw, setRaw] = useState('')
  const [qty, setQty] = useState('1')
  const submit = async () => {
    try {
      const result = await api.receive({ raw, quantity: qty, force: 'false' })
      if (result.needsRegistration) {
        const s = result.suggestion || {}
        const name = window.prompt(`Unknown product ${result.gtin}. Product name:`, s.name || '')
        if (!name) return
        await api.register({ gtin: result.gtin, name, manufacturer: s.manufacturer || '',
          category: s.category || '', source: s.found ? 'GUDID' : 'MANUAL' })
        await api.receive({ raw, quantity: qty, force: 'true' })
      }
      setRaw(''); setToast('Received'); refresh()
    } catch (e) { setToast(e.message) }
  }
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Scan to receive</Typography>
      <Stack direction="row" spacing={1}>
        <TextField autoFocus fullWidth size="small" label="Barcode" value={raw}
          onChange={e => setRaw(e.target.value)} onKeyDown={e => e.key === 'Enter' && submit()} />
        <TextField size="small" label="Qty" type="number" value={qty} sx={{ width: 100 }}
          onChange={e => setQty(e.target.value)} />
        <Button variant="contained" onClick={submit}>Receive</Button>
      </Stack>
    </CardContent></Card>
  )
}

function Inventory({ state, run }) {
  const [filter, setFilter] = useState('')
  const rows = (state.stock || []).filter(l => l.active &&
    (l.name + l.gtin + l.lot).toLowerCase().includes(filter.toLowerCase()))
  return (
    <Card><CardContent>
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <TextField size="small" label="Search" value={filter} onChange={e => setFilter(e.target.value)} />
      </Stack>
      <Table size="small">
        <TableHead><TableRow><TableCell>Name</TableCell><TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
          <TableCell>Qty</TableCell><TableCell>Actions</TableCell></TableRow></TableHead>
        <TableBody>
          {rows.map(l => (
            <TableRow key={l.itemKey}>
              <TableCell>{l.name || l.gtin}</TableCell><TableCell>{l.lot}</TableCell>
              <TableCell><Chip size="small" color={expiryColor(l.expirationIso)} label={l.expirationIso || '—'} /></TableCell>
              <TableCell>{l.quantity}</TableCell>
              <TableCell>
                <Button size="small" onClick={() => { const n = window.prompt('Pick quantity', '1'); if (n) run(() => api.pick({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, quantity: n }), 'Picked') }}>Pick</Button>
                <Button size="small" onClick={() => { const n = window.prompt('Set quantity', String(l.quantity)); if (n !== null) run(() => api.adjust({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, quantity: n }), 'Adjusted') }}>Adjust</Button>
                <Button size="small" color="error" onClick={() => { const r = window.prompt('Archive reason'); if (r) run(() => api.archive({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, reason: r }), 'Archived') }}>Archive</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </CardContent></Card>
  )
}

function Registration({ state, run }) {
  const [form, setForm] = useState({ gtin: '', name: '', manufacturer: '', category: '', unitPrice: '', par: '', notes: '' })
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const lookup = async () => {
    try {
      const r = await api.gudid({ gtin: form.gtin })
      if (r.enabled && r.found) setForm(f => ({ ...f, name: r.name || f.name, manufacturer: r.manufacturer || f.manufacturer, category: r.category || f.category }))
    } catch (e) { /* offline is fine */ }
  }
  const categories = [...new Set((state.catalog || []).map(c => c.category).filter(Boolean))]
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Register / update product</Typography>
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
        <Button variant="contained" onClick={() => run(() => api.register({
          ...form, unitPrice: form.unitPrice || '0', par: form.par === '' ? '-1' : form.par, source: 'MANUAL'
        }), 'Saved')}>Save product</Button>
      </Stack>
    </CardContent></Card>
  )
}

function Labels({ state }) {
  const [urls, setUrls] = useState({})
  const rows = (state.stock || []).filter(l => l.active)
  useEffect(() => {
    let cancelled = false
    Promise.all(rows.map(l => QRCode.toDataURL(l.barcode || l.gtin, { margin: 1, width: 96 })
      .then(url => [l.itemKey, url]))).then(pairs => { if (!cancelled) setUrls(Object.fromEntries(pairs)) })
    return () => { cancelled = true }
  }, [state])
  return (
    <Box>
      <Button variant="outlined" sx={{ mb: 2 }} onClick={() => window.print()}>Print labels</Button>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 1 }}>
        {rows.map(l => (
          <Card key={l.itemKey} variant="outlined"><CardContent sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            {urls[l.itemKey] && <img src={urls[l.itemKey]} width="72" height="72" alt="QR" />}
            <Box>
              <Typography variant="body2" fontWeight={600}>{l.name || l.gtin}</Typography>
              <Typography variant="caption" display="block">Lot: {l.lot}</Typography>
              <Typography variant="caption" display="block">Exp: {l.expirationIso || '—'} · Qty: {l.quantity}</Typography>
            </Box>
          </CardContent></Card>
        ))}
      </Box>
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

createRoot(document.getElementById('root')).render(<App />)
